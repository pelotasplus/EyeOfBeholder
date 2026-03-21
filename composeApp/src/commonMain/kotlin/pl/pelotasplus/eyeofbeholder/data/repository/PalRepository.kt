package pl.pelotasplus.eyeofbeholder.data.repository

import kotlinx.collections.immutable.toImmutableList
import pl.pelotasplus.eyeofbeholder.data.ByteReader
import pl.pelotasplus.eyeofbeholder.data.model.Palette
import pl.pelotasplus.eyeofbeholder.data.model.RGB

/**
 * Parses .PAL files — 256-color VGA palettes.
 *
 * ## Binary format (.PAL)
 * Exactly 768 bytes: 256 colors × 3 bytes (R, G, B).
 * Each channel is stored as a 6-bit VGA value (0-63), converted to 8-bit
 * (0-255) via: `(value * 255) / 63`.
 *
 * Color index 0 is always marked as transparent (used for see-through pixels
 * in tiles, decorations, and item icons).
 *
 * Different dungeon areas use different palettes to create distinct visual
 * themes (e.g. grey stone, brown earth, blue-green mezzanine).
 */
interface PalRepository {
    suspend fun loadPal(name: String): Result<Palette>

    suspend fun getAllPalNames(): Result<List<String>>
}

class PalRepositoryImpl(
    private val resourceRepository: ResourceRepository
) : PalRepository {
    override suspend fun loadPal(name: String): Result<Palette> {
        return runCatching {
            val bytes = resourceRepository.readResource("files/$name")
            val reader = ByteReader(bytes)

            val colors = List(PALETTE_SIZE) {
                val r = convert6bitTo8bit(reader.readU8())
                val g = convert6bitTo8bit(reader.readU8())
                val b = convert6bitTo8bit(reader.readU8())
                RGB(r, g, b, it == 0)
            }

            check(reader.remaining == 0) {
                "Unexpected bytes after palette data in $name"
            }
            check(colors[0].transparent) {
                "First color must be transparent in $name"
            }

            Palette(name = name, colors = colors.toImmutableList())
        }
    }

    override suspend fun getAllPalNames(): Result<List<String>> {
        return resourceRepository.listResources(".PAL")
    }

    private fun convert6bitTo8bit(byte: Int): Int {
        return ((byte * 255) / 63)
    }

    companion object {
        private const val PALETTE_SIZE = 256
    }
}
