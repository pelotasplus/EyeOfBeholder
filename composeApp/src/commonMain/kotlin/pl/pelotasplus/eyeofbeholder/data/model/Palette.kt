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
) {
    /**
     * Color at [index], honoring the universal convention that index 0 means
     * "no pixel": it maps to a transparent color that the renderer skips.
     * Door graphics are the one exception — they draw index 0 as an opaque
     * color and read [colors] directly.
     */
    fun colorOrTransparent(index: Int): RGB =
        if (index == 0) TRANSPARENT else colors[index]

    /**
     * Palette-index remap for distance darkening ("depth cueing"): every
     * color is shifted about a third of the way toward the fade root color
     * and snapped to the nearest palette entry — never itself, so repeated
     * application keeps darkening. Sprites drawn N scale steps away are
     * remapped N times via [fadedIndex]. Index 0 stays 0 (transparent).
     *
     * The arithmetic runs on the original 6-bit VGA channel values (recovered
     * from the stored 8-bit colors) so it matches the DOS engine exactly.
     * (ScummVM Screen_EoB::createColorFadeTable with rootColor 12, weight 85.)
     */
    val distanceFadeTable: List<Int> by lazy {
        val r = IntArray(256)
        val g = IntArray(256)
        val b = IntArray(256)
        for (i in 0 until 256) {
            r[i] = to6bit(colors[i].red)
            g[i] = to6bit(colors[i].green)
            b[i] = to6bit(colors[i].blue)
        }

        val weight = FADE_WEIGHT shr 1
        val table = IntArray(256)
        table[0] = 0

        for (i in 1 until 256) {
            val tr = fadeChannel(r[i], r[FADE_ROOT_COLOR], weight)
            val tg = fadeChannel(g[i], g[FADE_ROOT_COLOR], weight)
            val tb = fadeChannel(b[i], b[FADE_ROOT_COLOR], weight)

            var best = FADE_ROOT_COLOR
            var bestDistance = Int.MAX_VALUE
            for (candidate in 1 until 256) {
                val dr = r[candidate] - tr
                val dg = g[candidate] - tg
                val db = b[candidate] - tb
                val distance = dr * dr + dg * dg + db * db
                // <= keeps the original's later-index-wins tie-breaking
                if (distance <= bestDistance && (candidate == FADE_ROOT_COLOR || candidate != i)) {
                    bestDistance = distance
                    best = candidate
                }
            }
            table[i] = best
        }
        table.toList()
    }

    /** [index] remapped [steps] times through [distanceFadeTable]. */
    fun fadedIndex(index: Int, steps: Int): Int {
        var idx = index
        repeat(steps) { idx = distanceFadeTable[idx] }
        return idx
    }

    private fun to6bit(v8: Int): Int = (v8 * 63 + 127) / 255

    /** Shift channel [c] toward [root]; mirrors the original's uint8 wrap-around. */
    private fun fadeChannel(c: Int, root: Int, weight: Int): Int =
        (c - (((c - root) * weight) shl 1 shr 8)) and 0xFF

    companion object {
        private val TRANSPARENT = RGB(0, 0, 0, transparent = true)

        /** Palette entry every color fades toward (a dark grey in EoB2 palettes). */
        private const val FADE_ROOT_COLOR = 12

        /** Fade strength per application; halved before use like the original. */
        private const val FADE_WEIGHT = 85
    }
}
