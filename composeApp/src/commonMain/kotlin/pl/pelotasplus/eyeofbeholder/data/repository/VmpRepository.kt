package pl.pelotasplus.eyeofbeholder.data.repository

import co.touchlab.kermit.Logger
import pl.pelotasplus.eyeofbeholder.data.ByteReader
import pl.pelotasplus.eyeofbeholder.data.model.RGB
import pl.pelotasplus.eyeofbeholder.data.model.ViewPort
import pl.pelotasplus.eyeofbeholder.data.model.Vmp

interface VmpRepository {
    suspend fun loadVmp(name: String): Result<ViewPort>
}

class VmpRepositoryImpl(
    private val resourceRepository: ResourceRepository,
    private val vcnRepository: VcnRepository,
    private val palRepository: PalRepository
) : VmpRepository {
    override suspend fun loadVmp(name: String): Result<ViewPort> {
        return runCatching {
            val bytes = resourceRepository.readResource("files/$name")
            val vmp = readVmp(name, ByteReader(bytes))
            val vcn = vcnRepository.loadVcn(name.replace(".VMP", ".VCN")).getOrThrow()
            val pal = palRepository.loadPal(name.replace(".VMP", ".PAL")).getOrThrow()

            val vmpTilesAsBackdrop = vmp.backdrop
            val vcnTilesAsBackdrop = vmpTilesAsBackdrop.map { tileIndex ->
                vcn.getTileAsBackdrop(tileIndex.tileIndex)
            }
            val rgbTiles = vcnTilesAsBackdrop.map {
                it.pixels.map { pixel ->
                    if (pixel == 0) {
                        RGB(0, 0, 0, transparent = true)
                    } else {
                        pal.colors[pixel]
                    }
                }
            }
            check(rgbTiles.size == 330) {
                "Expected 330 tiles, got ${rgbTiles.size}"
            }

            val viewPort = ViewPort()

            rgbTiles.forEachIndexed { index, tile ->
                val row = index / 22
                val col = index % 22

                tile.forEachIndexed { pixelIndex, rgb ->
                    val tileRow = pixelIndex / 8
                    val tileCol = pixelIndex % 8
                    val x = col * 8 + tileCol
                    val y = row * 8 + tileRow
                    viewPort.draw(x, y, rgb)
                }
            }

//            val vmpTilesAsWall = vmp.getWallType(0)
//            val vcnTilesAsWall = vmpTilesAsWall.map { tileIndex ->
//                vcn.getTileAsWall(tileIndex.tileIndex)
//            }
//            val rgbTiles = vcnTilesAsWall.map {
//                it.pixels.map { pixel -> pal.colors[pixel] }
//            }
//            check(rgbTiles.size == 431) {
//                "Expected 330 tiles, got ${rgbTiles.size}"
//            }

            /*
             * 0 -> full wall
             * 1 -> full wall
             * 2 -> door front
             * 3 -> stairs up
             * 4 -> stairs down
             * 5 -> portal/door?
             */

            viewPort.drawWall(
                wallType = 0,
                wallPosition = 8,
                vmp = vmp,
                vcn = vcn,
                pal = pal
            )

            viewPort.drawWall(
                wallType = 0,
                wallPosition = 13,
                vmp = vmp,
                vcn = vcn,
                pal = pal
            )

            viewPort.drawWall(
                wallType = 1,
                wallPosition = 22,
                vmp = vmp,
                vcn = vcn,
                pal = pal
            )

            viewPort.drawWall(
                wallType = 1,
                wallPosition = 23,
                vmp = vmp,
                vcn = vcn,
                pal = pal
            )

            viewPort.drawWall(
                wallType = 0,
                wallPosition = 24,
                vmp = vmp,
                vcn = vcn,
                pal = pal
            )

            viewPort
        }
    }

    private fun readVmp(name: String, reader: ByteReader): Vmp {
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

        return Vmp(
            name = name,
            tileIndexes = tileIndexes
        )
    }

    /**
     * Decodes a 16-bit value into a TileIndex structure.
     *
     * Bit layout (MSB first):
     * - Bit 15: z-mask
     * - Bit 14: mirror_x
     * - Bits 0-13: tile_index (14-bit unsigned)
     */
    private fun decodeTileIndex(value: Int): Vmp.TileIndex {
        val zMask = (value and 0x8000) != 0
        val mirrorX = false // (value and 0x4000) != 0
        val tileIndex = value and 0x3FFF

        return Vmp.TileIndex(
            zMask = zMask,
            mirrorX = mirrorX,
            tileIndex = tileIndex
        )
    }

    companion object {
        private const val TAG = "VmpRepository"
    }
}
