package pl.pelotasplus.eyeofbeholder.data.repository

import co.touchlab.kermit.Logger
import pl.pelotasplus.eyeofbeholder.data.ByteReader
import pl.pelotasplus.eyeofbeholder.data.model.Dcr
import pl.pelotasplus.eyeofbeholder.data.model.MonsterFrameRect
import pl.pelotasplus.eyeofbeholder.data.model.MonsterPose

/**
 * Parses .DCR files — the overlay shapes for a monster sprite sheet.
 *
 * ## Binary format (.DCR, uncompressed)
 * - setCount (u16)
 * - For each set, one record per pose, in the order the poses sit on the
 *   sheet ([MonsterPose]), 6 bytes each:
 *   - x (u8) — in 8-pixel units, into the monster's own .CPS sheet
 *   - y (u8) — in pixels
 *   - w (u8) — in 8-pixel units
 *   - h (u8) — in pixels
 *   - offsetX (i8), offsetY (i8) — from the monster sprite's top-left corner
 *
 * A record with no width or no height is a pose the set does not decorate.
 */
interface DcrRepository {
    suspend fun loadDcr(name: String): Result<Dcr>
}

class DcrRepositoryImpl(
    private val resourceRepository: ResourceRepository
) : DcrRepository {

    override suspend fun loadDcr(name: String): Result<Dcr> {
        return runCatching {
            val reader = ByteReader(resourceRepository.readResource("files/$name"))

            val setCount = reader.readU16LE()
            Logger.d(TAG) { "$name holds $setCount decoration sets" }

            val sets = (0 until setCount).map {
                buildMap {
                    for (pose in MonsterPose.entries) {
                        val x = reader.readU8() * 8
                        val y = reader.readU8()
                        val w = reader.readU8() * 8
                        val h = reader.readU8()
                        val offsetX = reader.readI8()
                        val offsetY = reader.readI8()

                        if (w != 0 && h != 0) {
                            put(pose, Dcr.Placement(MonsterFrameRect(x, y, w, h), offsetX, offsetY))
                        }
                    }
                }
            }

            Dcr(name = name, sets = sets)
        }
    }

    companion object {
        private const val TAG = "DcrRepository"
    }
}
