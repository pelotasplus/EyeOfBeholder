package pl.pelotasplus.eyeofbeholder.data.model

/**
 * The four pieces of floor the party can reach into: the near half of the
 * square they stand on and of the one in front, each split left and right.
 *
 * Clicking one puts down what is being held, or picks up what is lying there
 * — the same place answers both, because a hand is either full or empty.
 *
 * The rectangles and the table of quadrants come from the original game.
 */
enum class FloorReach(
    private val x: Int,
    private val y: Int,
    private val width: Int,
    private val height: Int,
    /** False for the square the party stand on. */
    val aheadOfTheParty: Boolean,
    /** Which of the four the original counts this as, for [quadrantFacing]. */
    private val corner: Int,
) {
    OWN_LEFT(0, 102, 88, 18, aheadOfTheParty = false, corner = 0),
    OWN_RIGHT(89, 102, 88, 18, aheadOfTheParty = false, corner = 1),
    AHEAD_LEFT(0, 72, 88, 29, aheadOfTheParty = true, corner = 2),
    AHEAD_RIGHT(89, 72, 88, 29, aheadOfTheParty = true, corner = 3);

    /**
     * Which quadrant of its square this is in maze terms, which depends on
     * which way the party are looking: the near left corner as they see it is
     * a different corner of the square each time they turn.
     */
    fun quadrantFacing(direction: Direction): Int =
        dropQuadrant[direction.ordinal * CORNERS + corner]

    fun contains(screenX: Int, screenY: Int): Boolean =
        screenX in x until x + width && screenY in y until y + height

    companion object {
        fun at(screenX: Int, screenY: Int): FloorReach? =
            entries.firstOrNull { it.contains(screenX, screenY) }

        private const val CORNERS = 4
    }
}

/**
 * The maze quadrant each of the four reachable corners is, per facing. The
 * inverse of the rotation that decides where a thing on the floor is drawn:
 * one turns a square's own corner into the view's, this turns the view's back
 * into the square's.
 */
private val dropQuadrant: List<Int> = listOf(
    0, 1, 2, 3,
    1, 3, 0, 2,
    3, 2, 1, 0,
    2, 0, 3, 1,
)
