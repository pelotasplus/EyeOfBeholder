package pl.pelotasplus.eyeofbeholder.data.model.script

import pl.pelotasplus.eyeofbeholder.data.ByteReader
import pl.pelotasplus.eyeofbeholder.data.model.Location

/**
 * NewItem script token.
 * Adds a new item at a specified location.
 */
data class NewItem(
    val itemId: Int,
    val location: Location,
    val subPos: Int,
    val flags: Int,
    val itemValue: Int?,
    val itemFlag: Int?,
    val itemIcon: Int?
) : ScriptToken {

    

    companion object {
        fun read(reader: ByteReader): NewItem {
            val itemId = reader.readU16LE()
            val location = Location.read(reader)
            val subPos = reader.readU8()
            val flags = reader.readU8()

            val itemValue = if (flags and 1 == 1) reader.readU8() else null
            val itemFlag = if (flags and 2 == 2) reader.readU8() else null
            val itemIcon = if (flags and 4 == 4) reader.readU8() else null

            return NewItem(
                itemId = itemId,
                location = location,
                subPos = subPos,
                flags = flags,
                itemValue = itemValue,
                itemFlag = itemFlag,
                itemIcon = itemIcon
            )
        }
    }
}
