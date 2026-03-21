package pl.pelotasplus.eyeofbeholder.data.model.script

import pl.pelotasplus.eyeofbeholder.data.ByteReader
import pl.pelotasplus.eyeofbeholder.data.model.Location

/**
 * Launches a flying projectile in the dungeon. Opcode 0xE9.
 *
 * Creates a projectile (arrow, dart, magic bolt, etc.) that travels in a
 * direction from a starting location. Used for arrow traps, spell effects,
 * and thrown items triggered by scripts.
 *
 * @see MagicObject for spell projectiles (fireball, magic missile, etc.)
 * @see PhysicalItem for physical projectiles (arrows, darts, rocks)
 */
sealed class Launcher : ScriptToken {

    abstract val itemId: Int
    abstract val location: Location
    abstract val dir: Int
    abstract val dirOffset: Int

    /** Launch magic object. first byte = -33 (0xDF) */
    data class MagicObject(
        override val itemId: Int,
        override val location: Location,
        override val dir: Int,
        override val dirOffset: Int
    ) : Launcher()

    /** Launch physical item. first byte != -33 */
    data class PhysicalItem(
        override val itemId: Int,
        override val location: Location,
        override val dir: Int,
        override val dirOffset: Int
    ) : Launcher()

    companion object {
        fun read(reader: ByteReader): Launcher {
            val isMagic = reader.readI8() == -33 // 0xDF

            val itemId = reader.readU16LE()
            val location = Location.read(reader)
            val dir = reader.readU8()
            val dirOffset = reader.readU8()

            return if (isMagic) {
                MagicObject(
                    itemId = itemId,
                    location = location,
                    dir = dir,
                    dirOffset = dirOffset
                )
            } else {
                PhysicalItem(
                    itemId = itemId,
                    location = location,
                    dir = dir,
                    dirOffset = dirOffset
                )
            }
        }
    }
}
