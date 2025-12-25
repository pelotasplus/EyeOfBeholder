package pl.pelotasplus.eyeofbeholder.data.model.script

import pl.pelotasplus.eyeofbeholder.data.ByteReader

/**
 * ClearFlag script token.
 * Clears various flags: level, global, event, or party.
 */
sealed class ClearFlag : ScriptToken {

    data class LevelFlag(val flag: Int) : ClearFlag()         // type = -17 (0xEF)
    data class GlobalFlag(val flag: Int) : ClearFlag()        // type = -16 (0xF0)
    data object Event : ClearFlag()                            // type = -28 (0xE4)
    data object Party : ClearFlag()                            // type = -47 (0xD1)
    data class Unknown(val type: Int) : ClearFlag()

    companion object {
        fun read(reader: ByteReader): ClearFlag {
            return when (val type = reader.readU8()) {
                0xEF -> LevelFlag(flag = reader.readU8())      // -17 level flag
                0xF0 -> GlobalFlag(flag = reader.readU8())     // -16 global flag
                0xE4 -> Event                                  // -28 event
                0xD1 -> Party                                  // -47 party
                else -> error("Unknown ClearFlag type: ${type.toHexString()}")
            }
        }
    }
}
