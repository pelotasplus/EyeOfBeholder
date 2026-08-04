package pl.pelotasplus.eyeofbeholder.data.model

/**
 * The four pieces of floor the party can reach into: the near half of the
 * square they stand on and of the one in front, each split left and right.
 *
 * Clicking one puts down what is being held, or picks up what is lying there
 * — the same place answers both, because a hand is either full or empty.
 */
enum class FloorReach(
    private val x: Int,
    private val y: Int,
    private val width: Int,
    private val height: Int,
    /** False for the square the party stand on. */
    val aheadOfTheParty: Boolean,
    /** The corner of the view this piece of floor is, for [placeFacing]. */
    private val seenAt: ViewPlace,
) {
    OWN_LEFT(0, 102, 88, 18, aheadOfTheParty = false, seenAt = ViewPlace.FAR_LEFT),
    OWN_RIGHT(89, 102, 88, 18, aheadOfTheParty = false, seenAt = ViewPlace.FAR_RIGHT),
    AHEAD_LEFT(0, 72, 88, 29, aheadOfTheParty = true, seenAt = ViewPlace.NEAR_LEFT),
    AHEAD_RIGHT(89, 72, 88, 29, aheadOfTheParty = true, seenAt = ViewPlace.NEAR_RIGHT);

    /**
     * Where on its square this is in maze terms, which depends on which way
     * the party are looking: the corner on their left is a different corner of
     * the square each time they turn.
     */
    fun placeFacing(direction: Direction): SquarePlace = seenAt.onASquareFacing(direction)

    fun contains(screenX: Int, screenY: Int): Boolean =
        screenX in x until x + width && screenY in y until y + height

    companion object {
        fun at(screenX: Int, screenY: Int): FloorReach? =
            entries.firstOrNull { it.contains(screenX, screenY) }
    }
}
