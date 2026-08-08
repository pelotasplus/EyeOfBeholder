package pl.pelotasplus.eyeofbeholder.data.model

/**
 * A monster striking back.
 *
 * The mirror of [Fighting] and a simpler thing: a monster has no weapon and no
 * strength, only what its kind is worth. It needs `1d20` at or above its own
 * to-hit number less the champion's armour, and a twenty always lands however
 * well armoured they are.
 */
class MonstersTurn(
    private val kinds: List<MonsterProperty>,
    private val dice: Dice = Dice.random,
) {

    /** What a monster did on its turn, and the world it leaves behind. */
    data class Taken(val struck: List<Struck>, val world: GameState)

    /** One monster's blow at one champion. */
    data class Struck(val monster: Int, val at: PartySlot, val damage: Int, val heard: TrackIndex?)

    /**
     * Every monster that can reach the party takes its turn.
     *
     * Only one that has been roused: the pair on level 5 stand talking until
     * somebody hits one of them, and a monster that has not been provoked is
     * scenery.
     */
    fun taken(world: GameState): Taken {
        var after = world
        val struck = mutableListOf<Struck>()

        world.monsters.filter { it.provoked }.forEach { monster ->
            val blow = strike(after, monster) ?: return@forEach
            struck += blow
            after = after.championHurt(blow.at, blow.damage)
        }

        return Taken(struck, after.monstersStriking(struck.map { it.monster }))
    }

    private fun strike(world: GameState, monster: MonsterInstance): Struck? {
        if (!monster.canReach(world.party)) return null

        val kind = kinds.firstOrNull { it.id == monster.type.value } ?: return null
        val whom = whoItReaches(world, monster) ?: return null
        val champion = world.championIn(whom) ?: return null

        var damage = 0
        repeat(kind.attacksPerRound) { attack ->
            val roll = dice.roll(1, 20, 0)
            val lands = roll == NATURALLY_ALWAYS ||
                roll >= kind.hitChance - champion.armorClass.value

            if (lands) {
                kind.dmgDc.getOrNull(attack)?.let { damage += dice.roll(it.times, it.pips, it.base) }
            }
        }

        // A monster is heard swinging whether or not it connects, which is the
        // one sound a monster has ever been given.
        return Struck(monster.index, whom, damage, TrackIndex(kind.sound1).takeIf { kind.sound1 > 0 })
    }

    /**
     * Which champion the blow falls on: the first of the six still standing,
     * in the order this monster reaches them from where it is.
     */
    private fun whoItReaches(world: GameState, monster: MonsterInstance): PartySlot? =
        WhoTheMonsterReaches
            .inOrder(world.party.facing, monster.direction, monster.place)
            .firstOrNull { world.championIn(it)?.dead == false }

    private companion object {
        /** A twenty lands on anything, however well armoured. */
        const val NATURALLY_ALWAYS = 20
    }
}

/**
 * Which champion a monster's arm comes down on, in the order it reaches them.
 *
 * Three tables of the original's, and the shape of them is: the party's facing
 * against the monster's picks one of four groups of twelve; where the monster
 * stands on its own square picks which half of that twelve; and the six that
 * are left are champion slots, nearest first.
 */
object WhoTheMonsterReaches {

    fun inOrder(
        partyFacing: Direction,
        monsterFacing: Direction,
        monsterPlace: SquarePlace,
    ): List<PartySlot> {
        val group = GROUPS[partyFacing.ordinal * SIDES + monsterFacing.ordinal] * (SLOTS * 2)
        val half = if (nearerSide(monsterFacing, monsterPlace)) 0 else SLOTS

        return REACHED_IN_ORDER.subList(group + half, group + half + SLOTS).map(::PartySlot)
    }

    /**
     * Whether a monster stands on the half of its square nearer the party, and
     * so reaches the near pair of them first. Something in the middle of a
     * square is always near.
     */
    private fun nearerSide(facing: Direction, place: SquarePlace): Boolean {
        if (!place.onTheFloor) return true
        return NEARER[facing.ordinal * SIDES + place.ordinal] == 0
    }

    /**
     * Whether a monster on that corner can reach the party's square at all.
     * A separate table of the original's from [NEARER], and the one that keeps
     * something tucked into a far corner out of the fight.
     */
    fun armIsLongEnough(facing: Direction, place: SquarePlace): Boolean =
        !place.onTheFloor || CAN_REACH[facing.ordinal * SIDES + place.ordinal] == 1

    /** Which corners reach, by the monster's facing. From the original. */
    private val CAN_REACH = listOf(
        1, 1, 0, 0,
        0, 1, 0, 1,
        0, 0, 1, 1,
        1, 0, 1, 0,
    )

    /** Which group of twelve, by the party's facing against the monster's. */
    private val GROUPS = listOf(
        2, 3, 0, 1,
        1, 2, 3, 0,
        0, 1, 2, 3,
        3, 0, 1, 2,
    )

    /** Which half of it, by the monster's facing against where it stands. */
    private val NEARER = listOf(
        0, 1, 0, 1,
        0, 0, 1, 1,
        1, 0, 1, 0,
        1, 1, 0, 0,
    )

    /** And the six champions of each half, in the order they are reached. */
    private val REACHED_IN_ORDER = listOf(
        1, 0, 3, 2, 5, 4, /**/ 0, 1, 2, 3, 4, 5,
        5, 3, 1, 4, 2, 0, /**/ 1, 3, 5, 0, 2, 4,
        4, 5, 2, 3, 0, 1, /**/ 5, 4, 3, 2, 1, 0,
        0, 2, 4, 1, 3, 5, /**/ 4, 2, 0, 5, 3, 1,
    )

    private const val SIDES = 4
    private const val SLOTS = 6
}
