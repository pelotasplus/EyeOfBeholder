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

private const val WALL_OF_FORCE = 74

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
