package pl.pelotasplus.eyeofbeholder.data.model.script

import pl.pelotasplus.eyeofbeholder.data.ByteReader
import pl.pelotasplus.eyeofbeholder.data.model.Location

/**
 * Removes an item from the game. Opcode 0xED.
 *
 * Used when a key is consumed by a lock, a quest item is turned in, or
 * a potion is drunk. Items can be removed from a character's hand or
 * from a specific maze location.
 */
sealed class ConsumeItem : ScriptToken {

    /** Delete item in hand. c = -1 */
    data object DeleteHandItem : ConsumeItem()

    /** Delete item(s) from a block. c != -1 */
    data class DeleteBlockItem(
        val itemType: Int,  // -1 if c was -2, otherwise c
        val location: Location
    ) : ConsumeItem()

    companion object {
        fun read(reader: ByteReader): ConsumeItem {
            val c = reader.readI8()

            return if (c == -1) {
                DeleteHandItem
            } else {
                val location = Location.read(reader)
                DeleteBlockItem(
                    itemType = if (c == -2) -1 else c,
                    location = location
                )
            }
        }
    }
}
