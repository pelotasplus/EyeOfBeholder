package pl.pelotasplus.eyeofbeholder.agent

import pl.pelotasplus.eyeofbeholder.data.model.RGB

/**
 * A picture as a PNG file, written here rather than by a platform so that
 * every platform hands an agent the same bytes.
 *
 * Uncompressed: the pixels go into the zlib stream as stored blocks. A
 * 320×200 frame comes to under 200 KB that way, which is nothing on a local
 * connection, and it keeps this to the few dozen lines of the format itself.
 */
object Png {

    fun of(rows: List<List<RGB>>): ByteArray {
        val height = rows.size
        val width = rows.firstOrNull()?.size ?: 0

        val raw = ByteArray(height * (1 + width * 3))
        var at = 0
        for (row in rows) {
            raw[at++] = 0 // no filter
            for (pixel in row) {
                raw[at++] = pixel.red.toByte()
                raw[at++] = pixel.green.toByte()
                raw[at++] = pixel.blue.toByte()
            }
        }

        val out = Bytes()
        out.add(SIGNATURE)
        out.chunk("IHDR", Bytes().apply {
            int(width)
            int(height)
            add(byteArrayOf(8, 2, 0, 0, 0)) // 8 bits, RGB, deflate, no filter, no interlace
        }.toByteArray())
        out.chunk("IDAT", stored(raw))
        out.chunk("IEND", ByteArray(0))
        return out.toByteArray()
    }

    /** [data] as a zlib stream of stored blocks. */
    private fun stored(data: ByteArray): ByteArray {
        val out = Bytes()
        out.add(byteArrayOf(0x78, 0x01))
        var from = 0
        do {
            val length = minOf(MAX_STORED, data.size - from)
            val last = from + length >= data.size
            out.add(byteArrayOf(if (last) 1 else 0))
            out.add(byteArrayOf((length and 0xFF).toByte(), (length shr 8).toByte()))
            val inverse = length.inv() and 0xFFFF
            out.add(byteArrayOf((inverse and 0xFF).toByte(), (inverse shr 8).toByte()))
            out.add(data.copyOfRange(from, from + length))
            from += length
        } while (from < data.size)
        out.int(adler32(data))
        return out.toByteArray()
    }

    private fun Bytes.chunk(type: String, data: ByteArray) {
        val typed = type.encodeToByteArray() + data
        int(data.size)
        add(typed)
        int(crc32(typed))
    }

    private fun crc32(bytes: ByteArray): Int {
        var crc = -1
        for (b in bytes) {
            crc = crc xor (b.toInt() and 0xFF)
            repeat(8) { crc = if (crc and 1 != 0) (crc ushr 1) xor CRC_POLYNOMIAL else crc ushr 1 }
        }
        return crc.inv()
    }

    private fun adler32(bytes: ByteArray): Int {
        var a = 1
        var b = 0
        for (byte in bytes) {
            a = (a + (byte.toInt() and 0xFF)) % ADLER_MODULUS
            b = (b + a) % ADLER_MODULUS
        }
        return (b shl 16) or a
    }

    private class Bytes {
        private val parts = mutableListOf<ByteArray>()
        fun add(bytes: ByteArray) {
            parts += bytes
        }

        fun int(value: Int) = add(byteArrayOf((value ushr 24).toByte(), (value ushr 16).toByte(), (value ushr 8).toByte(), value.toByte()))

        fun toByteArray(): ByteArray {
            val all = ByteArray(parts.sumOf { it.size })
            var at = 0
            for (part in parts) {
                part.copyInto(all, at)
                at += part.size
            }
            return all
        }
    }

    private val SIGNATURE = byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10)
    private const val MAX_STORED = 0xFFFF
    private const val CRC_POLYNOMIAL = -0x12477ce0 // 0xEDB88320
    private const val ADLER_MODULUS = 65521
}
