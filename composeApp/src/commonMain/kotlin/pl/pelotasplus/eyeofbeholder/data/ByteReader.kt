package pl.pelotasplus.eyeofbeholder.data

/**
 * Sequential binary data reader for parsing DOS-era game file formats.
 *
 * All Eye of the Beholder data files use little-endian byte order and a mix
 * of unsigned/signed integer types. This reader provides safe, sequential
 * access with bounds checking on every read.
 *
 * ## Common patterns in EoB file formats
 * - u8: single byte (wall types, flags, palette indices, opcodes)
 * - i8: signed byte (item values, monster AC, coordinates that can be negative)
 * - u16LE: 2 bytes little-endian (file offsets, counts, packed locations)
 * - i16LE: signed 16-bit (linked-list pointers where -1 = "no link")
 * - u32LE: 4 bytes (uncompressed file sizes in CPS/VCN headers)
 * - Fixed-length strings: null-terminated within a fixed buffer (filenames = 13 chars)
 *
 * The [offset] advances automatically with each read. Use [remaining] to check
 * how many bytes are left (all parsers verify remaining == 0 at the end).
 */
class ByteReader(private val bytes: UByteArray) {
    var offset: Int = 0
        private set

    val size: Int get() = bytes.size

    val remaining: Int get() = bytes.size - offset

    fun readU8(): Int {
        check(offset < bytes.size) { "Read past end of buffer" }
        return bytes[offset++].toInt()
    }

    fun readI8(): Int {
        check(offset < bytes.size) { "Read past end of buffer" }
        return bytes[offset++].toByte().toInt()
    }

    fun readU16LE(): Int {
        check(offset + 1 < bytes.size) { "Read past end of buffer" }
        val value = (bytes[offset].toInt()) +
                (bytes[offset + 1].toInt() shl 8)
        offset += 2
        return value
    }

    fun readI16LE(): Int {
        check(offset + 1 < bytes.size) { "Read past end of buffer" }
        val value = (bytes[offset].toInt()) +
                (bytes[offset + 1].toInt() shl 8)
        offset += 2
        // Sign-extend from 16-bit to 32-bit
        return if (value >= 0x8000) value - 0x10000 else value
    }

    fun readU32LE(): Int {
        check(offset + 3 < bytes.size) { "Read past end of buffer" }
        val value = (bytes[offset].toInt()) +
                (bytes[offset + 1].toInt() shl 8) +
                (bytes[offset + 2].toInt() shl 16) +
                (bytes[offset + 3].toInt() shl 24)
        offset += 4
        return value
    }

    fun readBytes(count: Int): UByteArray {
        check(offset + count <= bytes.size) { "Read past end of buffer" }
        val result = bytes.copyOfRange(offset, offset + count)
        offset += count
        return result
    }

    fun readRemaining(): UByteArray {
        val result = bytes.copyOfRange(offset, bytes.size)
        offset = bytes.size
        return result
    }

    fun readString(length: Int, nullTerminated: Boolean = true): String {
        val bytes = readBytes(length)
        return if (nullTerminated) {
            val nullIndex = bytes.indexOfFirst { it.toInt() == 0 }
            val endIndex = if (nullIndex == -1) bytes.size else nullIndex
            bytes.take(endIndex).map { it.toInt().toChar() }.joinToString("")
        } else {
            bytes.map { it.toInt().toChar() }.joinToString("")
        }
    }

    fun readString(): String = buildString {
        while (true) {
            val byte = readU8()
            if (byte == 0x0) break
            append(byte.toChar())
        }
    }

    fun skip(count: Int) {
        check(offset + count <= bytes.size) { "Skip past end of buffer" }
        offset += count
    }
}
