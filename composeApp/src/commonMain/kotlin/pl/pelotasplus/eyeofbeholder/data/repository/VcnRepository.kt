package pl.pelotasplus.eyeofbeholder.data.repository

import co.touchlab.kermit.Logger
import pl.pelotasplus.eyeofbeholder.data.ByteReader
import pl.pelotasplus.eyeofbeholder.data.model.Vcn

interface VcnRepository {
    suspend fun loadVcn(name: String): Result<Vcn>
}

class VcnRepositoryImpl(
    private val resourceRepository: ResourceRepository,
    private val palRepository: PalRepository
) : VcnRepository {
    override suspend fun loadVcn(name: String): Result<Vcn> {
        return runCatching {
            val bytes = resourceRepository.decompressResource("files/$name")
            val reader = ByteReader(bytes)
            decodeVcn(name, reader)
        }
    }

    private fun decodeVcn(name: String, vcnReader: ByteReader): Vcn {
        val tilesCount = vcnReader.readU16LE()

        val backdropPaletteColor = MutableList(16) { 0 }
        val wallPaletteColor = MutableList(16) { 0 }

        // floor / ceiling
        repeat(16) {
            backdropPaletteColor[it] = vcnReader.readU8()
        }

        // walls
        repeat(16) {
            wallPaletteColor[it] = vcnReader.readU8()
        }

        Logger.d(TAG) { "Vcn $name has $tilesCount tiles" }

        check(tilesCount * 32 == vcnReader.remaining) {
            "Expected ${tilesCount * 32} bytes of tile data, got ${vcnReader.remaining}"
        }

        val tiles = mutableListOf<Vcn.Tile>()

        // The very first tile is fully transparent, 7 tilesets follow.
        // The first are the tiles for the backdrop (ceiling/floor), then 6 different wall types (including doorways and stairs) follow.

        repeat(tilesCount) {
            val tilePixels = mutableListOf<Int>()

            // Each tile is 32 bytes.  Each byte represents two pixels.
            // So 4 bytes are needed to represent 8 pixels, ie. one row.
            repeat(32) {
                val rawByte = vcnReader.readU8()
                val (pixel1, pixel2) = decodePixelByte(rawByte)
                check(pixel1 in 0..15) {
                    "Expected pixel1 to ne between 0 and 15"
                }
                check(pixel2 in 0..15) {
                    "Expected pixel2 to ne between 0 and 15"
                }
                tilePixels.add(pixel1)
                tilePixels.add(pixel2)
            }

            check(tilePixels.size == 8 * 8) {
                "Expected tile pixels to be 8x8, got ${tilePixels.size}"
            }

            tiles.add(Vcn.Tile(pixels = tilePixels))
        }

        return Vcn(
            name = name,
            tilesCount = tilesCount,
            backdropPalette = backdropPaletteColor,
            wallPalette = wallPaletteColor,
            tiles = tiles
        )
    }

    private fun decodePixelByte(
        rawByte: Int,
    ): Pair<Int, Int> {
        val highNibble = (rawByte shr 4) and 0x0F
        val lowNibble = rawByte and 0x0F

        return highNibble to lowNibble
    }

    companion object {
        private const val TAG = "VcnRepository"
    }
}


