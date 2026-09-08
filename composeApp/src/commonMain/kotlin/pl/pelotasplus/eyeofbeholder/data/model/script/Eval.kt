package pl.pelotasplus.eyeofbeholder.data.model.script

import pl.pelotasplus.eyeofbeholder.data.ByteReader

/**
 * A branch: a condition to work out, and where to carry on if it comes out
 * false.
 *
 * The condition is [tokens] — a little stack machine of its own, described in
 * [Conditional] — and [goto] is the offset the script jumps to when it does
 * not hold. Falling through means the condition held.
 */
data class Eval(
    val tokens: List<Conditional>,
    val goto: ScriptOffset
) : ScriptToken {

    companion object {
        fun read(reader: ByteReader): Eval {
            val tokens = mutableListOf<Conditional>()

            while (true) {
                val cmd = reader.readI8()
                val opcode = cmd.toUByte().toInt()

                if (opcode == 0xEE) { // Else marker - end of condition
                    break
                }

                val token = Conditional.fromOpcode(opcode, reader)
                tokens.add(token)
            }

            return Eval(
                tokens = tokens,
                goto = ScriptOffset(reader.readU16LE())
            )
        }
    }
}
