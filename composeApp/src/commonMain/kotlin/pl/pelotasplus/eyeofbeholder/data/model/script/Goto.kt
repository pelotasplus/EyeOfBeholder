package pl.pelotasplus.eyeofbeholder.data.model.script

import pl.pelotasplus.eyeofbeholder.data.ByteReader

/** Unconditional jump to a script offset. Opcode 0xF2. */
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
