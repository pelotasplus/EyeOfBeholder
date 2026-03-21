package pl.pelotasplus.eyeofbeholder.data.repository

import co.touchlab.kermit.Logger
import pl.pelotasplus.eyeofbeholder.data.ByteReader
import pl.pelotasplus.eyeofbeholder.data.model.Maz

/**
 * Parses .MAZ files — the dungeon floor layout.
 *
 * ## Binary format (.MAZ)
 * - Header: width (u16), height (u16), bytesPerSquare (u16) — always 32, 32, 4
 * - Body: 32×32 = 1024 squares in row-major order (y outer loop, x inner loop)
 * - Each square: 4 bytes → north wall, east wall, south wall, west wall
 * - Each wall byte maps to [Maz.WallType] via [Maz.WallType.fromInt]
 *
 * Total file size: 6 (header) + 4096 (squares) = 4102 bytes, always uncompressed.
 */
interface MazRepository {
    suspend fun loadMaz(name: String): Result<Maz>
}

class MazRepositoryImpl(
    private val resourceRepository: ResourceRepository
) : MazRepository {
    override suspend fun loadMaz(name: String): Result<Maz> {
        return runCatching {
            val bytes = resourceRepository.readResource("files/$name")

            val reader = ByteReader(bytes)

            val width = reader.readU16LE()
            check(width == 32) {
                "Expected width 32, got $width"
            }
            val height = reader.readU16LE()
            check(height == 32) {
                "Expected height 32, got $height"
            }
            val size = reader.readU16LE()
            check(size == 4) {
                "Expected size 4, got $size"
            }

            Logger.d(TAG) { "Maz $name width $width x $height size $size" }

            val squares = mutableListOf<Maz.Square>()
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val north = reader.readU8()
                    val east = reader.readU8()
                    val south = reader.readU8()
                    val west = reader.readU8()

                    val square = Maz.Square(
                        x = x,
                        y = y,
                        north = Maz.WallType.fromInt(north),
                        south = Maz.WallType.fromInt(south),
                        west = Maz.WallType.fromInt(west),
                        east = Maz.WallType.fromInt(east)
                    )

                    Logger.d(TAG) { "$square" }

                    squares.add(square)
                }
            }

            check(reader.remaining == 0) {
                "Expected empty reader after all Maz parsing"
            }

            Maz(
                name = name,
                width = width,
                height = height,
                squares = squares
            )
        }
    }

    companion object {
        private const val TAG = "MazRepository"
    }
}


