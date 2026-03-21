package pl.pelotasplus.eyeofbeholder.data.model

/**
 * Parsed .VMP file — viewport mapping that defines how VCN tiles are arranged
 * to create the 3D dungeon view.
 *
 * The game's viewport is 176×120 pixels (22×15 tiles of 8×8 each).
 * The VMP file specifies which VCN tile goes at each position for:
 *
 * ## Data layout
 * 1. **Backdrop** (first 330 entries = 22×15 tiles): the floor/ceiling background
 *    drawn behind everything else, always rendered first.
 * 2. **Wall types** (remaining entries, in groups of 431): each group defines
 *    tile assignments for one wall type. The VCN has multiple wall type sets
 *    (solid wall, doorframe, stairs-up, stairs-down, etc.) and the VMP tells
 *    the renderer which tiles to place at each of the 25 wall positions.
 *
 * ## TileIndex encoding (16-bit)
 * - Bit 15: zMask — depth/transparency flag (used to clip behind closer walls)
 * - Bit 14: mirrorX — flip the tile horizontally when drawing
 * - Bits 0-13: tileIndex — index into the VCN tile array (14-bit, max 16383)
 *
 * ## Constants
 * - BACKDROP_SIZE = 330 (22 columns × 15 rows)
 * - WALL_TYPE_SIZE = 431 (tiles per wall type, covering all 25 wall positions)
 *
 * @property name Original filename (e.g. "DUNG.VMP")
 * @property tileIndexes All tile indices: backdrop + N wall type groups
 */
data class Vmp(
    val name: String,
    val tileIndexes: List<TileIndex>,
) {
    data class TileIndex(
        val zMask: Boolean,
        val mirrorX: Boolean,
        val tileIndex: Int,
    )

    val backdrop: List<TileIndex>
        get() = tileIndexes.subList(0, BACKDROP_SIZE)

    fun getWallType(index: Int): List<TileIndex> {
        return tileIndexes.subList(
            BACKDROP_SIZE + (index * WALL_TYPE_SIZE),
            BACKDROP_SIZE + (index + 1) * WALL_TYPE_SIZE
        )
    }

    override fun toString(): String {
        return "Vmp(name='$name', tileIndexes=${tileIndexes.size})"
    }

    companion object {
        const val BACKDROP_SIZE = 330
        const val WALL_TYPE_SIZE = 431
    }
}
