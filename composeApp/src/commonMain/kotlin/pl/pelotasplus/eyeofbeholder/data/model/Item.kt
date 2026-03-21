package pl.pelotasplus.eyeofbeholder.data.model

/**
 * Represents an individual item instance in the game.
 *
 * Items form a linked list via [next] and [prev] indices.
 *
 * @property nameUnidentified Index into unidentified item names table
 * @property nameIdentified Index into identified item names table
 * @property flags Item flags (cursed, identified, etc.)
 * @property icon Icon index for rendering
 * @property type Item type index (references [ItemType])
 * @property pos Position within container/inventory slot
 * @property location Block position in the level (-1 if not placed)
 * @property next Index of next item in linked list (-1 if none)
 * @property prev Index of previous item in linked list (-1 if none)
 * @property level Level/floor the item is on
 * @property value Item-specific value (charges, bonus, etc.)
 */
data class Item(
    val nameUnidentified: String = "",
    val nameIdentified: String = "",
    val location: Location,
    val level: Int,

    val nameUnidentifiedId: Int,
    val nameIdentifiedId: Int,
    val flags: Int,
    val icon: Int,
    val type: Int,
    val pos: Int,
    val next: Int,
    val prev: Int,
    val value: Int,
) {
    companion object {
        const val NO_BLOCK = -1
        const val NO_LINK = -1
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
