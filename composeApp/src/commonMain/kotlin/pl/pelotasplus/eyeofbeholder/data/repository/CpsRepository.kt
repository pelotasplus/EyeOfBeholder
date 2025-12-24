package pl.pelotasplus.eyeofbeholder.data.repository

import co.touchlab.kermit.Logger
import pl.pelotasplus.eyeofbeholder.data.ByteReader
import pl.pelotasplus.eyeofbeholder.data.LCWHelper
import pl.pelotasplus.eyeofbeholder.data.model.Cps

interface CpsRepository {
    suspend fun loadCps(name: String): Result<Cps>

    suspend fun getAllCpsNames(): Result<List<String>>
}

class CpsRepositoryImpl(
    private val resourceRepository: ResourceRepository
) : CpsRepository {

    private val TAG = "CpsRepository"

    override suspend fun loadCps(name: String): Result<Cps> {
        return runCatching {
            val bytes = resourceRepository.readResource("files/$name")
            val reader = ByteReader(bytes)

            Logger.d(TAG) { "Decompressing $name; On-disk file size ${bytes.size}" }

            val sizeFromHeader = reader.readU16LE()
            Logger.d(TAG) { "Header file size $sizeFromHeader" }

            val compressionType = reader.readU16LE()
            Logger.d(TAG) { "Compression Type $compressionType" }

            val uncompressedSize = reader.readU32LE()
            check(uncompressedSize == IMAGE_WIDTH * IMAGE_HEIGHT) {
                "Unexpected uncompressed size: $uncompressedSize, expected ${IMAGE_WIDTH * IMAGE_HEIGHT}"
            }
            Logger.d(TAG) { "Uncompressed size $uncompressedSize" }

            val paletteSize = reader.readU16LE()
            check(paletteSize == 0) {
                "Unexpected palette size: $paletteSize, expected 0"
            }
            Logger.d(TAG) { "Palette Size $paletteSize" }

            val compressed = reader.readRemaining()
            val decompressed = UByteArray(uncompressedSize)

            LCWHelper.decompress(compressed, decompressed)

            Cps(
                name = name,
                width = IMAGE_WIDTH,
                height = IMAGE_HEIGHT,
                pixels = decompressed
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
