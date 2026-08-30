package pl.pelotasplus.eyeofbeholder.data.model

import pl.pelotasplus.eyeofbeholder.data.model.script.Damage as DamageDealt

/**
 * What a blow a script deals the party comes to.
 *
 * The commonest of them by a long way is the drop down a pit: 2d6 to
 * everybody, no saving throw, and nothing at all to anyone wearing a ring of
 * feather fall. The traps in a corridor are the same shape with other dice,
 * and the columns of lightning are the shape that asks for a save.
 */
sealed interface Harm {

    /** How much comes off each champion the blow reached, rolled separately. */
    data class Taken(val each: Map<PartySlot, Damage>) : Harm

    /**
     * A blow that allows a saving throw, which is not rolled yet — the tables
     * behind one are a piece of their own. Nothing is taken off until it is.
     */
    data object AllowsASave : Harm

    companion object {
        /**
         * What [blow] does to the party as they stand.
         *
         * Everybody's dice are rolled separately, so six champions down the
         * same pit lose six different amounts. Nobody out of the party, past
         * raising, or turned to stone is touched.
         */
        fun of(
            blow: DamageDealt,
            world: GameState,
            types: ItemTypes?,
            dice: Dice,
        ): Harm {
            if (blow.savingThrowType != NO_SAVE) return AllowsASave

            val reached = if (blow.charIndex == EVERYBODY) {
                List(Champion.PARTY_SLOTS) { PartySlot(it) }
            } else {
                listOf(PartySlot(blow.charIndex))
            }

            return Taken(
                reached.mapNotNull { whose ->
                    val who = world.championIn(whose)?.takeIf { it.canBeHurt }
                        ?: return@mapNotNull null

                    if (blow.isAFall && world.isWearing(whose, Ring.FEATHER_FALL, types)) {
                        return@mapNotNull null
                    }

                    whose to Damage(
                        dice.roll(blow.times, blow.itemOrPips, blow.useStrModifierOrBase)
                    )
                }.toMap()
            )
        }

        /** The world with [harm] taken off everybody it reached. */
        fun GameState.hurtBy(harm: Harm): GameState = when (harm) {
            is AllowsASave -> this
            is Taken -> harm.each.entries.fold(this) { world, (whose, damage) ->
                world.championHurt(whose, damage)
            }
        }

        /** The number a blow names when it means the whole party. */
        private const val EVERYBODY = -1

        /**
         * The saving throw that is not one. A blow naming this kind is taken
         * whole, and it is what every pit and nearly every trap names.
         */
        private const val NO_SAVE = 5
    }
}

/**
 * Whether a ring of feather fall stops this blow, which is how a pit is told
 * from a trap that happens to do the same dice.
 */
private val DamageDealt.isAFall: Boolean get() = flags and STOPPED_BY_FEATHER_FALL != 0

private const val STOPPED_BY_FEATHER_FALL = 4
