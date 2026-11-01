package pl.pelotasplus.eyeofbeholder.data.model

/**
 * Turn undead: every undead creature on the square ahead asked once, on one
 * throw shared between them.
 *
 * What a creature's resistance and the caster's level come to is either a
 * number the throw has to reach, in which case it turns its back as a
 * frightened thing does but for twice as long, or destruction with no throw at
 * all. A creature is only ever asked once, whether or not it turned: praying
 * at it again does nothing.
 */
class TurningUndead(
    private val kinds: List<MonsterProperty>,
    private val level: Int,
    private val dice: Dice = Dice.random,
) {
    data class Cast(val world: GameState, val reachedAny: Boolean)

    /** The throw is made for anything standing ahead, undead or not, so it moves the dice either way. */
    fun castAt(world: GameState, casterLevel: Int): Cast {
        val ahead = world.party.facing.oneStepFrom(world.party.position)
        val standingAhead = world.monsters.filter { it.location == ahead }
        if (standingAhead.isEmpty()) return Cast(world, reachedAny = false)

        val thrown = dice.roll(1, 20, 0)
        val askable = standingAhead.filter { !it.askedToTurn && !it.stone }.mapNotNull { monster ->
            kinds.firstOrNull { it.id == monster.type.value }?.takeIf { it.isUndead }?.let { monster to it }
        }
        val slots = askable.map { (monster, _) -> monster.index }.toSet()
        val asked = world.copy(
            monsters = world.monsters.map { if (it.index in slots) it.copy(askedToTurn = true) else it },
        )

        return askable.fold(Cast(asked, reachedAny = false)) { cast, (monster, kind) ->
            val needs = needed(kind.tuResist, casterLevel)
            when {
                needs == DESTROYED -> Cast(destroyed(cast.world, monster, kind), reachedAny = true)
                thrown < needs -> cast
                else -> Cast(turned(cast.world, monster), reachedAny = true)
            }
        }
    }

    /** One the blow leaves standing does not show it: a prayer lands no blow to flash at. */
    private fun destroyed(world: GameState, monster: MonsterInstance, kind: MonsterProperty): GameState {
        val after = Wounds(kinds, dice, level).hurt(world, monster, kind.immunities.softened(UNMADE, DealtBy.Magic))
        return after.copy(
            monsters = after.monsters.map { if (it.index == monster.index) it.copy(struck = false) else it },
        )
    }

    private fun turned(world: GameState, monster: MonsterInstance) = world.copy(
        monsters = world.monsters.map {
            if (it.index == monster.index) it.turnedBack(world.party.position) else it
        },
    )

    private fun needed(resistance: Int, casterLevel: Int): Int {
        val at = resistance * LEVELS_IN_A_ROW + casterLevel.coerceAtMost(LEVELS_IN_A_ROW)
        return NEEDED.getOrElse(at) { OUT_OF_REACH }
    }

    private companion object {
        val UNMADE = Damage(500)
        const val LEVELS_IN_A_ROW = 14
        const val DESTROYED = 0xFF
        const val OUT_OF_REACH = 99

        /**
         * A row for each resistance and a column for each caster level. Data:
         * reasoning will not check it and nothing notices a digit changed by
         * eye. 99 is out of reach of the throw. A level of 14 reads the first
         * column of the row below, and is kept that way as data.
         */
        val NEEDED = intArrayOf(
            10, 7, 4, 0, 0, 255, 255, 255, 255, 255, 255, 255, 255, 255,
            13, 10, 7, 4, 0, 0, 255, 255, 255, 255, 255, 255, 255, 255,
            16, 13, 10, 7, 4, 0, 0, 255, 255, 255, 255, 255, 255, 255,
            19, 16, 13, 10, 7, 4, 0, 0, 255, 255, 255, 255, 255, 255,
            20, 19, 16, 13, 10, 7, 4, 0, 0, 255, 255, 255, 255, 255,
            99, 20, 19, 16, 13, 10, 7, 4, 0, 0, 0, 255, 255, 0,
            99, 99, 20, 19, 16, 13, 10, 7, 4, 0, 0, 0, 0, 255,
            99, 99, 99, 20, 19, 16, 13, 10, 7, 4, 4, 0, 0, 0,
            99, 99, 99, 99, 20, 19, 16, 13, 10, 7, 7, 4, 4, 0,
            99, 99, 99, 99, 99, 20, 19, 16, 13, 10, 10, 7, 7, 4,
        )
    }
}
