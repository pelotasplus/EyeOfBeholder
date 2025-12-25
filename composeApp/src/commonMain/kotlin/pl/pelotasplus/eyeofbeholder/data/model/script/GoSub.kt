package pl.pelotasplus.eyeofbeholder.data.model.script

import pl.pelotasplus.eyeofbeholder.data.ByteReader

data class GoSub(
    val offset: Int,
) : ScriptToken {

    companion object {
        fun read(reader: ByteReader): GoSub {
            val offset = reader.readU16LE()

            return GoSub(
                offset = offset,
            )
        }
    }
}
