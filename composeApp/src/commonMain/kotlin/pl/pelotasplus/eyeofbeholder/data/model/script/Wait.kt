package pl.pelotasplus.eyeofbeholder.data.model.script

import pl.pelotasplus.eyeofbeholder.data.ByteReader

/**
 * Pauses script execution for a number of game ticks. Opcode 0xE5.
 *
 * @property delay Number of ticks to wait before continuing script execution
 */
data class Wait(
    val delay: Int
) : ScriptToken {

    

    companion object {
        fun read(reader: ByteReader): Wait {
            val delay = reader.readU16LE()

            return Wait(
                delay = delay,
            )
        }
    }
}
