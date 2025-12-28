package pl.pelotasplus.eyeofbeholder.data.model

data class Vcn(
    val name: String,
    val tilesCount: Int,
    val tiles: List<Tile>, // list of 8x8 tiles of pixels
    val backdropPalette: List<Int>, // list of 16 color values pointing to colors in relevant .PAL
    val wallPalette: List<Int>, // list of 16 color values pointing to colors in relevant .PAL
) {
    data class Tile(
        val pixels: List<Int> // each pixel points to a color in the palette in the Vcn class
    )

    fun getTileAsBackdrop(tileIndex: Int): Tile {
        return Tile(
            pixels = tiles[tileIndex].pixels.map { pixel -> backdropPalette[pixel] }
        )
    }

    fun getTileAsWall(tileIndex: Int): Tile {
        return Tile(
            pixels = tiles[tileIndex].pixels.map { pixel -> wallPalette[pixel] }
        )
    }
}
