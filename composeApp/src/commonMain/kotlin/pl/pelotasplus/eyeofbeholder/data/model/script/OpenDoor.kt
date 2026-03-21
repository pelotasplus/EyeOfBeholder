package pl.pelotasplus.eyeofbeholder.data.model.script

import pl.pelotasplus.eyeofbeholder.data.ByteReader
import pl.pelotasplus.eyeofbeholder.data.model.Location

/**
 * Opens a door at the specified location. Opcode 0xFD.
 * Changes the door's wall type state from closed to open.
 */
data class OpenDoor(
    val location: Location
) : ScriptToken {

    companion object {
        fun read(reader: ByteReader): ScriptToken {
            return OpenDoor(
                location = Location.read(reader)
            )
        }
    }
}
