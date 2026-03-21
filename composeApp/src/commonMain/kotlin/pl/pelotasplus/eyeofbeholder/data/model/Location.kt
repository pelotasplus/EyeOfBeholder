package pl.pelotasplus.eyeofbeholder.data.model

import pl.pelotasplus.eyeofbeholder.data.ByteReader

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
data class Location(
    val x: Int,
    val y: Int
) {
    companion object {
        fun read(reader: ByteReader): Location {
            val pos = reader.readU16LE()
            val x = pos and 31
            val y = pos / 32
            return Location(x, y)
        }
    }
}
