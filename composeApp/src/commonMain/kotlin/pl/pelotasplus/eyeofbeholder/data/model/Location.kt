package pl.pelotasplus.eyeofbeholder.data.model

import pl.pelotasplus.eyeofbeholder.data.ByteReader

data class Location(
//    val h: Int,
//    val l: Int
    val x: Int,
    val y: Int
) {
//    val value: Int
//        get() = (l shl 8) + h
//    val x: Int
//        get() = value and 0x1F
//    val y: Int
//        get() = (value shr 5) and 0x1F

    companion object {
        fun read(reader: ByteReader): Location {
            val pos = reader.readU16LE()
            val x = pos and 31
            val y = pos / 32
//            val h = reader.readU8()
//            val l = reader.readU8()
//            val value = (l shl 8) + h
//            val x = value and 0x1F
//            val y = (value shr 5) and 0x1F
            return Location(x, y)
        }
    }
}
