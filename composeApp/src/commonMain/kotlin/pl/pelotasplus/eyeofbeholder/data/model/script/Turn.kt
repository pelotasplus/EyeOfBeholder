package pl.pelotasplus.eyeofbeholder.data.model.script

import pl.pelotasplus.eyeofbeholder.data.ByteReader

/**
 * Turn script token.
 * Turns the party or flying objects.
 */
sealed class Turn : ScriptToken {

    abstract val dir: Int

    /** Turn party direction. cmd = -15 (0xF1) */
    data class TurnParty(override val dir: Int) : Turn()

    /** Turn flying objects. cmd = -11 (0xF5) */
    data class TurnFlyingObjects(override val dir: Int) : Turn()

    data class Unknown(val cmd: Int, override val dir: Int) : Turn()

    companion object {
        fun read(reader: ByteReader): Turn {
            val cmd = reader.readI8()
            val dir = reader.readI8()

            return when (cmd) {
                -15 -> TurnParty(dir)           // 0xF1
                -11 -> TurnFlyingObjects(dir)   // 0xF5
                else -> Unknown(cmd, dir)
            }
        }
    }
}
