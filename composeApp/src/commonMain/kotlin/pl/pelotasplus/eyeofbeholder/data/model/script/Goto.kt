package pl.pelotasplus.eyeofbeholder.data.model.script

import pl.pelotasplus.eyeofbeholder.data.ByteReader

data class Goto(
    val offset: Int,
) : ScriptToken {

    companion object {
        fun read(reader: ByteReader): Goto {
            val offset = reader.readU16LE()

            return Goto(
                offset = offset,
            )
        }
    }
}
