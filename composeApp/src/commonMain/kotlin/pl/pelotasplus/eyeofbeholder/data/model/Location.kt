package pl.pelotasplus.eyeofbeholder.data.model

import pl.pelotasplus.eyeofbeholder.data.ByteReader

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
