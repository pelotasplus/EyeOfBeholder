package pl.pelotasplus.eyeofbeholder.data.model.script

import pl.pelotasplus.eyeofbeholder.data.ByteReader

/** Jump to a script subroutine, pushing the return address. Opcode 0xEF. */
data class GoSub(
    val offset: ScriptOffset,
) : ScriptToken {

    companion object {
        fun read(reader: ByteReader) = GoSub(ScriptOffset(reader.readU16LE()))
    }
}
