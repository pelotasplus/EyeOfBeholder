package pl.pelotasplus.eyeofbeholder.data.model.script

import pl.pelotasplus.eyeofbeholder.data.ByteReader
import pl.pelotasplus.eyeofbeholder.data.model.Item
import pl.pelotasplus.eyeofbeholder.data.model.ItemIconId
import pl.pelotasplus.eyeofbeholder.data.model.ItemIndex
import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.SquarePlace

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

    data class OnASquare(val at: Location, val place: SquarePlace) : ItemDestination

    companion object {
        /**
         * @param block the square as the script writes it, which is -1 for the
         *   hand and -2 for underfoot
         */
        fun of(block: Int, place: SquarePlace): ItemDestination = when (block) {
            THE_HAND -> IntoTheHand
            UNDERFOOT -> Underfoot
            else -> OnASquare(
                at = Location(block and MAZE_WIDTH_MASK, block / MAZE_WIDTH),
                place = place,
            )
        }

        private const val THE_HAND = 0xFFFF
        private const val UNDERFOOT = 0xFFFE
        private const val MAZE_WIDTH = 32
        private const val MAZE_WIDTH_MASK = 31
    }
}

/**
 * What a script says about the thing it makes that the thing it copied does
 * not already say. Each is sent only when the script has something to say
 * about it, and an absent one leaves what was copied standing.
 *
 * This is how one entry in the world's table serves as several things: every
 * key in the game is the same key until a script says which door it opens.
 */
data class ItemOverrides(
    val value: Int? = null,
    val flags: Int? = null,
    val icon: ItemIconId? = null,
) {
    fun applyTo(item: Item): Item = item.copy(
        value = value ?: item.value,
        flags = flags ?: item.flags,
        icon = icon ?: item.icon,
    )
}

/**
 * Makes a new thing and puts it somewhere. Opcode 0xEA.
 *
 * What is made is a copy of one of the things already in the world's table,
 * named by [copyOf] — a script does not describe a thing, it points at one
 * that already exists and asks for another like it, then says in [overrides]
 * how the copy differs.
 */
data class NewItem(
    val copyOf: ItemIndex,
    val goes: ItemDestination,
    val overrides: ItemOverrides,
) : ScriptToken {

    companion object {
        fun read(reader: ByteReader): NewItem {
            val copyOf = ItemIndex(reader.readU16LE())
            val block = reader.readU16LE()
            val place = SquarePlace.of(reader.readU8())
            val sends = reader.readU8()

            return NewItem(
                copyOf = copyOf,
                goes = ItemDestination.of(block, place),
                // read in the order they are written, so each byte is the one
                // its bit claims
                overrides = ItemOverrides(
                    value = if (sends and SENDS_VALUE != 0) reader.readU8() else null,
                    flags = if (sends and SENDS_FLAGS != 0) reader.readU8() else null,
                    icon = if (sends and SENDS_ICON != 0) ItemIconId(reader.readU8()) else null,
                ),
            )
        }

        private const val SENDS_VALUE = 1
        private const val SENDS_FLAGS = 2
        private const val SENDS_ICON = 4
    }
}
