package pl.pelotasplus.eyeofbeholder.data.model.script

import pl.pelotasplus.eyeofbeholder.data.ByteReader
import pl.pelotasplus.eyeofbeholder.data.model.Location

/**
 * Places a new item in the dungeon at runtime. Opcode 0xEA.
 *
 * Used by scripts to spawn quest items, treasure, or key items when events trigger.
 * The [flags] field controls which optional properties follow in the bytecode:
 * - bit 0: itemValue present (magical bonus, charges, key ID)
 * - bit 1: itemFlag present (additional item flags)
 * - bit 2: itemIcon present (override the item's default icon)
 *
 * @property itemId Index into the global item table (ITEM.DAT)
 * @property location Maze position to place the item
 * @property subPos Position within the square (0-3 = floor quadrant, 8 = wall niche)
 * @property flags Bitmask controlling which optional fields are present
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
