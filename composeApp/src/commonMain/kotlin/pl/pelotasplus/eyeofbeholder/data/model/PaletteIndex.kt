package pl.pelotasplus.eyeofbeholder.data.model

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
value class PaletteIndex(val value: Int) {
    val isTransparent: Boolean get() = value == TRANSPARENT.value

    companion object {
        val TRANSPARENT = PaletteIndex(0)
    }
}
