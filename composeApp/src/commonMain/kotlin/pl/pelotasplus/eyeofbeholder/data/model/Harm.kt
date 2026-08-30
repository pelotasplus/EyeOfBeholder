package pl.pelotasplus.eyeofbeholder.data.model

import pl.pelotasplus.eyeofbeholder.data.model.script.Damage as DamageDealt

/**
 * What a blow a script deals the party comes to.
 *
 * The commonest of them by a long way is the drop down a pit: 2d6 to
 * everybody, no saving throw, and nothing at all to anyone wearing a ring of
 * feather fall. The traps in a corridor are the same shape with other dice,
 * and the columns of lightning are the shape that allows a throw.
 */
object Harm {

    /**
     * How much [blow] takes off each champion it reaches.
     *
     * Everybody is dealt with separately — their own dice, their own throw —
     * so six champions down one pit lose six different amounts. Nobody out of
     * the party, past raising, or turned to stone is touched.
     *
     * @return one entry per champion reached, which may be none.
     */
    fun of(
        blow: DamageDealt,
        world: GameState,
        types: ItemTypes?,
        dice: Dice,
    ): Map<PartySlot, Damage> {
        val reached = if (blow.charIndex == EVERYBODY) {
            List(Champion.PARTY_SLOTS) { PartySlot(it) }
        } else {
            listOf(PartySlot(blow.charIndex))
        }

        return reached.mapNotNull { whose ->
            val who = world.championIn(whose)?.takeIf { it.canBeHurt } ?: return@mapNotNull null

            // A ring of feather fall is the whole of the answer to a pit, and
            // is asked before the dice rather than after them.
            if (blow.isAFall && world.isWearing(whose, Ring.FEATHER_FALL, types)) {
                return@mapNotNull null
            }

            val rolled = dice.roll(blow.times, blow.itemOrPips, blow.useStrModifierOrBase)
            val throwAllowed = SavingThrow.of(blow.savingThrowType)

            whose to Damage(
                if (throwAllowed != null && who.saves(throwAllowed, dice)) {
                    blow.softenedTo(rolled)
                } else {
                    rolled
                }
            )
        }.toMap()
    }

    /** The world with [blows] taken off everybody they reached. */
    fun GameState.hurtBy(blows: Map<PartySlot, Damage>): GameState =
        blows.entries.fold(this) { world, (whose, amount) -> world.championHurt(whose, amount) }

    /** The number a blow names when it means the whole party. */
    private const val EVERYBODY = -1
}

/**
 * Whether a ring of feather fall stops this blow, which is how a pit is told
 * from a trap that happens to roll the same dice.
 */
private val DamageDealt.isAFall: Boolean get() = flags and STOPPED_BY_FEATHER_FALL != 0

/**
 * What this blow is worth to somebody who made their throw. Which of the
 * three it is is the blow's own business: most are halved, some are avoided
 * outright, and a few are not softened at all — a throw made against those
 * buys nothing.
 */
private fun DamageDealt.softenedTo(rolled: Int): Int = when (savingThrowEffect) {
    AVOIDED -> 0
    HALVED, ALSO_HALVED -> rolled / 2
    else -> rolled
}

private const val STOPPED_BY_FEATHER_FALL = 4

private const val HALVED = 0
private const val ALSO_HALVED = 1
private const val AVOIDED = 3
