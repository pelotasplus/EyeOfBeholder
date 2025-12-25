package pl.pelotasplus.eyeofbeholder.data.model.script

import pl.pelotasplus.eyeofbeholder.data.ByteReader

data class Goto(
    val offset: Int,
) : ScriptToken {

    override fun read(reader: ByteReader): ScriptToken = read(reader)

    companion object {
        fun read(reader: ByteReader): Goto {
            val offset = reader.readU16LE()

            return Goto(
                offset = offset,
            )
        }
    }
}
