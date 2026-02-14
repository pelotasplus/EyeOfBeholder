package pl.pelotasplus.eyeofbeholder.data.model

data class Cps(
    val name: String,
    val width: Int,
    val height: Int,
    val pixels: List<Int>, // each pixel index to an entry from the color palette (PAL file)
) {
    override fun toString(): String {
        return "Cps(name='$name', width=$width, height=$height, pixels=${pixels.size})"
    }
}
