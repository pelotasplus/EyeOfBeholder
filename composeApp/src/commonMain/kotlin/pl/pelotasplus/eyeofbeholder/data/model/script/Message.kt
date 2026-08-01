package pl.pelotasplus.eyeofbeholder.data.model.script

import pl.pelotasplus.eyeofbeholder.data.ByteReader
import pl.pelotasplus.eyeofbeholder.data.model.MessageId

/**
 * Displays a text message in the game's message area. Opcode 0xF8.
 *
 * @property messageId Which of the level's own messages to print
 * @property color Text color (palette index or predefined color constant)
 */
data class Message(
    val messageId: MessageId,
    val color: Int,
) : ScriptToken {


    companion object {
        fun read(reader: ByteReader): Message {
            val messageId = MessageId(reader.readU16LE())
            val color = reader.readU16LE()

            return Message(
                messageId = messageId,
                color = color
            )
        }
    }
}
