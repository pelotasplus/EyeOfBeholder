package pl.pelotasplus.eyeofbeholder.data.model.script

import pl.pelotasplus.eyeofbeholder.data.ByteReader

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
