package pl.pelotasplus.eyeofbeholder.data.repository

import co.touchlab.kermit.Logger
import pl.pelotasplus.eyeofbeholder.data.LCWHelper
import pl.pelotasplus.eyeofbeholder.data.model.Cps
import pl.pelotasplus.eyeofbeholder.data.readU16LE
import pl.pelotasplus.eyeofbeholder.data.readU32LE

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
            var offset = 0

            Logger.d(TAG) { "Decompressing $name; On-disk file size ${bytes.size}" }

            val sizeFromHeader = bytes.readU16LE(offset)
            offset += 2
            Logger.d(TAG) { "Header file size $sizeFromHeader" }

            val compressionType = bytes.readU16LE(offset)
            offset += 2
            Logger.d(TAG) { "Compression Type $compressionType" }

            val uncompressedSize = bytes.readU32LE(offset)
            check(uncompressedSize == IMAGE_WIDTH * IMAGE_HEIGHT) {
                "Unexpected uncompressed size: $uncompressedSize, expected ${IMAGE_WIDTH * IMAGE_HEIGHT}"
            }

            offset += 4
            Logger.d(TAG) { "Uncompressed size $uncompressedSize" }

            val paletteSize = bytes.readU16LE(offset)
            offset += 2
            Logger.d(TAG) { "Palette Size $paletteSize" }

            // sizeFromHeader is file size minus 2, compressed data is from current offset to end
            val compressed = bytes.copyOfRange(offset, bytes.size)
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
