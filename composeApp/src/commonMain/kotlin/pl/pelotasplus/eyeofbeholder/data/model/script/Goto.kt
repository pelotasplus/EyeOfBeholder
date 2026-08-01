package pl.pelotasplus.eyeofbeholder.data.model.script

import pl.pelotasplus.eyeofbeholder.data.ByteReader

/** Unconditional jump to a script offset. Opcode 0xF2. */
data class Goto(
    val offset: ScriptOffset,
) : ScriptToken {

    companion object {
        fun read(reader: ByteReader) = Goto(
            offset = ScriptOffset(reader.readU16LE()),
        )
    }
}
