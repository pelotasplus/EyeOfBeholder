package pl.pelotasplus.eyeofbeholder.data.repository

import co.touchlab.kermit.Logger
import kotlinx.collections.immutable.toImmutableList
import pl.pelotasplus.eyeofbeholder.data.ByteReader
import pl.pelotasplus.eyeofbeholder.data.model.Dec
import pl.pelotasplus.eyeofbeholder.data.model.Dec.DecorationRectangle

/**
 * Parses .DEC files — decoration layout definitions.
 *
 * ## Binary format (.DEC, uncompressed)
 * - decorationCount (u16)
 * - For each decoration:
 *   - 10 rectangle indices (u8 each) — which CPS rectangle to use at each of
 *     10 viewing distances/angles (0xFF = not visible at that distance)
 *   - linkToNextDecoration (u8) — chains multi-part decorations (0 = end of chain)
 *   - flags (u8) — bit 0 = mirror on front walls
 *   - 10 x-coordinates (u16 each) — screen X position for each viewing distance
 *   - 10 y-coordinates (u16 each) — screen Y position for each viewing distance
 * - rectangleCount (u16)
 * - For each rectangle: x, y, w, h (u16 each)
 *   - x and w are in 8-pixel units (multiply by 8 for actual pixel coordinates)
 *   - y and h are in pixel units
 */
interface DecRepository {
    suspend fun loadDec(name: String): Result<Dec>
}

class DecRepositoryImpl(
    private val resourceRepository: ResourceRepository
) : DecRepository {
    override suspend fun loadDec(name: String): Result<Dec> {
        return runCatching {
            val bytes = resourceRepository.readResource("files/$name")
            val reader = ByteReader(bytes)

            val numberOfDecorations = reader.readU16LE()
            Logger.d(TAG) { "Number of decorations: $numberOfDecorations" }

            val decorations = (0 until numberOfDecorations).map { index ->
                val rectangleIndices = (0 until 10).map { reader.readU8() }.toImmutableList()
                val linkToNextDecoration = reader.readU8()
                val flags = reader.readU8()
                val xCoords = (0 until 10).map { reader.readU16LE() }.toImmutableList()
                val yCoords = (0 until 10).map { reader.readU16LE() }.toImmutableList()

                Dec.Decoration(
                    index = index,
                    rectangleIndices = rectangleIndices,
                    linkToNextDecoration = linkToNextDecoration,
                    flags = flags,
                    xCoords = xCoords,
                    yCoords = yCoords,
                )
            }.toImmutableList()

            Logger.d(TAG) { "After reading decorations ${reader.remaining}" }

            val numberOfDecorationRectangles = reader.readU16LE()
            Logger.d(TAG) { "Number of decoration rectangles: $numberOfDecorationRectangles" }

            val rectangles = (0 until numberOfDecorationRectangles).map {
                DecorationRectangle(
                    x = reader.readU16LE(),
                    y = reader.readU16LE(),
                    w = reader.readU16LE(),
                    h = reader.readU16LE(),
                )
            }.toImmutableList()

            check(reader.remaining == 0) {
                "Expected 0 bytes remaining, but got ${reader.remaining}"
            }

            Dec(
                name = name,
                decorations = decorations,
                rectangles = rectangles,
            )
        }
    }

    companion object {
        private const val TAG = "DecRepository"
    }
}
