package pl.pelotasplus.eyeofbeholder.data.model.script

import pl.pelotasplus.eyeofbeholder.data.ByteReader

sealed class Dialog : ScriptToken {

    override fun read(reader: ByteReader): ScriptToken = read(reader)

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
        val textId: Int,
        val button1: Int,
        val button2: Int,
        val button3: Int
    ) : Dialog()

    data class DialogText(
        val x: Int,
        val y: Int
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
                    textId = reader.readI16LE(),
                    button1 = reader.readI16LE(),
                    button2 = reader.readI16LE(),
                    button3 = reader.readI16LE()
                )

                0xF8 -> DialogText(
                    x = reader.readU16LE(),
                    y = reader.readU16LE()
                )

                else -> error("Unknown dialog type: ${type.toHexString()}")
            }
        }
    }
}
