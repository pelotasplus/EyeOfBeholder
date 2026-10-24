package pl.pelotasplus.eyeofbeholder.data.model

/**
 * Whether a thing in the air is drawn over a quarter of a square in view, and
 * how far it is shrunk there.
 *
 * Not every quarter shows one. Over the party's own square only the two far
 * quarters do — a thing just let go of is not seen until it is halfway across
 * — and at the edges of the view only the quarters towards the middle do. The
 * table is data: nothing works it out, a value changed by eye has nothing to
 * catch it but the goldens of things in flight, and a row is four quarters in
 * the order [ViewPlace] keeps them.
 */
object InFlightSight {

    /** How far a thing over [corner] of the square at [blockIndex] is shrunk, or null where it is not drawn. */
    fun shrunkBy(blockIndex: Int, corner: ViewPlace): ScaleSteps? {
        if (corner == ViewPlace.MIDDLE) return null
        val value = TABLE.getOrNull(blockIndex * CORNERS + corner.ordinal) ?: return null
        return if (value == NOT_DRAWN) null else ScaleSteps(value)
    }

    private const val CORNERS = 4
    private const val NOT_DRAWN = -1

    private val TABLE = listOf(
        -1, -1, -1, -1,
        -1, -1, -1, 3,
        -1, -1, 3, 3,
        -1, -1, 3, 3,
        -1, -1, 3, 3,
        -1, -1, 3, -1,
        -1, -1, -1, -1,
        -1, -1, -1, -1,
        2, 2, 2, 2,
        2, 2, 2, 2,
        2, 2, 2, 2,
        -1, -1, -1, -1,
        -1, 1, -1, 1,
        1, 1, 1, 1,
        1, -1, 1, -1,
        -1, -1, -1, -1,
        0, 0, -1, -1,
        -1, -1, -1, -1,
    )
}
