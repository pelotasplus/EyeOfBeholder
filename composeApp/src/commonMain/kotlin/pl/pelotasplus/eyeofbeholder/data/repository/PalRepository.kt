package pl.pelotasplus.eyeofbeholder.data.repository

import kotlinx.collections.immutable.toImmutableList
import pl.pelotasplus.eyeofbeholder.data.ByteReader
import pl.pelotasplus.eyeofbeholder.data.model.Palette
import pl.pelotasplus.eyeofbeholder.data.model.RGB

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
