package pl.pelotasplus.eyeofbeholder.data.model.script

import pl.pelotasplus.eyeofbeholder.data.ByteReader
import pl.pelotasplus.eyeofbeholder.data.model.Location

/**
 * Spawns a monster at a specified maze location. Opcode 0xFB.
 *
 * The monster's combat stats come from [MonsterProperty] definitions in the sublevel.
 * [type] selects which MonsterProperty to use, and [unit] determines the monster's
 * graphic slot (for animation and rendering).
 *
 * @property unit Monster graphic unit index (selects which MonsterGfx sprite sheet)
 * @property timer Respawn timer value
 * @property location Maze position to spawn at
 * @property pos Sub-position within the square (0-3 for quadrants)
 * @property dir Facing direction (-1 = random, 0-3 = N/E/S/W)
 * @property type Monster type index (references sublevel's MonsterProperty list)
 * @property frame Starting animation frame
 * @property phase AI behavior phase (idle, patrol, aggressive, etc.)
 * @property pause Initial pause before the monster acts
 * @property pocket Item ID in the monster's pocket (dropped on death)
 * @property weapon Item ID of the monster's wielded weapon
 */
data class CreateMonster(
    val unit: Int,
    val timer: Int,
    val location: Location,
    val pos: Int,
    val dir: Int,
    val type: Int,
    val frame: Int,
    val phase: Int,
    val pause: Int,
    val pocket: Int,
    val weapon: Int
) : ScriptToken {

    companion object {
        fun read(reader: ByteReader): CreateMonster {
            return CreateMonster(
                unit = reader.readU8(),
                timer = reader.readU8(),
                location = Location.read(reader),
                pos = reader.readU8(),
                dir = reader.readI8(),
                type = reader.readU8(),
                frame = reader.readU8(),
                phase = reader.readU8(),
                pause = reader.readU8(),
                pocket = reader.readU16LE(),
                weapon = reader.readU16LE()
            )
        }
    }
}
