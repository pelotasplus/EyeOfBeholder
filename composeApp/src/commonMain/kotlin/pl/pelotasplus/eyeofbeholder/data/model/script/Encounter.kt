package pl.pelotasplus.eyeofbeholder.data.model.script

import pl.pelotasplus.eyeofbeholder.data.ByteReader

/**
 * Triggers a special encounter or cutscene sequence. Opcode 0xE6.
 *
 * These are the game's major narrative events: NPC conversations, the death
 * sequence, portal animations, and password-protected doors.
 */
sealed class Encounter : ScriptToken {

    /** Death sequence. cmd = -3 (or 9 in EOB1) */
    data object DeathSequence : Encounter()

    /** Portal sequence. cmd = -2 (or 8 in EOB1) */
    data object PortalSequence : Encounter()

    /** Password check. cmd = -1 (or 10 in EOB1) */
    data object PasswordCheck : Encounter()

    /** NPC sequence. cmd >= 0 */
    data class NpcSequence(val npcId: Int) : Encounter()

    companion object {
        fun read(reader: ByteReader): Encounter {
            val cmd = reader.readI8()

            // EOB1 remapping: 10 -> -1, 9 -> -3, 8 -> -2
            val normalizedCmd = when (cmd) {
                10 -> -1
                9 -> -3
                8 -> -2
                else -> cmd
            }

            return when (normalizedCmd) {
                -3 -> DeathSequence
                -2 -> PortalSequence
                -1 -> PasswordCheck
                else -> NpcSequence(npcId = normalizedCmd)
            }
        }
    }
}
