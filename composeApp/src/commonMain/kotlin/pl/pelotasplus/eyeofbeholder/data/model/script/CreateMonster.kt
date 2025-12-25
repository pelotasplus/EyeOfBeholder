package pl.pelotasplus.eyeofbeholder.data.model.script

import pl.pelotasplus.eyeofbeholder.data.ByteReader
import pl.pelotasplus.eyeofbeholder.data.model.Location

/**
 * CreateMonster script token.
 * Spawns a monster at a specified location.
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
