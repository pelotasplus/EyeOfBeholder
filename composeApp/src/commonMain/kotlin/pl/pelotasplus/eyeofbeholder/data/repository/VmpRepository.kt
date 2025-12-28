package pl.pelotasplus.eyeofbeholder.data.repository

import co.touchlab.kermit.Logger
import pl.pelotasplus.eyeofbeholder.data.ByteReader
import pl.pelotasplus.eyeofbeholder.data.model.TileIndex
import pl.pelotasplus.eyeofbeholder.data.model.Vmp

interface VmpRepository {
    suspend fun loadVmp(name: String): Result<Vmp>
}

class VmpRepositoryImpl(
    private val resourceRepository: ResourceRepository,
) : VmpRepository {
    override suspend fun loadVmp(name: String): Result<Vmp> {
        return runCatching {
            val bytes = resourceRepository.readResource("files/$name")
            val reader = ByteReader(bytes)
            Logger.d(TAG) { "VMP size ${reader.remaining}" }

            val indexCount = reader.readU16LE()
            Logger.d(TAG) { "Index count $indexCount" }

            val tileIndexes = List(indexCount) {
                decodeTileIndex(reader.readU16LE())
            }

            check(reader.remaining == 0) {
                "Expected 0 bytes remaining, got ${reader.remaining}"
            }

            val tilesForWalls = tileIndexes.size - 330
            check(tilesForWalls % 431 == 0) {
                "Expected tilesForWalls to be divisible by 431, got $tilesForWalls"
            }

            Vmp(
                name = name,
                tileIndexes = tileIndexes
            )
        }
    }

    /**
     * Decodes a 16-bit value into a TileIndex structure.
     *
     * Bit layout (MSB first):
     * - Bit 15: z-mask
     * - Bit 14: mirror_x
     * - Bits 0-13: tile_index (14-bit unsigned)
     */
    private fun decodeTileIndex(value: Int): TileIndex {
        val zMask = (value and 0x8000) != 0
        val mirrorX = (value and 0x4000) != 0
        val tileIndex = value and 0x3FFF

        return TileIndex(
            zMask = zMask,
            mirrorX = mirrorX,
            tileIndex = tileIndex
        )
    }

    companion object {
        private const val TAG = "VmpRepository"
    }
}
