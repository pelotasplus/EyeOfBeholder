package pl.pelotasplus.eyeofbeholder.data.model.script

import pl.pelotasplus.eyeofbeholder.data.ByteReader
import pl.pelotasplus.eyeofbeholder.data.model.FlagBit
import pl.pelotasplus.eyeofbeholder.data.model.MonsterSlot

/**
 * Sets a game state flag. Opcode 0xF7.
 *
 * Flags are the primary mechanism for tracking game progress and state.
 * Scripts check flags via [Conditional.GetLevelFlag] and [Conditional.GetGlobalFlag]
 * in [Eval] expressions, and set/clear them to record events.
 *
 * - **LevelFlag**: persists only for the current level (reset when leaving)
 * - **GlobalFlag**: persists across all levels (quest progress, key events)
 * - **MonsterFlag**: per-monster state (alerted, fleeing, etc.)
 * - **DialogResult**: marks a dialog as completed
 * - **RestingAllowed**: lets the party sleep here again
 *
 * The resting one is named for what it does rather than for the flag it
 * writes: the flag means *no resting*, so setting it lets the party rest and
 * clearing it stops them. [ClearFlag.RestingForbidden] is the other half.
 */
sealed class SetFlag : ScriptToken {

    

    data class LevelFlag(val bit: FlagBit) : SetFlag()         // type = -17 (0xEF)
    data class GlobalFlag(val bit: FlagBit) : SetFlag()        // type = -16 (0xF0)
    data class MonsterFlag(
        val monsterId: MonsterSlot,
        val bit: FlagBit
    ) : SetFlag() {                                            // type = -13 (0xF3)
        companion object {
            /**
             * The bit that means a monster has been roused and will fight.
             *
             * A blow sets it too, which is why swinging at something friendly
             * ends the conversation — but a scene can set it first, and level
             * 5's clerics are turned hostile this way by the answer the party
             * give rather than by being hit.
             */
            val ROUSED = FlagBit(0)
        }
    }
    data object DialogResult : SetFlag()                       // type = -28 (0xE4)
    data object RestingAllowed : SetFlag()                     // type = -47 (0xD1)
    data class Unknown(val type: Int) : SetFlag()

    companion object {
        fun read(reader: ByteReader): SetFlag {
            return when (val type = reader.readI8()) {
                -17 -> LevelFlag(FlagBit(reader.readU8()))     // 0xEF - level flag
                -16 -> GlobalFlag(FlagBit(reader.readU8()))    // 0xF0 - global flag
                -13 -> MonsterFlag(                            // 0xF3 - monster flag
                    monsterId = MonsterSlot(reader.readU8()),
                    bit = FlagBit(reader.readU8())
                )
                -28 -> DialogResult                            // 0xE4 - event/dialog
                -47 -> RestingAllowed                          // 0xD1 - party may sleep
                else -> error("Unknown flag type $type")
            }
        }
    }
}
