package pl.pelotasplus.eyeofbeholder.data.model

/**
 * How much of a square a kind of monster takes up, which is what says how many
 * of them can stand on one and where.
 *
 * It decides more than crowding. Something filling a square reaches the party
 * from anywhere on it, where four of something small have to be standing on
 * the right corner, and a square already holding a monster of one size will
 * not take one of another at all.
 */
enum class MonsterSize(val asWritten: Int) {
    /** Four to a square, one in each corner. */
    FOUR_TO_A_SQUARE(0),

    /** One to a square, standing in the middle of it. */
    ONE_TO_A_SQUARE(1),

    /** One to a square, and nothing else may enter it. */
    FILLS_THE_SQUARE(2);

    /** Whether a square holding one of these has room for anything else. */
    val shares: Boolean get() = this == FOUR_TO_A_SQUARE

    companion object {
        fun of(asWritten: Int) = entries.firstOrNull { it.asWritten == asWritten }
            ?: FILLS_THE_SQUARE
    }
}
