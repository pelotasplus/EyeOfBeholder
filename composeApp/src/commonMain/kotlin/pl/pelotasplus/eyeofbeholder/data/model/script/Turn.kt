package pl.pelotasplus.eyeofbeholder.data.model.script

import pl.pelotasplus.eyeofbeholder.data.ByteReader

/**
 * Forces a direction change on the party or flying projectiles. Opcode 0xE8.
 *
 * Used by spinners (squares that randomly rotate the party), scripted
 * teleport sequences, and projectile redirection traps.
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
