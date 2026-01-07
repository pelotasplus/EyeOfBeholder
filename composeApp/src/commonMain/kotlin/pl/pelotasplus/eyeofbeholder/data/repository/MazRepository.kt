package pl.pelotasplus.eyeofbeholder.data.repository

import co.touchlab.kermit.Logger
import pl.pelotasplus.eyeofbeholder.data.ByteReader
import pl.pelotasplus.eyeofbeholder.data.model.Maz

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


