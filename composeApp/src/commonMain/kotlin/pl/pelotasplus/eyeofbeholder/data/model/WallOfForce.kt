package pl.pelotasplus.eyeofbeholder.data.model

/**
 * The curtain of light that fills a doorway a wall of force stands in.
 *
 * Like a teleporter, this is not something a level draws. Its wall byte maps
 * to nothing at all — a level names no appearance for it — so the square shows
 * as an empty doorway that cannot be walked through, which is the worst of
 * both. The curtain belongs to the engine, which knows the byte itself, and
 * that is why [WALL_OF_FORCE] is a number here rather than something read out
 * of a file.
 *
 * It is woven rather than drawn: two tiles are laid in a grid, alternating row
 * by row, and the pair swaps over on every pulse — the same pulse a teleporter
 * flickers on. That swap is the whole of the animation.
 *
 * There is one weave per depth row, and the party's own square has none: a
 * wall of force is not something they can be standing in.
 *
 * The tiles, the grid and the placement are all transcribed.
 */
val WallByte.isAWallOfForce: Boolean get() = value == WALL_OF_FORCE

/**
 * The byte a square's four sides are set to while a wall of force stands on
 * it. Not a level's own wall: no file names it, and every floor means the
 * same thing by it.
 */
val WALL_OF_FORCE_BYTE = WallByte(WALL_OF_FORCE)

private const val WALL_OF_FORCE = 74

/**
 * One wall of force standing somewhere, and how long it has left.
 *
 * It is on a square rather than on a side: all four faces are raised at once,
 * so it cannot be walked round or entered from behind.
 */
data class AWallOfForce(
    val level: Int,
    val at: Location,
    val ticksLeft: Int,
)

/**
 * The walls of force standing anywhere in the dungeon.
 *
 * There is room for five and no more. A sixth does not fail and does not
 * queue: it takes the place of whichever of the five has least time left to
 * run, which is the one whose loss costs the party least.
 *
 * They are kept here rather than on the squares because a square only knows
 * the byte, and a byte cannot say when to stop being one.
 */
data class WallsOfForce(val standing: List<AWallOfForce> = emptyList()) {

    /**
     * Room made for another, and whichever was turned out to make it.
     *
     * Nothing is turned out while there is space, so the pair is a wall only
     * once five are already up.
     */
    fun roomForAnother(): Pair<WallsOfForce, AWallOfForce?> {
        if (standing.size < AT_ONCE) return this to null

        val goes = standing.minBy { it.ticksLeft }
        return WallsOfForce(standing - goes) to goes
    }

    fun raised(one: AWallOfForce) = WallsOfForce(standing + one)

    /** What is left after [by], and which of them ran out in that step. */
    fun runDown(by: Ticks): Pair<WallsOfForce, List<AWallOfForce>> {
        val stepped = standing.map { it.copy(ticksLeft = it.ticksLeft - by.value) }
        val (up, gone) = stepped.partition { it.ticksLeft > 0 }
        return WallsOfForce(up) to gone
    }

    companion object {
        /** How many may stand at once. */
        const val AT_ONCE = 5

        /**
         * How long one lasts: half a minute, and another half-minute for
         * every two levels the caster has. Read off a scroll, which is always
         * ninth level, that is five and a half minutes.
         *
         * Half a minute per two levels rather than per level, which is why
         * this is worked out here instead of with [SpellLasts] — that one
         * counts whole levels and cannot say a half of one.
         */
        fun lastsForACasterOf(level: Int) =
            Ticks(((level * SpellLasts.HALF_A_MINUTE) shr 1) + SpellLasts.HALF_A_MINUTE)
    }
}

/** Where one of the two tiles a curtain is woven from sits in DECORATE.CPS. */
data class ForceTile(val x: Int, val y: Int, val w: Int, val h: Int)

/**
 * How the curtain hangs at one depth: how many tiles across and down, where
 * its top edge falls, and the two tiles it alternates between.
 */
data class ForceCurtain(
    val top: ScreenY,
    val across: Int,
    val down: Int,
    val tiles: List<ForceTile>,
) {
    /**
     * Which tile the given row is woven from. Rows alternate, and the pulse
     * starts them on the other one — so the whole weave shifts by a row rather
     * than each tile changing in place.
     */
    fun tileFor(row: Int, pulse: TeleporterPulse): ForceTile =
        tiles[(row + if (pulse == TeleporterPulse.TRADED) 1 else 0) % tiles.size]
}

/**
 * Where a curtain's left edge falls at each of the squares in sight, in
 * [visibleBlocks] order. The party's own row has no curtain and no place for
 * one.
 */
val forceCurtainX: List<Int> = listOf(
    -52, -12, 28, 68, 108, 148, 188,
    -72, -8, 56, 120, 184,
    -56, 40, 136,
    0, 0, 0,
)

/**
 * The curtain as it hangs at each depth, farthest row first.
 *
 * The far one is woven from a coarser tile than the near ones — fewer, bigger
 * pieces rather than the same piece shrunk, which is how everything in the
 * cone that is not a wall is drawn.
 */
val forceCurtains: List<ForceCurtain> = listOf(
    ForceCurtain(
        top = ScreenY(32), across = 1, down = 2,
        tiles = listOf(ForceTile(64, 0, 40, 16), ForceTile(96, 0, 40, 16)),
    ),
    ForceCurtain(
        top = ScreenY(24), across = 2, down = 6,
        tiles = listOf(ForceTile(32, 0, 32, 8), ForceTile(32, 8, 32, 8)),
    ),
    ForceCurtain(
        top = ScreenY(16), across = 3, down = 9,
        tiles = listOf(ForceTile(0, 0, 32, 8), ForceTile(0, 8, 32, 8)),
    ),
)

/**
 * The squares in view standing behind a wall of force, as the blocks they are
 * drawn at.
 *
 * A square is asked about by the face it turns towards the party, the same one
 * a click is aimed at.
 */
fun wallsOfForceInView(
    party: Location,
    facing: Direction,
    wallAt: (Location, WallSide) -> Maz.WallType,
): List<ViewBlock> {
    val facingUs = facing.transformWallSide(WallSide.SOUTH)

    return viewBlockRows.values.flatten().filter { block ->
        val (dx, dy) = facing.transformCoordinates(block.relativeX, block.relativeY)
        wallAt(Location(party.x + dx, party.y + dy), facingUs).asByte().isAWallOfForce
    }
}
