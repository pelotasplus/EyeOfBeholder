package pl.pelotasplus.eyeofbeholder.data.repository

import pl.pelotasplus.eyeofbeholder.data.ByteReader
import pl.pelotasplus.eyeofbeholder.data.model.Cps

/**
 * Parses .CPS files — LCW-compressed 320×200 images.
 *
 * CPS is the standard image format used throughout Eye of the Beholder for
 * door graphics, decoration overlays, item sprite sheets, portraits,
 * cutscenes, and UI elements.
 *
 * After decompression, the pixel data is a flat array of 64000 palette
 * indices (320 × 200), stored row-major. Each index references the current
 * sublevel's .PAL palette; index 0 = transparent.
 */
interface CpsRepository {
    suspend fun loadCps(name: String): Result<Cps>

    suspend fun getAllCpsNames(): Result<List<String>>
}

class CpsRepositoryImpl(
    private val resourceRepository: ResourceRepository
) : CpsRepository {

    override suspend fun loadCps(name: String): Result<Cps> {
        return runCatching {
            val bytes = resourceRepository.decompressResource("files/$name")
            val reader = ByteReader(bytes)

            Cps(
                name = name,
                width = IMAGE_WIDTH,
                height = IMAGE_HEIGHT,
                pixels = reader.readRemaining().map { it.toInt() }.toList()
            )
        }
    }

    override suspend fun getAllCpsNames(): Result<List<String>> {
        return resourceRepository.listResources(".CPS")
    }

    companion object {
        private const val IMAGE_WIDTH = 320
        private const val IMAGE_HEIGHT = 200
    }
}
