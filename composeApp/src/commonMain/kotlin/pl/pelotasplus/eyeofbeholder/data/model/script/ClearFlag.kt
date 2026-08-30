package pl.pelotasplus.eyeofbeholder.data.model.script

import pl.pelotasplus.eyeofbeholder.data.ByteReader

/**
 * Clears a game state flag. Opcode 0xF5.
 *
 * The inverse of [SetFlag] — resets a flag to 0/false. Used to re-arm
 * triggers, reset quest states, or stop the party resting.
 *
 * [RestingForbidden] is named for what it does rather than for the flag it
 * writes: the flag means *no resting*, so clearing it stops the party and
 * setting it lets them again. [SetFlag.RestingAllowed] is the other half.
 */
sealed class ClearFlag : ScriptToken {

    data class LevelFlag(val flag: Int) : ClearFlag()         // type = -17 (0xEF)
    data class GlobalFlag(val flag: Int) : ClearFlag()        // type = -16 (0xF0)
    data object Event : ClearFlag()                            // type = -28 (0xE4)
    data object RestingForbidden : ClearFlag()                 // type = -47 (0xD1)
    data class Unknown(val type: Int) : ClearFlag()

    companion object {
        fun read(reader: ByteReader): ClearFlag {
            return when (val type = reader.readU8()) {
                0xEF -> LevelFlag(flag = reader.readU8())      // -17 level flag
                0xF0 -> GlobalFlag(flag = reader.readU8())     // -16 global flag
                0xE4 -> Event                                  // -28 event
                0xD1 -> RestingForbidden                       // -47 no resting
                else -> error("Unknown ClearFlag type: ${type.toHexString()}")
            }
        }
    }
}
