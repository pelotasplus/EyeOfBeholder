package pl.pelotasplus.eyeofbeholder.data.model

import pl.pelotasplus.eyeofbeholder.data.ByteReader

data class Location(
    val h: Int,
    val l: Int
) {
    val value: Int
        get() = (l.toInt() shl 8) + h.toInt()
    val x: Int
        get() = value and 0x1F
    val y: Int
        get() = (value shr 5) and 0x1F

    companion object {
        fun read(reader: ByteReader): Location =
            Location(reader.readU8(), reader.readU8())
    }
}
