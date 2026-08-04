package pl.pelotasplus.eyeofbeholder.data.model

/**
 * How far away an item lying at [place] on a square [dim] rows ahead is drawn,
 * where dim runs 0 (three rows ahead) to 3 (the party's own square).
 *
 * Not visible ([ScaleSteps.isVisible]) means the item is not drawn there at
 * all — too far to make out, or behind the party on their own square. Nothing
 * is drawn in the middle of a square either: an item is there only while it is
 * in the air.
 */
fun itemScaleStepsAt(dim: Int, place: ViewPlace): ScaleSteps =
    if (place == ViewPlace.MIDDLE) NOT_DRAWN
    else itemScaleSteps[dim * CORNERS + place.ordinal]

private val itemScaleSteps: List<ScaleSteps> = listOf(
    -1, -1, 3, 3,
    2, 2, 2, 2,
    1, 1, 1, 1,
    0, 0, -1, -1,
).map { ScaleSteps(it) }

private val NOT_DRAWN = ScaleSteps(-1)

private const val CORNERS = 4

/**
 * Screen X of an item sitting in a wall niche, per visible block, before
 * centering it on the icon's width.
 */
val nicheItemX: List<ScreenX> = listOf(
    -56, -8, 40, 88, 136, 184, 232,
    -72, 8, 88, 168, 248,
    -40, 88, 216,
    -88, 88, 264,
).map { ScreenX(it) }

/**
 * How far a thing is nudged from the middle of where it lies, so that two
 * things sharing a corner or a shelf do not sit exactly on top of one
 * another.
 *
 * @property across sideways, which every thing gets
 * @property down along the floor, which only a thing lying on one gets — a
 *   shelf has no depth to move a thing along
 */
data class ItemNudge(val across: Int, val down: Int)

/**
 * The nudge is taken from the thing's own place in the world's table, so it
 * never changes for a given thing and differs between any two that happen to
 * come to rest together.
 */
fun nudgeOf(item: ItemIndex) = ItemNudge(
    across = nudges[item.value and WRAP] * ACROSS_STEP,
    down = nudges[(item.value shr 1) and WRAP],
)

private val nudges = listOf(0, -2, 1, -1, 2, 0, 1, -1)

/** The sideways nudge is taken in twos, the one along the floor in ones. */
private const val ACROSS_STEP = 2

/** Eight nudges, so every eighth thing in the table shares one. */
private const val WRAP = 7

/** Niche-item baseline Y per depth row (dim 0-3); the icon's bottom edge. */
val nicheItemY: List<ScreenY> = listOf(37, 49, 56, 0).map { ScreenY(it) }
