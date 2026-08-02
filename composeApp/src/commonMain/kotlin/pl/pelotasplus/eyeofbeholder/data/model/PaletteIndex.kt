package pl.pelotasplus.eyeofbeholder.data.model

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

/**
 * An index into a 256-entry [Palette], as stored in every pixel of a .CPS
 * image, a .VCN tile or a rendered [ViewPort] source buffer.
 *
 * Distinct from the other quantities the codebase also keeps in plain Ints —
 * screen coordinates, scale steps, tile indices, and the 4-bit sub-palette
 * indices inside a raw [Vcn.Tile] — none of which may be handed to
 * [Palette.colorOrTransparent].
 *
 * Index 0 means "no pixel" everywhere except door graphics, which draw it as
 * an opaque color and read [Palette.colors] directly.
 */
@JvmInline
@Serializable
value class PaletteIndex(val value: Int) {
    val isTransparent: Boolean get() = value == TRANSPARENT.value

    companion object {
        val TRANSPARENT = PaletteIndex(0)
    }
}

/**
 * A 4-bit index (0-15) into a [Vcn]'s 16-entry sub-palette, as stored in every
 * pixel of a raw [Vcn.Tile].
 *
 * This is *not* a [PaletteIndex]: it must first be resolved through
 * [Vcn.backdropPalette] or [Vcn.wallPalette], and which of the two applies
 * depends on whether the tile is being drawn as floor/ceiling or as a wall.
 * The same tile therefore yields different colors in the two passes.
 */
@JvmInline
value class SubPaletteIndex(val value: Int) {
    val isTransparent: Boolean get() = value == TRANSPARENT.value

    companion object {
        val TRANSPARENT = SubPaletteIndex(0)
    }
}

/**
 * An index into a [Vcn]'s tile array, carried in the low 14 bits of a
 * [Vmp.TileIndex] entry. Distinct from the position of a tile in the viewport
 * grid, which is also an Int.
 */
@JvmInline
value class VcnTileIndex(val value: Int)
