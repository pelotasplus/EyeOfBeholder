package pl.pelotasplus.eyeofbeholder.data

import co.touchlab.kermit.Logger

/**
 * LCW (Lempel-Castle-Welch, "Format80") decompression — the compression
 * algorithm used by Westwood Studios in Eye of the Beholder and other games of
 * the era.
 *
 * LCW-compressed data appears in .CPS (images), .VCN (tilesets), and .INF (level data)
 * files. The compressed data follows a file header that specifies the uncompressed size.
 *
 * ## Command types
 * The algorithm uses a byte-level command stream with 5 command types:
 *
 * 1. **Copy-as-is** (bit7=1, bit6=0): Copy N bytes verbatim from source to destination.
 *    Command 0x80 = end-of-data marker.
 * 2. **Short copy** (bit7=0): Copy 3-9 bytes from a recent position in the destination
 *    (12-bit back-reference, relative to the current position).
 * 3. **Large copy** (bit7=1, bit6=1, count<0x3E): Copy N+3 bytes from a 16-bit
 *    offset counted from the *start* of the destination.
 * 4. **Fill** (0xFE): Fill N bytes with a single value (run-length encoding).
 * 5. **Very large copy** (0xFF): Copy N bytes with both count and offset as
 *    16-bit values (for large back-references).
 *
 * Decoding stops at the end-of-data marker or as soon as the destination is
 * full, and every count is clamped to the space left. Some shipped EoB2 assets
 * (DRANX.CPS, DOORWAY1.CPS, KHELBAN1.CPS) have streams that run past the end of
 * the image or reference before its start; the original engine clamps rather
 * than failing, producing a partial image, so this does too.
 */
object LCWHelper {
    fun decompress(source: UByteArray, dest: UByteArray) {
        var sp = 0 // Source Pointer
        var dp = 0 // Destination Pointer

        while (dp < dest.size && sp < source.size) {
            val com = source[sp].toInt() and 0xFF
            sp++
            val remaining = dest.size - dp

            if (com and 0x80 == 0) {
                // Short copy (type 2): count in bits 4-6, 12-bit back-reference
                val count = minOf(remaining, (com shr 4) + 3)
                val posit = dp - (((com and 0x0F) shl 8) + source.byteAt(sp))
                sp++

                for (i in 0 until count) {
                    // reads before the buffer start read as 0, as an empty
                    // destination page would in the original engine
                    dest[dp] = if (posit + i < 0) ZERO else dest[posit + i]
                    dp++
                }
            } else if (com and 0x40 != 0) {
                // Fill (type 4), very large copy (type 5) or large copy (type 3)
                if (com == 0xFE) {
                    val count = minOf(remaining, source.readU16LE(sp))
                    sp += 2
                    val value = source.byteAt(sp).toUByte()
                    sp++

                    for (i in 0 until count) {
                        dest[dp] = value
                        dp++
                    }
                } else {
                    var count = (com and 0x3F) + 3
                    if (com == 0xFF) {
                        count = source.readU16LE(sp)
                        sp += 2
                    }
                    val posit = source.readU16LE(sp)
                    sp += 2
                    count = minOf(remaining, count)

                    for (i in 0 until count) {
                        dest[dp] = if (posit + i >= dest.size) ZERO else dest[posit + i]
                        dp++
                    }
                }
            } else if (com == 0x80) {
                Logger.d { "EOF marker found; sp $sp dp $dp; decompression complete" }
                break
            } else {
                // Copy as is (type 1)
                val count = minOf(remaining, com and 0x3F, source.size - sp)

                for (i in 0 until count) {
                    dest[dp] = source[sp]
                    dp++
                    sp++
                }
            }
        }

        if (dp < dest.size) {
            Logger.d { "Stream ended with $dp of ${dest.size} bytes decoded" }
        }
    }

    private val ZERO: UByte = 0u

    /** Reads past the end of a truncated stream yield 0 instead of throwing. */
    private fun UByteArray.byteAt(offset: Int): Int =
        if (offset < size) this[offset].toInt() and 0xFF else 0

    private fun UByteArray.readU16LE(offset: Int): Int =
        byteAt(offset) + (byteAt(offset + 1) shl 8)
}
