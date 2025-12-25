package pl.pelotasplus.eyeofbeholder.data.model.script

import pl.pelotasplus.eyeofbeholder.data.ByteReader

/**
 * SetFlag script token.
 * Sets various flags: level, global, monster, dialog result, or prevent rest.
 */
sealed class SetFlag : ScriptToken {

    override fun read(reader: ByteReader): ScriptToken = read(reader)

    data class LevelFlag(val flag: Int) : SetFlag()           // type = -17 (0xEF)
    data class GlobalFlag(val flag: Int) : SetFlag()          // type = -16 (0xF0)
    data class MonsterFlag(
        val monsterId: Int,
        val flag: Int
    ) : SetFlag()                                              // type = -13 (0xF3)
    data object DialogResult : SetFlag()                       // type = -28 (0xE4)
    data object PreventRest : SetFlag()                        // type = -47 (0xD1)
    data class Unknown(val type: Int) : SetFlag()

    companion object {
        fun read(reader: ByteReader): SetFlag {
            return when (val type = reader.readI8()) {
                -17 -> LevelFlag(flag = reader.readU8())       // 0xEF - level flag
                -16 -> GlobalFlag(flag = reader.readU8())      // 0xF0 - global flag
                -13 -> MonsterFlag(                            // 0xF3 - monster flag
                    monsterId = reader.readU8(),
                    flag = reader.readU8()
                )
                -28 -> DialogResult                            // 0xE4 - event/dialog
                -47 -> PreventRest                             // 0xD1 - party can't sleep
                else -> error("Unknown flag type $type")
            }
        }
    }
}
