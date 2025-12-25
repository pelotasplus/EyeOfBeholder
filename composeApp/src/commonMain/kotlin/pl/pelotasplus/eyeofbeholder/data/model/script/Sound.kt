package pl.pelotasplus.eyeofbeholder.data.model.script

import pl.pelotasplus.eyeofbeholder.data.ByteReader
import pl.pelotasplus.eyeofbeholder.data.model.Location

data class Sound(
    val soundId: Int,
    val location: Location
) : ScriptToken {

    

    companion object {
        fun read(reader: ByteReader): Sound {
            val soundId = reader.readU8()
            val location = Location.read(reader)

            return Sound(
                soundId = soundId,
                location = location,
            )
        }
    }
}
