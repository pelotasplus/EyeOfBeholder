package pl.pelotasplus.eyeofbeholder.data.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import pl.pelotasplus.eyeofbeholder.data.ByteReader
import kotlin.math.abs

/**
 * A position on the 32×32 dungeon grid.
 *
 * Used throughout the game data for item placement, monster positions, teleport
 * destinations, and script triggers.
 *
 * ## Packed binary format
 * Stored as a single u16 little-endian value:
 * - Bits 0-4 (lower 5 bits): x coordinate (0-31)
 * - Bits 5-15: y coordinate (value / 32)
 *
 * A Location of (-1, -1) or similar invalid values means "not placed" / "no location".
 */
@Serializable
data class Location(
    val x: Int,
    val y: Int
) {
    /** The same square packed the way a record on disk writes it. */
    val asBlock: Int get() = (y shl 5) or (x and 31)

    /**
     * How many squares away [other] is, counting a diagonal as one.
     *
     * It is how far something has to walk rather than how far away it looks,
     * which is what a monster deciding whether the party are worth noticing
     * wants to know.
     */
    fun squaresFrom(other: Location) =
        maxOf(abs(other.x - x), abs(other.y - y))

    companion object {
        fun read(reader: ByteReader): Location {
            val pos = reader.readU16LE()
            return ofBlock(pos)
        }

        /** The square a packed word names. */
        fun ofBlock(block: Int) = Location(x = block and 31, y = block shr 5)
    }
}

/**
 * A square goes into a save as the one packed word the game writes for it,
 * under the name a save already gives it, so that a save written before it had
 * a type of its own still reads.
 */
object LocationAsAPackedBlock : KSerializer<Location> {
    override val descriptor = PrimitiveSerialDescriptor("Location", PrimitiveKind.INT)

    override fun serialize(encoder: Encoder, value: Location) = encoder.encodeInt(value.asBlock)

    override fun deserialize(decoder: Decoder): Location = Location.ofBlock(decoder.decodeInt())
}
