package pl.pelotasplus.eyeofbeholder.data.repository

import pl.pelotasplus.eyeofbeholder.data.model.Font
import pl.pelotasplus.eyeofbeholder.data.model.GlyphRow

/**
 * The game's bitmap fonts (`.FNT`):
 *
 * | Offset | Meaning |
 * | --- | --- |
 * | 0 | uint16: the file's size, less these two bytes |
 * | 2 | uint16 per glyph: where that glyph's rows start, from the file's start |
 * | 258 | glyph height in pixels |
 * | 259 | glyph width in pixels |
 *
 * Height and width sit at a fixed 258/259 because the offset table is always
 * read as [MAX_GLYPHS] entries however many glyphs the font really has.
 */
interface FontRepository {
    suspend fun loadFont(name: String): Result<Font>
}

class FontRepositoryImpl(
    private val resourceRepository: ResourceRepository,
) : FontRepository {

    override suspend fun loadFont(name: String): Result<Font> = runCatching {
        val bytes = resourceRepository.readResource("files/$name")

        fun u8(at: Int) = bytes[at].toInt()
        fun u16(at: Int) = u8(at) or (u8(at + 1) shl 8)

        require(u16(0) == bytes.size - 2) { "$name is not a .FNT file" }

        val height = u8(MAX_GLYPHS * 2 + 2)
        val width = u8(MAX_GLYPHS * 2 + 3)
        val glyphCount = u16(2) / 2 - 2
        val bytesPerRow = (width + 7) / 8

        val glyphs = List(glyphCount) { glyph ->
            val start = u16(2 + glyph * 2)
            Font.Glyph(
                List(height) { row ->
                    val at = start + row * bytesPerRow
                    GlyphRow(
                        (0 until bytesPerRow).fold(0) { bits, byte ->
                            bits or (u8(at + byte) shl (8 - byte * 8))
                        }
                    )
                }
            )
        }

        Font(width = width, height = height, glyphs = glyphs)
    }

    private companion object {
        const val MAX_GLYPHS = 128
    }
}
