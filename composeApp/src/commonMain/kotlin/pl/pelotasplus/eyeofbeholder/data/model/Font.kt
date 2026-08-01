package pl.pelotasplus.eyeofbeholder.data.model

import kotlin.jvm.JvmInline

/** One row of a glyph, a pixel per bit, the leftmost in the highest. */
@JvmInline
value class GlyphRow(private val bits: Int) {
    fun isInk(x: Int): Boolean = bits and (0x8000 ushr x) != 0
}

class Font(
    val width: Int,
    val height: Int,
    private val glyphs: List<Glyph>,
) {
    fun glyphFor(character: Char): Glyph? = glyphs.getOrNull(character.code)

    fun widthOf(text: String): Int = text.length * width

    /**
     * Spacing inside [text] is kept as written — the game indents a speech's
     * first line and doubles the space between sentences — so words carry the
     * blanks that precede them, and only a line break drops them.
     */
    fun wrap(text: String, maxWidth: Int): List<String> {
        val charactersPerLine = (maxWidth / width).coerceAtLeast(1)

        return text.split('\n').flatMap { paragraph ->
            val lines = mutableListOf<String>()
            var line = ""

            Regex(" *[^ ]+").findAll(paragraph).forEach { match ->
                val word = match.value
                if (line.isNotEmpty() && line.length + word.length > charactersPerLine) {
                    lines += line
                    line = word.trimStart()
                } else {
                    line += word
                }
            }

            lines += line
            lines
        }
    }

    class Glyph(private val rows: List<GlyphRow>) {
        fun isInk(x: Int, y: Int): Boolean = rows.getOrNull(y)?.isInk(x) ?: false
    }
}
