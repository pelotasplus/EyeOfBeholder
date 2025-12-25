package pl.pelotasplus.eyeofbeholder.data.model.script

import pl.pelotasplus.eyeofbeholder.data.ByteReader
import pl.pelotasplus.eyeofbeholder.data.model.Location

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
