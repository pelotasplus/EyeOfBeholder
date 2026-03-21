package pl.pelotasplus.eyeofbeholder.data

import co.touchlab.kermit.Logger

/**
 * LCW (Lempel-Castle-Welch) decompression — the compression algorithm used by
 * Westwood Studios in Eye of the Beholder and other games of the era.
 *
 * LCW-compressed data appears in .CPS (images), .VCN (tilesets), and .INF (level data)
 * files. The compressed data follows a file header that specifies the uncompressed size.
 *
 * ## Command types
 * The algorithm uses a byte-level command stream with 5 command types:
 *
 * 1. **Copy-as-is** (bit7=1, bit6=0): Copy N bytes verbatim from source to destination.
 *    Count 0 = end-of-data marker.
 * 2. **Short copy** (bit7=0): Copy 3-9 bytes from a recent position in the destination
 *    (12-bit relative back-reference).
 * 3. **Large copy** (bit7=1, bit6=1, count<0x3E): Copy N+3 bytes from a 16-bit
 *    back-reference (relative or absolute depending on mode).
 * 4. **Fill** (count==0x3E): Fill N bytes with a single value (run-length encoding).
 * 5. **Very large copy** (count==0x3F): Copy N bytes with both count and position
 *    as 16-bit values (for large back-references).
 *
 * ## Addressing modes
 * If the first byte of compressed data is 0x00, addresses are **relative** (offset
 * from current destination position). Otherwise, addresses are **absolute** (direct
 * index into the destination buffer).
 */
object LCWHelper {
    fun decompress(source: UByteArray, dest: UByteArray) {
        var sp = 0 // Source Pointer
        var dp = 0 // Destination Pointer

        // Determine if the addressing is relative based on the first byte
        val rel = source[sp].toInt() and 0xFF
        if (rel == 0) {
            sp++ // If relative, skip the indicator byte
        }

        while (true) {
            val com = source[sp].toInt() and 0xFF
            sp++
            val b7 = com shr 7 // Get bit 7 of Com

            if (b7 == 0) {
                // Copy command (type 2)
                // Count is bits 4-6 + 3
                val count = ((com and 0x7F) shr 4) + 3
                // Position is bits 0-3, with bits 0-7 of the next byte
                var posit = ((com and 0x0F) shl 8) + (source[sp].toInt() and 0xFF)
                sp++

                // Starting position = Current destination position - calculated value
                posit = dp - posit

                for (i in 0 until count) {
                    dest[dp] = dest[posit + i]
                    dp++
                }
            } else {
                // Check bit 6 of Com
                val b6 = (com and 0x40) shr 6

                if (b6 == 0) {
                    // Copy as is command (type 1)
                    val count = com and 0x3F // Mask 2 topmost bits
                    if (count == 0) {
                        Logger.d { "EOF marker found; sp $sp dp $dp; decompression complete" }
                        break // EOF marker
                    }

                    for (i in 0 until count) {
                        dest[dp] = source[sp]
                        dp++
                        sp++
                    }
                } else {
                    // Large copy, very large copy, and fill commands
                    // Count = (bits 0-5 of Com) + 3
                    var count = com and 0x3F

                    if (count < 0x3E) {
                        // Large copy (type 3)
                        count += 3
                        val posit = if (rel == 0) {
                            // Relative
                            dp - source.readU16LE(sp)
                        } else {
                            source.readU16LE(sp)
                        }
                        sp += 2

                        for (i in 0 until count) {
                            dest[dp] = dest[posit + i]
                            dp++
                        }
                    } else if (count == 0x3F) {
                        // Very large copy (type 5)
                        // Next 2 words are Count and Pos
                        count = source.readU16LE(sp)
                        val posit = if (rel == 0) {
                            // Relative
                            dp - source.readU16LE(sp + 2)
                        } else {
                            source.readU16LE(sp + 2)
                        }
                        sp += 4

                        for (i in 0 until count) {
                            dest[dp] = dest[posit + i]
                            dp++
                        }
                    } else {
                        // Count == 0x3E, fill (type 4)
                        // Next word is count, the byte after is color
                        count = source.readU16LE(sp)
                        sp += 2
                        val b = source[sp]
                        sp++

                        for (i in 0 until count) {
                            dest[dp] = b
                            dp++
                        }
                    }
                }
            }
        }
    }

    private fun UByteArray.readU16LE(offset: Int): Int {
        return (this[offset].toInt() and 0xFF) +
                ((this[offset + 1].toInt() and 0xFF) shl 8)
    }
}
