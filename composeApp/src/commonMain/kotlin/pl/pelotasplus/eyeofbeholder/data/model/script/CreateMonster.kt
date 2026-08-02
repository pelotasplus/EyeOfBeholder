package pl.pelotasplus.eyeofbeholder.data.model.script

import pl.pelotasplus.eyeofbeholder.data.ByteReader
import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.MonsterTypeId

/**
 * Puts a monster into the world. Opcode 0xFB.
 *
 * The record is the one a level file uses for the monsters it starts out
 * with, save for the slot number: a level file names the slot, while a script
 * leaves the engine to find one.
 *
 * @property unit Which group the monster is updated with
 * @property location Maze square to stand on
 * @property pos Sub-position within the square (0-3 = quadrants, 4 = middle)
 * @property direction Which way it faces
 * @property type Its species, an index into the sublevel's monster properties
 * @property gfxIndex Which of the sublevel's sprite sheets it is drawn from
 * @property mode Behaviour mode at spawn
 * @property pause Movement pause counter
 * @property weapon Item type id of the held weapon (0 = none)
 * @property pocketItem Item type id carried as loot (0 = none)
 */
data class CreateMonster(
    val unit: Int,
    val location: Location,
    val pos: Int,
    val direction: Direction,
    val type: MonsterTypeId,
    val gfxIndex: Int,
    val mode: Int,
    val pause: Int,
    val weapon: Int,
    val pocketItem: Int,
) : ScriptToken {

    companion object {
        fun read(reader: ByteReader): CreateMonster {
            // every one of the game's spawns opens with the same byte, which
            // the engine reads past without looking at
            reader.skip(1)

            return CreateMonster(
                unit = reader.readU8(),
                location = Location.read(reader),
                pos = reader.readU8(),
                // -1 asks the engine to roll for a facing, which no level does
                direction = Direction.entries.getOrElse(reader.readI8()) { Direction.NORTH },
                type = MonsterTypeId(reader.readU8()),
                gfxIndex = reader.readU8(),
                mode = reader.readU8(),
                pause = reader.readU8(),
                weapon = reader.readU16LE(),
                pocketItem = reader.readU16LE(),
            )
        }
    }
}
