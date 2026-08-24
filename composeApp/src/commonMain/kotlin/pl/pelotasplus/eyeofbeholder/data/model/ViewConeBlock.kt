package pl.pelotasplus.eyeofbeholder.data.model

/**
 * One square of the view cone that can hold something standing on it — an
 * item, a monster — ordered back-to-front.
 *
 * This is the counterpart of [ViewSlot], which covers the walls: walls are
 * drawn per face, things are drawn per square.
 *
 * @property relativeX X offset from the player when facing NORTH (negative = left)
 * @property relativeY Y offset from the player when facing NORTH (negative = ahead)
 * @property blockIndex The visible-block index (0-17) used to
 *           address [blockScreenCoords]
 * @property scaleSteps 2/3-shrink steps applied at this distance
 */
data class ViewBlock(
    val relativeX: Int,
    val relativeY: Int,
    val blockIndex: Int,
    val scaleSteps: ScaleSteps,
)

/**
 * Visible blocks by depth row, nearest last.
 *
 * The party's own row is not one of them: the square they stand on is drawn
 * on its own, at [ViewPort.OWN_BLOCK_INDEX], and the two beside it are not
 * drawn at all.
 */
val viewBlockRows: Map<Int, List<ViewBlock>> = mapOf(
    -3 to (-3..3).mapIndexed { i, vx -> ViewBlock(vx, -3, i, scaleSteps = ScaleSteps(2)) },
    -2 to (-2..2).mapIndexed { i, vx -> ViewBlock(vx, -2, 7 + i, scaleSteps = ScaleSteps(1)) },
    -1 to (-1..1).mapIndexed { i, vx -> ViewBlock(vx, -1, 12 + i, scaleSteps = ScaleSteps(0)) },
)

/**
 * Where within a block a thing standing at [place] is drawn: [BlockSpot.x]
 * from the middle of the viewport, [BlockSpot.y] from the baseline its feet
 * stand on.
 */
fun blockSpot(blockIndex: Int, place: ViewPlace): BlockSpot {
    val coord = (blockIndex * ViewPlace.entries.size + place.ordinal) * 2
    return BlockSpot(blockScreenCoords[coord], blockScreenCoords[coord + 1])
}

/** One of the five places a block draws a thing at, as an offset. */
data class BlockSpot(val x: Int, val y: Int)

/**
 * Screen coordinates for objects in the view cone: 18 visible blocks ×
 * 5 sub-positions × (x, y). x is relative to the viewport horizontal center
 * (88); y to the baseline — 127 for monsters, 124 for items. The sprite is
 * drawn centered on x with its feet on y. Shared by monsters everywhere and
 * by items lying on the party's own square (block 16).
 */
val blockScreenCoords: List<Int> = listOf(
    // blocks 0-6: three rows ahead
    -111, -63, -95, -63, -139, -59, -117, -59, -120, -61,
    -76, -63, -60, -63, -95, -59, -74, -59, -80, -61,
    -43, -63, -27, -63, -53, -59, -31, -59, -40, -61,
    -8, -63, 8, -63, -10, -59, 10, -59, 0, -61,
    27, -63, 43, -63, 31, -59, 53, -59, 40, -61,
    60, -63, 76, -63, 74, -59, 95, -59, 80, -61,
    95, -63, 111, -63, 117, -59, 139, -59, 120, -61,
    // blocks 7-11: two rows ahead
    -118, -53, -92, -53, -152, -45, -120, -45, -118, -50,
    -66, -53, -40, -53, -84, -45, -51, -45, -59, -50,
    -13, -53, 13, -53, -16, -45, 16, -45, 0, -50,
    40, -53, 66, -53, 51, -45, 84, -45, 59, -50,
    92, -53, 118, -53, 120, -45, 152, -45, 118, -50,
    // blocks 12-14: one row ahead
    -110, -35, -67, -35, -140, -22, -83, -22, -98, -30,
    -22, -35, 22, -35, -27, -22, 27, -22, 0, -30,
    67, -35, 110, -35, 83, -22, 140, -22, 98, -30,
    // blocks 15-17: the party's own row. Only 16, the square underfoot, is
    // ever seen. The two beside it carry the same 128 either way — the game's
    // own numbers, and identical for both, so they do not even say which side
    // is which — and 128 from the middle of a 176-wide viewport is 216 or -40
    // once the middle is added back. An icon is sixteen pixels. Nothing put on
    // those two squares can land on the screen at all.
    -128, -4, 128, -4, -128, -66, 128, -66, 128, 0,
    -38, -4, 38, -4, -38, -66, 38, -66, 0, 0,
    -128, -4, 128, -4, -128, -66, 128, -66, 128, 0,
)
