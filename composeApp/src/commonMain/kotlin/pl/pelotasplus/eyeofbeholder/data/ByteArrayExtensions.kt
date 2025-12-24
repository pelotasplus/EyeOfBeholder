package pl.pelotasplus.eyeofbeholder.data

fun UByteArray.readU16LE(offset: Int): Int {
    return (this[offset].toInt() and 0xFF) +
            ((this[offset + 1].toInt() and 0xFF) shl 8)
}

fun UByteArray.readU32LE(offset: Int): Int {
    return (this[offset].toInt() and 0xFF) +
            ((this[offset + 1].toInt() and 0xFF) shl 8) +
            ((this[offset + 2].toInt() and 0xFF) shl 16) +
            ((this[offset + 3].toInt() and 0xFF) shl 24)
}
