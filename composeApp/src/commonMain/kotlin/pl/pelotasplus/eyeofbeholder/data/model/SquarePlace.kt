package pl.pelotasplus.eyeofbeholder.data.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Where on a square a thing is: one of the four corners of its floor, the
 * middle of it, or shelved in a niche of one of its walls.
 *
 * A square holds several things at once, and this is what tells them apart —
 * a chest in one corner is picked up on its own, and the party can only reach
 * the corners nearest them.
 *
 * The corners are named as the maze has them, which is not how the party see
 * them: the corner on their left is a different corner of the square each time
 * they turn. [asSeenFacing] turns one into the other, and [ViewPlace] is what
 * it turns into.
 */
enum class SquarePlace(val asWritten: Int) {
    NORTH_WEST(0),
    NORTH_EAST(1),
    SOUTH_WEST(2),
    SOUTH_EAST(3),

    /**
     * In no corner at all. A monster too big for one stands in the middle of
     * the square and is drawn there; a thing in the air over it is in no
     * corner either, and is drawn by whatever threw it rather than by the
     * square it is passing over.
     */
    MIDDLE(4),

    /** On a wall of the square rather than on its floor. */
    IN_A_NICHE(8);

    val onTheFloor: Boolean get() = ordinal < CORNERS

    /**
     * Where this is drawn when the party face [facing] — or nowhere, for a
     * thing that is not on the square at all: a niche is drawn where its wall
     * is, and so turns with the walls rather than with the floor.
     */
    fun asSeenFacing(facing: Direction): ViewPlace? = when (this) {
        IN_A_NICHE -> null
        MIDDLE -> ViewPlace.MIDDLE
        else -> ViewPlace.entries[asThePartySeeIt[facing.ordinal * CORNERS + ordinal]]
    }

    companion object {
        /**
         * What the byte in a record means. Anything that is neither a corner
         * nor a niche is the middle — the four values a thing in the air takes
         * among them, none of which anything here throws yet.
         */
        fun of(asWritten: Int) = entries.firstOrNull { it.asWritten == asWritten } ?: MIDDLE
    }
}

/**
 * Where on a square something is drawn, as the party see it: [ViewBlock] says
 * which of the squares in front of them a thing stands on, this says where on
 * that square.
 *
 * Declared in the order the screen coordinates are kept in, five to a block.
 */
enum class ViewPlace {
    FAR_LEFT,
    FAR_RIGHT,
    NEAR_LEFT,
    NEAR_RIGHT,
    MIDDLE;

    /** Which corner of the square this is when the party face [facing]. */
    fun onASquareFacing(facing: Direction): SquarePlace =
        if (this == MIDDLE) SquarePlace.MIDDLE
        else SquarePlace.entries[asTheSquareHasIt[facing.ordinal * CORNERS + ordinal]]
}

/** Which corner of the view each corner of a square is, per facing. */
private val asThePartySeeIt = listOf(
    0, 1, 2, 3,
    2, 0, 3, 1,
    3, 2, 1, 0,
    1, 3, 0, 2,
)

/**
 * The same the other way about: which corner of a square each corner of the
 * view is. Every row is the inverse of the row above it in [asThePartySeeIt],
 * which is what lets a thing be put down where it was seen lying.
 */
private val asTheSquareHasIt = listOf(
    0, 1, 2, 3,
    1, 3, 0, 2,
    3, 2, 1, 0,
    2, 0, 3, 1,
)

private const val CORNERS = 4

/**
 * A thing's place goes into a save as the number the game writes for it, under
 * the name a save already gives it, so that a save written before it had a
 * type of its own still reads.
 */
object SquarePlaceAsTheGameWritesIt : KSerializer<SquarePlace> {
    override val descriptor = PrimitiveSerialDescriptor("SquarePlace", PrimitiveKind.INT)

    override fun serialize(encoder: Encoder, value: SquarePlace) =
        encoder.encodeInt(value.asWritten)

    override fun deserialize(decoder: Decoder): SquarePlace =
        SquarePlace.of(decoder.decodeInt())
}
