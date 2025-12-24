package pl.pelotasplus.eyeofbeholder.data.model

@OptIn(ExperimentalUnsignedTypes::class)
data class Cps(
    val name: String,
    val width: Int,
    val height: Int,
    val pixels: UByteArray, // each pixel index to an entry from the color palette (PAL file)
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as Cps

        if (name != other.name) return false
        if (width != other.width) return false
        if (height != other.height) return false
        if (!pixels.contentEquals(other.pixels)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + width
        result = 31 * result + height
        result = 31 * result + pixels.contentHashCode()
        return result
    }
}
