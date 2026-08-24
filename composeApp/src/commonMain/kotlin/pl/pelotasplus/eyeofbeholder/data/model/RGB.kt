package pl.pelotasplus.eyeofbeholder.data.model

/**
 * A single color value with optional transparency.
 *
 * Color channels are 8-bit (0-255). .PAL files store colors as 6-bit VGA
 * values (0-63); conversion to 8-bit happens during
 * palette loading via the formula: `(sixBit * 255) / 63`.
 *
 * @property transparent When true, this pixel is not drawn (used for color index 0
 *           in palettes, and for see-through parts of wall tiles and decorations)
 */
data class RGB(
    val red: Int,
    val green: Int,
    val blue: Int,
    val transparent: Boolean = false
)
