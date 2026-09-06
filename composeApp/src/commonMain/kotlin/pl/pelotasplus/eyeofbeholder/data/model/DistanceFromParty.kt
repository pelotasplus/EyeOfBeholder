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
 * on the square beside it (141). A square's contents and the faces of it sit a
 * fraction apart, never far enough to reorder two squares.
 */
@JvmInline
value class DistanceFromParty(private val hundredthsOfASquare: Int) :
    Comparable<DistanceFromParty> {

    override fun compareTo(other: DistanceFromParty): Int =
        hundredthsOfASquare.compareTo(other.hundredthsOfASquare)

    companion object {
        /** Further away than anything: the backdrop, and pixels nothing has claimed. */
        val BEYOND_EVERYTHING = DistanceFromParty(Int.MAX_VALUE)

        /** Items and monsters, which stand in the middle of their square. */
        fun standingOnSquare(relativeX: Int, relativeY: Int) =
            DistanceFromParty(distanceTo(relativeX, relativeY))

        /**
         * The face a square turns towards the party, half a square in front of
         * its middle, which is where a wall across the view stands.
         *
         * In front of the middle rather than behind it, because that is where
         * the face is: measured at the square instead, a wall would sort behind
         * its own square's contents and a thing lying round the corner would
         * paint over the very wall that hides it.
         */
        fun faceTowardsTheParty(relativeX: Int, relativeY: Int) =
            DistanceFromParty(distanceToPoint(relativeX.toDouble(), relativeY + HALF_A_SQUARE))

        /**
         * And what is set into that face, which is a hair in front of it.
         *
         * A niche is cut into the wall rather than standing on the floor behind
         * it. Measured at its square it would be further off than the very wall
         * it sits in, and the wall would cover it.
         */
        fun shelvedInThatFace(relativeX: Int, relativeY: Int) =
            DistanceFromParty(
                faceTowardsTheParty(relativeX, relativeY).hundredthsOfASquare - A_HAIR,
            )

        /**
         * A wall seen side-on, measured out at the far end of its square.
         *
         * Deliberately the far end, and deliberately not where the wall
         * actually is. Such a wall runs alongside its square rather than across
         * the view, so what stands on the next square along is beside it rather
         * than behind it, however near its near end comes. Measured honestly it
         * would cut those sprites — a wolf loses its tail, a cleric an arm. How
         * much of a square a side wall leaves showing is settled before
         * anything is drawn, and not by distance.
         */
        fun farSideOfSquare(relativeX: Int, relativeY: Int) =
            DistanceFromParty(distanceTo(relativeX, relativeY) + FACE)

        private fun distanceTo(relativeX: Int, relativeY: Int): Int =
            distanceToPoint(relativeX.toDouble(), relativeY.toDouble())

        private fun distanceToPoint(x: Double, y: Double): Int =
            (hypot(x, y) * 100).roundToInt()

        private const val HALF_A_SQUARE = 0.5

        /**
         * How far a side wall is pushed past its square's middle. Comfortably
         * inside the 41 hundredths between the square ahead and the one
         * diagonal to it, so it never overtakes a neighbouring square.
         */
        private const val FACE = 30

        /** Enough to sort in front of the face, and not enough to sort in front of anything else. */
        private const val A_HAIR = 1
    }
}
