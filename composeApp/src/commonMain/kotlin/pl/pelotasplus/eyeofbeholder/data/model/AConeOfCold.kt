package pl.pelotasplus.eyeofbeholder.data.model

/**
 * The squares a cone covers, spreading away from whoever casts it.
 *
 * Seven of them, one square wide where it leaves the caster and three wide
 * for the two rows after — which is why it is worth turning to face a corridor
 * before casting rather than standing in a doorway.
 *
 * Measured forward and sideways from the caster, and turned to whichever way
 * they face. The game keeps this as four transcribed tables, one per facing,
 * of offsets into the maze; they are the same seven squares each time, so one
 * shape and a rotation says it once instead of four times. That the four
 * agree is worth a test rather than a promise.
 */
object AConeInFront {

    /**
     * One square of the cone, counted from the caster rather than from the
     * map: [forward] squares the way they face, then [sideways] to their
     * right. Nothing here is a maze coordinate until it is turned.
     */
    data class FromTheCaster(val forward: Int, val sideways: Int)

    /**
     * The cone, a row at a time. Read down the page it is the shape itself:
     * one square where it leaves the caster, three across the row beyond it,
     * three across the row after that. Nothing on the caster's own square.
     */
    val SQUARES: List<FromTheCaster> = listOf(
        FromTheCaster(forward = 1, sideways = 0),

        FromTheCaster(forward = 2, sideways = -1),
        FromTheCaster(forward = 2, sideways = 0),
        FromTheCaster(forward = 2, sideways = 1),

        FromTheCaster(forward = 3, sideways = -1),
        FromTheCaster(forward = 3, sideways = 0),
        FromTheCaster(forward = 3, sideways = 1),
    )

    /** Those seven squares as the maze numbers them, for a caster at [from]. */
    fun spreadingFrom(from: Location, facing: Direction): List<Location> =
        SQUARES.map { square ->
            val (dx, dy) = facing.transformCoordinates(square.sideways, -square.forward)
            Location(from.x + dx, from.y + dy)
        }
}

/**
 * The cone of cold: everything standing in front of the caster frozen at once.
 *
 * Nothing is thrown and nothing flies. The cold is over all seven squares the
 * moment it is cast, so a creature cannot be missed by standing behind
 * another one and a wall between two of the squares is no shelter — the cone
 * is cast over the map rather than travelling across it.
 *
 * There is **no saving throw**. Cold is the one attack spell in the game with
 * nothing to be thrown off, which is most of what the fifth level buys.
 */
class AConeOfCold(
    private val kinds: List<MonsterProperty> = emptyList(),
    private val itemTypes: ItemTypes? = null,
    private val dice: Dice = Dice.random,
) {
    /** The world afterwards, and everything the cold reached. */
    data class Struck(
        val world: GameState,
        val frozen: List<MonsterSlot> = emptyList(),
    )

    fun castBy(world: GameState, casterLevel: Int): Struck {
        val over = AConeInFront
            .spreadingFrom(world.party.position, world.party.facing)
            .toSet()

        val caught = world.monsters.filter { Location(it.x, it.y) in over }

        return caught.fold(Struck(world)) { struck, monster ->
            val kind = kinds.getOrNull(monster.type.value)
            val dealt = dealtTo(kind, casterLevel)

            struck.copy(
                world = struck.world.monsterHurt(monster.index, dealt, kinds, itemTypes, dice),
                frozen = struck.frozen + monster.index,
            )
        }
    }

    /**
     * A die of four and one over for every level of the caster, so a scroll
     * read by anybody at all freezes for nine of each.
     */
    private fun dealtTo(kind: MonsterProperty?, casterLevel: Int): Damage {
        if (kind?.immunities?.shrugsOff(HURTS_BY) == true) return Damage(0)

        val rolled = dice.roll(casterLevel, DIE, casterLevel)
        val dealt = Damage(rolled.coerceAtLeast(0))

        return kind?.immunities?.softened(dealt, DealtBy.Magic) ?: dealt
    }

    companion object {
        /** Cold, and magic as every spell is. Anything immune to either takes none. */
        val HURTS_BY = setOf(HarmKind.MAGIC, HarmKind.COLD)

        private const val DIE = 4
    }
}
