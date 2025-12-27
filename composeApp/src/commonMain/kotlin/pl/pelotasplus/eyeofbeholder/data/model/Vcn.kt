package pl.pelotasplus.eyeofbeholder.data.model

data class Vcn(
    val name: String,
    val tilesCount: Int,
    val tiles: List<List<Int>>,
    val backdropPalette: MutableList<Int>,
    val wallPalette: MutableList<Int>, // list of 8x8 tiles, each value is index into intermediary palettes
)
