package pl.pelotasplus.eyeofbeholder.data.model

/**
 * Parsed .VCN file — the tile set used to render the 3D dungeon viewport.
 *
 * VCN files are LCW-compressed and contain a collection of 8x8 pixel tiles
 * plus two 16-color sub-palettes. Each pixel in a tile is a 4-bit index (0-15)
 * into one of the two sub-palettes; the sub-palette value then indexes into
 * the full 256-color .PAL file.
 *
 * ## Tile organization
 * - Tile 0: fully transparent (used as "no tile")
 * - Tiles 1+: grouped into 7 sets:
 *   1. Backdrop tiles (floor/ceiling, rendered with [backdropPalette])
 *   2-7. Wall type tiles (solid walls, doorframes, stairs — rendered with [wallPalette])
 *
 * ## Two-level palette indirection
 * Pixel value (0-15) → sub-palette[pixel] → full palette index (0-255) → RGB color.
 * This allows the same tile data to appear with different color schemes by swapping
 * palettes, and is why backdrop and wall tiles use separate sub-palettes.
 *
 * ## Binary format (.VCN, after decompression)
 * - tileCount (u16)
 * - backdropPalette: 16 bytes (palette indices for floor/ceiling tiles)
 * - wallPalette: 16 bytes (palette indices for wall tiles)
 * - tile data: tileCount × 32 bytes (each byte = 2 pixels, high nibble first)
 *
 * @property name Original filename (e.g. "DUNG.VCN")
 * @property tilesCount Total number of tiles in this set
 * @property tiles The actual tile pixel data (each tile is 64 pixels = 8×8)
 * @property backdropPalette 16-entry palette remap table for floor/ceiling rendering
 * @property wallPalette 16-entry palette remap table for wall rendering
 */
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
            pixels = tiles[tileIndex].pixels.map { pixel ->
                if (pixel == 0) {
                    0
                } else {
                    backdropPalette[pixel]
                }
            }
        )
    }

    fun getTileAsWall(tileIndex: Int): Tile {
        val tile = tiles[tileIndex]
        return Tile(
            pixels = tile.pixels.map { pixel ->
                if (pixel == 0) {
                    0
                } else {
                    wallPalette[pixel]
                }
            }
        )
    }
}
