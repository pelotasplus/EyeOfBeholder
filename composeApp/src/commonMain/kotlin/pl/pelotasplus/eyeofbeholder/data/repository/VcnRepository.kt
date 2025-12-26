package pl.pelotasplus.eyeofbeholder.data.repository

import androidx.lifecycle.viewmodel.compose.viewModel
import co.touchlab.kermit.Logger
import pl.pelotasplus.eyeofbeholder.data.ByteReader
import pl.pelotasplus.eyeofbeholder.data.LCWHelper
import pl.pelotasplus.eyeofbeholder.data.model.Vcn

interface VcnRepository {
    suspend fun loadVcn(name: String): Result<Vcn>
}

class VcnRepositoryImpl(
    private val resourceRepository: ResourceRepository
) : VcnRepository {
    override suspend fun loadVcn(name: String): Result<Vcn> {
        return runCatching {
            val bytes = resourceRepository.readResource("files/$name")
            val reader = ByteReader(bytes)

            Logger.d(TAG) { "Decompressing $name; On-disk file size ${bytes.size}" }

            val sizeFromHeader = reader.readU16LE()
            Logger.d(TAG) { "Header file size $sizeFromHeader" }

            val compressionType = reader.readU16LE()
            Logger.d(TAG) { "Compression Type $compressionType" }

            val uncompressedSize = reader.readU32LE()
            Logger.d(TAG) { "Uncompressed size $uncompressedSize" }

            val paletteSize = reader.readU16LE()
            check(paletteSize == 0) {
                "Unexpected palette size: $paletteSize, expected 0"
            }
            Logger.d(TAG) { "Palette Size $paletteSize" }

            val compressed = reader.readRemaining()
            val decompressed = UByteArray(uncompressedSize)

            LCWHelper.decompress(compressed, decompressed)

            val vcnReader = ByteReader(decompressed)
            val tilesCount = vcnReader.readU16LE()

            repeat(16) {
                val backdropPaletteColor = vcnReader.readU8()
            }

            repeat(16) {
                val wallPaletteColor = vcnReader.readU8()
            }

            Logger.d(TAG) { "Vcn $name has $tilesCount tiles"}
            Logger.d(TAG) { "Remaining size ${vcnReader.remaining}" }

            check(tilesCount * 32 == vcnReader.remaining) {
                "Expected ${tilesCount * 32} bytes of tile data, got ${vcnReader.remaining}"
            }

            Vcn(
                name = name,
                tilesCount = tilesCount
            )
        }
    }

    companion object {
        private const val TAG = "VcnRepository"
    }
}


