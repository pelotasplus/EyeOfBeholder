package pl.pelotasplus.eyeofbeholder.data.model

/**
 * Disintegrate: the square ahead unmade, wall and creature alike.
 *
 * Two things happen and neither waits on the other. Whatever stands on the
 * square ahead is unmade outright — not hurt, unmade, however much of it
 * there was — and the square's walls go whether anything was standing there,
 * whether it survived, and whether there was anything at all. So the spell is
 * as much a way through a wall as a way past a creature, and cast down an
 * empty corridor it is not a wasted scroll.
 */
class Disintegration(
    private val kinds: List<MonsterProperty> = emptyList(),
    private val itemTypes: ItemTypes? = null,
    private val dice: Dice = Dice.random,
) {
    data class Unmade(
        val world: GameState,
        /**
         * What went, or nothing — which covers three different endings: an
         * empty square, one out of reach, and a creature that held against
         * it. The walls went in all three.
         */
        val gone: MonsterSlot? = null,
    )

    /**
     * @param reachable whether the square ahead can be stepped onto, which is
     *   what decides whether a creature on it can be reached at all. A closed
     *   door is not opened by this and what is behind one is not touched —
     *   but the door itself is a wall of that square, and goes.
     */
    fun castAheadOf(
        world: GameState,
        level: Int,
        reachable: Boolean,
    ): Unmade {
        val ahead = world.party.facing.oneStepFrom(world.party.position)

        val gone = if (!reachable) null else whatItUnmakes(world, level, ahead)

        val after = (gone?.let { world.monsterHurt(it, UNMADE, kinds, itemTypes, dice) } ?: world)
            .wallsChanged(level, ahead, WallByte(0))

        return Unmade(after, gone)
    }

    /**
     * Whichever creature on [ahead] the spell takes, or none.
     *
     * The three questions are asked in the game's own order, which decides
     * how many dice are thrown and so what everything after it rolls: quick
     * enough to be out of the way, then beyond this magic's reach at all,
     * and only then its throw against it.
     */
    private fun whatItUnmakes(world: GameState, level: Int, ahead: Location): MonsterSlot? {
        val standing = world.monsters.firstOrNull {
            it.level == level && it.x == ahead.x && it.y == ahead.y
        } ?: return null

        val kind = kinds.firstOrNull { it.id == standing.type.value } ?: return null

        if (dice.roll(1, 100, 0) < kind.dmgModifierEvade) return null
        if (kind.immunities.untouchedByThisMagic) return null
        if (kind.saves(SavingThrow.A_SPELL, dice)) return null

        return standing.index
    }

    private companion object {
        /**
         * Past anything in the dungeon has, because the spell does not wound:
         * whatever it reaches is gone whatever was left of it, and enough
         * damage is how that is said to a world that only knows damage.
         */
        val UNMADE = Damage(1000)
    }
}
