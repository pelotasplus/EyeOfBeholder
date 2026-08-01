package pl.pelotasplus.eyeofbeholder.data.model

import kotlin.jvm.JvmInline
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * How far from the party something being drawn is, in hundredths of a square:
 * the square straight ahead is 100, the one diagonally ahead 141, two rows
 * ahead 200.
 *
 * Draw order alone cannot express this. Something standing on a square has to
 * be drawn after the wall at that square's far end, or the wall would cover it,
 * but before the walls of the squares in front of it — and those walls are
 * drawn earlier still, because that is the order the walls need among
 * themselves. So each pixel remembers how far away whatever painted it was, and
 * a sprite skips the pixels where something closer already is.
 *
 * Measuring to the square is what makes the diagonal square further than the one
 * straight ahead, so a tree directly in front of the party (100) hides what lies
 * on the square beside it (141). Within a square, its near face, its contents
 * and its far face sit a fraction apart, never far enough to reorder two
 * squares.
 */
@JvmInline
value class DistanceFromParty(private val hundredthsOfASquare: Int) :
    Comparable<DistanceFromParty> {

    override fun compareTo(other: DistanceFromParty): Int =
        hundredthsOfASquare.compareTo(other.hundredthsOfASquare)

    companion object {
        /** Further away than anything: the backdrop, and pixels nothing has claimed. */
        val BEYOND_EVERYTHING = DistanceFromParty(Int.MAX_VALUE)

        /** The face of a square turned towards the party. */
        fun nearSideOfSquare(relativeX: Int, relativeY: Int) =
            DistanceFromParty(distanceTo(relativeX, relativeY) - FACE)

        /** Items and monsters, which stand in the middle of their square. */
        fun standingOnSquare(relativeX: Int, relativeY: Int) =
            DistanceFromParty(distanceTo(relativeX, relativeY))

        /** The face of a square turned away from the party. */
        fun farSideOfSquare(relativeX: Int, relativeY: Int) =
            DistanceFromParty(distanceTo(relativeX, relativeY) + FACE)

        private fun distanceTo(relativeX: Int, relativeY: Int): Int =
            (hypot(relativeX.toDouble(), relativeY.toDouble()) * 100).roundToInt()

        /**
         * How far a square's faces sit from its middle. Comfortably inside the
         * 41 hundredths between the square ahead and the one diagonal to it, so
         * a face never overtakes a neighbouring square.
         */
        private const val FACE = 30
    }
}
