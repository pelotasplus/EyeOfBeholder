package pl.pelotasplus.eyeofbeholder.data.model

/** How a spell that turns a creature to stone finds it. */
enum class TurningToStone {
    /** See [FleshToStone.castBy]. */
    ON_THE_SQUARE_AHEAD,

    /** See [FleshToStone.spokenBy]. */
    BY_A_WORD,
}

/**
 * Flesh to stone: one creature on the square ahead turned to stone for good.
 *
 * It takes the one nearest whoever cast it, the same one a blow of theirs
 * would land on. Turned, it does nothing ever again but stand there; it is
 * still in the way, and a blow by hand breaks it — see [Blow.Shattered].
 */
class FleshToStone(
    private val kinds: List<MonsterProperty> = emptyList(),
    private val dice: Dice = Dice.random,
) {
    data class Cast(val world: GameState, val came: CameTo)

    enum class CameTo {
        /** Nothing on the square ahead, which the caster is told. */
        NOTHING_IN_REACH,

        /** Something there, and it escaped. Nothing is said. */
        RESISTED,

        TURNED,
    }

    fun castBy(world: GameState, whose: PartySlot): Cast {
        val target = world.theMonsterNearestAhead(whose)
            ?: return Cast(world, CameTo.NOTHING_IN_REACH)

        return turning(target, world)
    }

    /**
     * Power word stun, which for all its name leaves the same statue: the
     * nearest creature on the first two squares the party can see onto,
     * [reachable] saying which they can.
     *
     * Anything with [TOO_MUCH_LEFT] hit points or more is passed over before
     * any die is thrown, so it gets no chance to dodge and none is needed.
     */
    fun spokenBy(world: GameState, whose: PartySlot, reachable: (Location) -> Boolean): Cast {
        val target = world.theMonsterNearestWithin(WORD_REACH, whose, reachable)
            ?: return Cast(world, CameTo.NOTHING_IN_REACH)

        if (target.hitPoints.current >= TOO_MUCH_LEFT) return Cast(world, CameTo.RESISTED)
        return turning(target, world)
    }

    private fun turning(target: MonsterInstance, world: GameState): Cast {
        val kind = kinds.firstOrNull { it.id == target.type.value }
        if (kind == null || kind.escapesASpellAimedAtIt(dice)) {
            return Cast(world, CameTo.RESISTED)
        }

        return Cast(
            world.copy(
                monsters = world.monsters.map {
                    if (it.index == target.index) it.turnedToStone() else it
                },
            ),
            CameTo.TURNED,
        )
    }

    private companion object {
        const val WORD_REACH = 2
        const val TOO_MUCH_LEFT = 90
    }
}
