package pl.pelotasplus.eyeofbeholder.data.model.script

import pl.pelotasplus.eyeofbeholder.data.ByteReader
import pl.pelotasplus.eyeofbeholder.data.model.DialogueTextId
import pl.pelotasplus.eyeofbeholder.data.model.MessageId

/**
 * Controls the dialog/cutscene UI system. Opcode 0xE3.
 *
 * Manages NPC conversation screens, picture displays, and interactive
 * dialog choices. Dialog sequences are composed of multiple Dialog tokens
 * in sequence: display a picture, draw the dialog box, show text, then
 * run the dialog with button choices.
 */
sealed class Dialog : ScriptToken {

    data class DisplayPicture(
        val pictureName: String,
        val rect: Int,
        val x: Int,
        val y: Int,
        val flags: Int  // flags & 1 = border, flags & 2 = fade
    ) : Dialog()

    data object CloseDialog : Dialog()

    data object DisplayBackground : Dialog()

    data object DrawDialogBox : Dialog()

    data class RunDialog(
        val textId: DialogueTextId,
        val button1: MessageId,
        val button2: MessageId,
        val button3: MessageId
    ) : Dialog()

    /**
     * Prints a speech into the dialogue box that is already on screen, then
     * waits on a button labelled with [pageBreakLabel] — usually "ok". The
     * speech is allowed to be empty: the branches that roar at the party print
     * that as a message first and use this only to be acknowledged.
     */
    data class DialogText(
        val textId: DialogueTextId,
        val pageBreakLabel: MessageId
    ) : Dialog()

    data class Unknown(val type: Int) : Dialog()

    companion object {
        fun read(reader: ByteReader): Dialog {
            return when (val type = reader.readU8()) {
                0xD3 -> DisplayPicture(
                    pictureName = reader.readString(13),
                    rect = reader.readU8(),
                    x = reader.readU16LE(),
                    y = reader.readU16LE(),
                    flags = reader.readU16LE()
                )

                0xD4 -> CloseDialog
                0xD5 -> DisplayBackground
                0xD6 -> DrawDialogBox
                0xD8 -> RunDialog(
                    textId = DialogueTextId(reader.readI16LE()),
                    button1 = MessageId(reader.readI16LE()),
                    button2 = MessageId(reader.readI16LE()),
                    button3 = MessageId(reader.readI16LE())
                )

                0xF8 -> DialogText(
                    textId = DialogueTextId(reader.readU16LE()),
                    pageBreakLabel = MessageId(reader.readU16LE())
                )

                else -> error("Unknown dialog type: ${type.toHexString()}")
            }
        }
    }
}
