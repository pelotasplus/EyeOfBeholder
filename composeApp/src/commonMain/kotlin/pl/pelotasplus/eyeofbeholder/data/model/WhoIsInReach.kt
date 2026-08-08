package pl.pelotasplus.eyeofbeholder.data.model

/**
 * Which of the things on the square ahead a champion's arm comes down on.
 *
 * A square holds up to four monsters in its corners, or one big one in the
 * middle, and a blow lands on one of them. The middle always wins: something
 * filling the square is what the arm meets whatever else is there. Otherwise
 * each champion reaches for their own side of the square first and across it
 * second, so the two in the front rank do not both hit the same creature while
 * another stands untouched beside it.
 *
 * The order is the original's table rather than a rule fitted to it. A rule is
 * tempting — near before far, own side before the other — and it is right for
 * three of the four directions and wrong facing west, where the two far
 * corners come the other way round.
 */
object WhoIsInReach {

    /**
     * The corners of the square ahead in the order [whose] reaches them, when
     * the party face [facing].
     *
     * @param whose which champion of the party, of whom only the front rank
     *   reach at all — an even slot stands on the left of the pair.
     */
    fun cornersFor(facing: Direction, whose: PartySlot): List<SquarePlace> {
        val from = (facing.ordinal * CORNERS * HANDS) + ((whose.index and 1) * CORNERS)
        return REACHED_IN_ORDER.subList(from, from + CORNERS).map { SquarePlace.entries[it] }
    }

    /**
     * Which corner each champion reaches for first, second, third and fourth,
     * for each way the party can face. From the original.
     *
     * Four directions, two sides of the front rank, four corners named as the
     * maze has them.
     */
    private val REACHED_IN_ORDER = listOf(
        2, 3, 0, 1, /**/ 3, 2, 1, 0, // north
        0, 2, 1, 3, /**/ 2, 0, 3, 1, // east
        1, 0, 3, 2, /**/ 0, 1, 2, 3, // south
        3, 1, 0, 2, /**/ 1, 3, 2, 0, // west
    )

    private const val CORNERS = 4
    private const val HANDS = 2
}

/**
 * Whether this champion can reach anything at all with a hand weapon.
 *
 * Only the front rank of the party are in front of anything; the two behind
 * them can throw and shoot but not strike.
 */
val PartySlot.inTheFrontRank: Boolean get() = index < FRONT_RANK

private const val FRONT_RANK = 2
