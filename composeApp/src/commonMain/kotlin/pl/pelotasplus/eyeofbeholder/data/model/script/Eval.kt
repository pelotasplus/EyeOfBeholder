package pl.pelotasplus.eyeofbeholder.data.model.script

import pl.pelotasplus.eyeofbeholder.data.ByteReader

/**
 * Conditional evaluation script token.
 * See https://github.com/scummvm/scummvm/blob/master/engines/kyra/script/script_eob.cpp
 */
data class Eval(
    val tokens: List<Conditional>,
    val goto: Int
) : ScriptToken {

    override fun read(reader: ByteReader): ScriptToken = read(reader)

    companion object {
        fun read(reader: ByteReader): Eval {
            val tokens = mutableListOf<Conditional>()

            while (true) {
                val opcode = reader.readU8()
                if (opcode == 0xEE) { // Else marker - end of condition
                    break
                }

                val token = Conditional.fromOpcode(opcode, reader)
                tokens.add(token)
            }

            val goto = reader.readU16LE()

            return Eval(
                tokens = tokens,
                goto = goto
            )
        }
    }
}
