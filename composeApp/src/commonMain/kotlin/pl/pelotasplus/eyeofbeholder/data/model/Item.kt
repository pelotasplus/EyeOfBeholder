package pl.pelotasplus.eyeofbeholder.data.model

import kotlinx.serialization.Serializable
import pl.pelotasplus.eyeofbeholder.data.ByteReader

/**
 * One item, wherever it happens to be: lying in a quadrant of a square, or in
 * one of a champion's slots.
 *
 * An item does not know who has it. Everything that can hold one — a
 * champion's [Champion.carrying], a square's floor — names it by its place in
 * the world's one table, as an [ItemIndex]. So the holder knows what it holds,
 * and the item only knows what it is.
 *
 * ## Binary layout, 12 bytes, the same record in ITEM.DAT and in a save
 * ```
 * 0  1  name while unidentified, an index into the name table
 * 1  1  name once identified
 * 2  1  flags; bit 7 is identified
 * 3  1  icon
 * 4  1  type
 * 5  1  where in its square
 * 6  2  the square it lies on, packed
 * 8  2  next in that square's chain
 * 10 2  previous in the chain
 * 12 1  which level it is on
 * 13 1  value
 * ```
 *
 * @property pos where in the square: 0-3 a floor quadrant, 8 a wall niche
 * @property value what the number counts depends on the type — a magical
 *   bonus, charges left, which door a key opens
 */
@Serializable
data class Item(
    val nameUnidentified: ItemNameId,
    val nameIdentified: ItemNameId,
    val flags: Int,
    val icon: ItemIconId,
    val type: ItemTypeId,
    val pos: Int,
    val location: Location,
    /**
     * The chain of items lying on one square, as the original keeps it.
     * Nothing here follows it — a square's items are found by asking every
     * item where it lies — so these are what was read and are not kept up.
     */
    val next: Int,
    val prev: Int,
    val level: Int,
    val value: Int,
) {
    /**
     * Whether this slot holds an item at all. A table has room for more items
     * than a game contains, and the spare slots are marked by being nowhere.
     */
    val exists: Boolean get() = location != NOWHERE

    companion object {
        /**
         * The square an item that is on none lies on: the packed word 0xFFFF,
         * which is what -1 comes to when read as a position.
         */
        val NOWHERE = Location(31, 2047)

        fun read(reader: ByteReader) = Item(
            nameUnidentified = ItemNameId(reader.readU8()),
            nameIdentified = ItemNameId(reader.readU8()),
            flags = reader.readU8(),
            icon = ItemIconId(reader.readI8()),
            type = ItemTypeId(reader.readI8()),
            pos = reader.readI8(),
            location = Location.read(reader),
            next = reader.readI16LE(),
            prev = reader.readI16LE(),
            level = reader.readU8(),
            value = reader.readI8(),
        )
    }
}

/**
 * Defines properties for an item type/category.
 *
 * @property invFlags Inventory slot flags (where item can be placed)
 * @property handFlags Hand/equipment flags
 * @property armorClass Armor class modifier
 * @property allowedClasses Bitmask of character classes that can use this item
 * @property requiredHands Number of hands required (1 or 2)
 * @property dmgNumDiceS Number of damage dice vs small creatures
 * @property dmgNumPipsS Damage die size vs small creatures (e.g., 6 for d6)
 * @property dmgIncS Damage bonus vs small creatures
 * @property dmgNumDiceL Number of damage dice vs large creatures
 * @property dmgNumPipsL Damage die size vs large creatures
 * @property dmgIncL Damage bonus vs large creatures
 * @property unk1 Unknown field
 * @property extraProperties Additional item properties flags
 */
data class ItemType(
    val invFlags: Int,
    val handFlags: Int,
    val armorClass: Int,
    val allowedClasses: Int,
    val requiredHands: Int,
    val dmgNumDiceS: Int,
    val dmgNumPipsS: Int,
    val dmgIncS: Int,
    val dmgNumDiceL: Int,
    val dmgNumPipsL: Int,
    val dmgIncL: Int,
    val unk1: Int,
    val extraProperties: Int
)
