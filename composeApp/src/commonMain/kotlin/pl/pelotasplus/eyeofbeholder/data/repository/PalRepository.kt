package pl.pelotasplus.eyeofbeholder.data.repository

import kotlinx.collections.immutable.toImmutableList
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

            val colors = bytes
                .take(PALETTE_SIZE * BYTES_PER_COLOR)
                .chunked(BYTES_PER_COLOR)
                .map { (r, g, b) ->
                    RGB(
                        red = convert6bitTo8bit(r),
                        green = convert6bitTo8bit(g),
                        blue = convert6bitTo8bit(b),
                    )
                }
            Palette(name = name, colors = colors.toImmutableList())
        }
    }

    override suspend fun getAllPalNames(): Result<List<String>> {
        return resourceRepository.listResources(".PAL")
    }

    private fun convert6bitTo8bit(byte: UByte): UByte {
        return ((byte.toInt() * 255) / 63).toUByte()
    }

    companion object {
        private const val PALETTE_SIZE = 256
        private const val BYTES_PER_COLOR = 3
    }
}
