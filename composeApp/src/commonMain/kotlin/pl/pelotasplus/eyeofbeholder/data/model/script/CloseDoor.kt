package pl.pelotasplus.eyeofbeholder.data.model.script

import pl.pelotasplus.eyeofbeholder.data.ByteReader
import pl.pelotasplus.eyeofbeholder.data.model.Location

/**
 * Closes a door at the specified location. Opcode 0xFC.
 * Changes the door's wall type state from open to closed.
 */
data class CloseDoor(
    val location: Location
) : ScriptToken {

    companion object {
        fun read(reader: ByteReader): ScriptToken {
            return CloseDoor(
                location = Location.read(reader)
            )
        }
    }
}
