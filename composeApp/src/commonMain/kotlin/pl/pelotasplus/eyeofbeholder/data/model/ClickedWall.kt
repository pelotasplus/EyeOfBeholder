package pl.pelotasplus.eyeofbeholder.data.model

/**
 * Whether a click in the view landed on what a wall has on it.
 *
 * A click is not aimed: it always means the wall of the square ahead that
 * faces the party, wherever in the view it fell. What its position decides is
 * only whether it hit the thing hanging there — the button, the lever, the
 * keyhole — and missing it does nothing even though the wall is clickable.
 *
 * The rectangle tested is the decoration as drawn at the near distance, where
 * the party stand to reach it, and it is generous by a few pixels on every
 * side because the original is.
 */
object ClickedWall {

    /**
     * Which of the ten distances a decoration is drawn at is the wall the
     * party are standing in front of.
     */
    private const val WITHIN_REACH = 1

    /** The original's slop: four pixels above and left, eight below and right. */
    private const val SLOP_BEFORE = 4
    private const val SLOP_AFTER = 8

    /** The view is this wide, which a mirrored decoration is measured from. */
    private const val VIEW_WIDTH = 176

    /**
     * True where [x] and [y] fall on [decoration] as it hangs on the wall
     * ahead, or where it has nothing to hit and the whole wall counts.
     */
    fun hits(decoration: Dec.Decoration, rectangles: List<Dec.DecorationRectangle>, x: Int, y: Int): Boolean {
        val rectangle = decoration.rectangleIndices.getOrNull(WITHIN_REACH)
            ?.let { rectangles.getOrNull(it) }
            ?: return false

        val width = rectangle.w * ViewPort.TILE_SIZE
        val left = decoration.xCoords.getOrNull(WITHIN_REACH)?.let { drawnAt ->
            if (decoration.isMirrored) VIEW_WIDTH - drawnAt - width else drawnAt
        } ?: return false
        val top = decoration.yCoords.getOrNull(WITHIN_REACH) ?: return false

        return x in (left - SLOP_BEFORE) until (left + width + SLOP_AFTER) &&
            y in (top - SLOP_BEFORE) until (top + rectangle.h + SLOP_AFTER)
    }
}

/** Decorations on a front-facing wall are drawn the other way round. */
val Dec.Decoration.isMirrored: Boolean get() = flags and 1 != 0
