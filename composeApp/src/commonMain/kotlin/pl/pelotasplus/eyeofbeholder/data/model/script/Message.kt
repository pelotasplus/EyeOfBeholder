package pl.pelotasplus.eyeofbeholder.data.model.script

import pl.pelotasplus.eyeofbeholder.data.ByteReader

/**
 * Displays a text message in the game's message area. Opcode 0xF8.
 *
 * @property messageId Index into [Inf.messages] string array
 * @property color Text color (palette index or predefined color constant)
 */
data class Message(
    val messageId: Int,
    val color: Int,
) : ScriptToken {


    companion object {
        fun read(reader: ByteReader): Message {
            val messageId = reader.readU16LE()
            val color = reader.readU16LE()

            return Message(
                messageId = messageId,
                color = color
            )
        }
    }
}
