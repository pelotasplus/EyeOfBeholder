package pl.pelotasplus.eyeofbeholder.data.model.script

import pl.pelotasplus.eyeofbeholder.data.ByteReader

/**
 * SpecialEvent script token.
 * Triggers various special game events.
 */
sealed class SpecialEvent : ScriptToken {

    data object DrawLightningColumn : SpecialEvent()          // cmd = 0
    data object CharSelectDialogue : SpecialEvent()           // cmd = 1
    data object CharacterLevelGain : SpecialEvent()           // cmd = 2
    data object ResurrectionSelectDialogue : SpecialEvent()   // cmd = 3
    data object InitNpc : SpecialEvent()                      // cmd = 4
    data object DeletePartyItems : SpecialEvent()             // cmd = 5
    data object LoadVcnData : SpecialEvent()                  // cmd = 6
    data class Unknown(val cmd: Int) : SpecialEvent()

    companion object {
        fun read(reader: ByteReader): SpecialEvent {
            return when (val cmd = reader.readU16LE()) {
                0 -> DrawLightningColumn
                1 -> CharSelectDialogue
                2 -> CharacterLevelGain
                3 -> ResurrectionSelectDialogue
                4 -> InitNpc
                5 -> DeletePartyItems
                6 -> LoadVcnData
                else -> Unknown(cmd)
            }
        }
    }
}
