package pl.pelotasplus.eyeofbeholder.data.model.script

import pl.pelotasplus.eyeofbeholder.data.ByteReader
import pl.pelotasplus.eyeofbeholder.data.model.Location

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
