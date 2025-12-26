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

            val width = reader.readU16LE() // always 32
            val height = reader.readU16LE() // always 32
            val faces = reader.readU16LE() // north, south, west, east

            Logger.d(TAG) { "Maz $name width $width x $height faces $faces" }

            val squares = mutableListOf<Maz.Square>()
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val north = reader.readU8()
                    val east = reader.readU8()
                    val south = reader.readU8()
                    val west = reader.readU8()

                    Logger.d(TAG) {
                        "Square $x, $y north $north south $south west $west east $east"
                    }

                    val square = Maz.Square(
                        x = x,
                        y = y,
                        north = north,
                        south = south,
                        west = west,
                        east = east
                    )

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


