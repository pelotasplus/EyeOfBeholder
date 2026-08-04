package pl.pelotasplus.eyeofbeholder.data.model.script

import pl.pelotasplus.eyeofbeholder.data.ByteReader
import pl.pelotasplus.eyeofbeholder.data.model.ItemIndex
import pl.pelotasplus.eyeofbeholder.data.model.Location

/**
 * Where a thing a script makes is put.
 *
 * Two of the three are written as squares that are not on the map, so what
 * looks like a position is really an instruction.
 */
sealed interface ItemDestination {
    /**
     * Into the hand — or, if the hand is already full, onto the square the
     * party stand on, since the thing has to go somewhere.
     */
    data object IntoTheHand : ItemDestination

    /** Onto the square the party stand on, in a corner in front of them. */
    data object Underfoot : ItemDestination

    data class OnASquare(val at: Location, val corner: Int) : ItemDestination

    companion object {
        /**
         * @param block the square as the script writes it, which is -1 for the
         *   hand and -2 for underfoot
         */
        fun of(block: Int, corner: Int): ItemDestination = when (block) {
            THE_HAND -> IntoTheHand
            UNDERFOOT -> Underfoot
            else -> OnASquare(
                at = Location(block and MAZE_WIDTH_MASK, block / MAZE_WIDTH),
                corner = corner,
            )
        }

        private const val THE_HAND = 0xFFFF
        private const val UNDERFOOT = 0xFFFE
        private const val MAZE_WIDTH = 32
        private const val MAZE_WIDTH_MASK = 31
    }
}

/**
 * Makes a new thing and puts it somewhere. Opcode 0xEA.
 *
 * What is made is a copy of one of the things already in the world's table,
 * named by [copyOf] — a script does not describe a thing, it points at one
 * that already exists and asks for another like it.
 *
 * [flags] says which of the optional bytes follow, each overriding what the
 * copy inherited:
 * - bit 0: [itemValue], a magical bonus, a number of charges, which door a key opens
 * - bit 1: [itemFlag]
 * - bit 2: [itemIcon]
 */
data class NewItem(
    val copyOf: ItemIndex,
    val goes: ItemDestination,
    val flags: Int,
    val itemValue: Int?,
    val itemFlag: Int?,
    val itemIcon: Int?
) : ScriptToken {

    companion object {
        fun read(reader: ByteReader): NewItem {
            val copyOf = ItemIndex(reader.readU16LE())
            val block = reader.readU16LE()
            val corner = reader.readU8()
            val flags = reader.readU8()

            return NewItem(
                copyOf = copyOf,
                goes = ItemDestination.of(block, corner),
                flags = flags,
                itemValue = if (flags and 1 == 1) reader.readU8() else null,
                itemFlag = if (flags and 2 == 2) reader.readU8() else null,
                itemIcon = if (flags and 4 == 4) reader.readU8() else null,
            )
        }
    }
}
