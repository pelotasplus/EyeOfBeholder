package pl.pelotasplus.eyeofbeholder.data.model

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
}
