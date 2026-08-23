package pl.pelotasplus.eyeofbeholder.data.model

/**
 * How much of a square a kind of monster takes up, which is what says how many
 * of them can stand on one and where.
 *
 * It decides more than crowding. Something that shares a square with only one
 * other reaches the party from either place it may stand, where four of
 * something small have to be standing on the right corner, and a square
 * already holding a monster of one size will not take one of another at all.
 *
 * Whatever the size, one on its own stands in the middle of its square and
 * moves aside only when another arrives.
 */
enum class MonsterSize(val asWritten: Int, val toASquare: Int) {
    /** Four to a square, one in each corner. */
    FOUR_TO_A_SQUARE(0, 4),

    /**
     * Two to a square, in opposite corners. Level 4's wolves are these, which
     * is what lets a pack close on the party two at a time.
     */
    TWO_TO_A_SQUARE(1, 2),

    /** One to a square, and nothing else may enter it. */
    FILLS_THE_SQUARE(2, 1);

    /** Whether a square holding one of these has room for anything else. */
    val shares: Boolean get() = toASquare > 1

    /**
     * Whether its arm reaches the party from wherever on its square it stands.
     * Only the small ones have to be on the right corner for it; anything
     * bigger fills enough of the square to reach from either side of it.
     */
    val reachesFromAnywhere: Boolean get() = this != FOUR_TO_A_SQUARE

    companion object {
        fun of(asWritten: Int) = entries.firstOrNull { it.asWritten == asWritten }
            ?: FILLS_THE_SQUARE
    }
}
