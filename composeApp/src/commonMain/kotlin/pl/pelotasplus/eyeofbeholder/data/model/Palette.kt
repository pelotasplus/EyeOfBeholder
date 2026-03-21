package pl.pelotasplus.eyeofbeholder.data.model

import kotlinx.collections.immutable.ImmutableList

/**
 * A 256-color VGA palette parsed from a .PAL file.
 *
 * Each sublevel has its own palette that determines the color scheme of its
 * walls, floors, decorations, and items. Color index 0 is always transparent.
 *
 * ## Binary format (.PAL)
 * 768 bytes: 256 colors × 3 bytes (R, G, B) in 6-bit VGA format (0-63).
 *
 * @property name Original filename (e.g. "DUNG.PAL")
 * @property colors 256 RGB entries; index 0 is transparent
 */
data class Palette(
    val name: String,
    val colors: ImmutableList<RGB>,
)
