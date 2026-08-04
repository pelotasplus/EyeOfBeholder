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
 * 2  1  flags; 0x20 stuck to its slot, 0x40 identified, 0x80 magical
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

    /** A cursed thing cannot be taken out of the slot it was put in. */
    val stuckToItsSlot: Boolean get() = flags and STUCK != 0

    /** Whether the party know what this really is, and so what to call it. */
    val identified: Boolean get() = flags and IDENTIFIED != 0

    companion object {
        /**
         * The square an item that is on none lies on: the packed word 0xFFFF,
         * which is what -1 comes to when read as a position.
         */
        val NOWHERE = Location(31, 2047)

        /**
         * Where a thing on a stack lies: not on the map and not in a slot of
         * its own, but strung onto the one thing the slot names.
         */
        val ON_A_STACK = Location(30, 2047)

        /** A stacked thing is on no level either, which is written this way. */
        const val NO_LEVEL = 255

        /**
         * Where a thing being carried lies. It is square zero of level zero,
         * which is a real square that nothing is ever drawn on — the levels
         * are numbered from one.
         */
        val CARRIED = Location(0, 0)
        const val CARRIED_LEVEL = 0

        private const val STUCK = 0x20
        private const val IDENTIFIED = 0x40

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
 * The places a thing can be put. Every kind of item names the ones it fits,
 * in these bits, and every slot on a champion's page takes one of them.
 *
 * What each one is was read off the items that fit it: [QUIVER] takes arrows
 * and nothing else, [ARMOUR] takes leather and plate and robes and cloaks,
 * [BRACERS] takes bracers, [POUCH] takes the small carryable things — a
 * dagger, a scroll, a potion, a key.
 */
enum class ItemFits(val bit: Int) {
    QUIVER(0x0001),
    ARMOUR(0x0002),
    BRACERS(0x0004),
    HAND(0x0008),
    BOOTS(0x0010),
    HELMET(0x0020),
    NECKLACE(0x0040),
    POUCH(0x0080),
    RING(0x0100),
}

/** What one slot will take: one kind of place, or anything at all. */
sealed interface SlotTakes {
    /** A pocket of the pack, which is what a pack is for. */
    data object Anything : SlotTakes

    data class Only(val fits: ItemFits) : SlotTakes

    /** @param kinds the places an item's own kind says it fits. */
    fun accepts(kinds: Int): Boolean = when (this) {
        Anything -> kinds != NOWHERE_AT_ALL
        is Only -> kinds and fits.bit != 0
    }

    private companion object {
        /** A kind that fits nowhere fits nowhere, a pocket included. */
        const val NOWHERE_AT_ALL = 0
    }
}

/**
 * What each kind of item is, from ITEMTYPE.DAT, which is what says who may
 * hold one and whether it takes both hands.
 */
data class ItemTypes(private val types: List<ItemType>) {
    operator fun get(id: ItemTypeId): ItemType? = types.getOrNull(id.value)

    /**
     * Whether a champion may strike with what is in [hand], the other hand
     * being part of the answer.
     *
     * Three things stop them, and the original draws the same grid over the
     * slot for all three. The item may be for a class they are not — a
     * spellbook in a fighter's hand, thieves' tools in anyone else's. The
     * other hand may be holding something that wants both. And a weapon that
     * wants both hands cannot be wielded from the shield hand at all.
     *
     * @param held what a slot of the world's item table holds
     */
    fun canStrikeWith(champion: Champion, hand: Int, held: (ItemIndex) -> Item?): Boolean {
        val first = champion.carrying.getOrNull(0)?.let(held)

        if (hand == FIRST_HAND) return allows(champion, first)

        // whatever the second hand holds, a two-handed weapon in the first
        // leaves nothing for it to be held with
        if (first != null && this[first.type]?.requiredHands == BOTH_HANDS) return false

        val second = champion.carrying.getOrNull(1)?.let(held) ?: return true

        val kind = (this[second.type]?.extraProperties ?: 0) and KIND
        val hands = this[second.type]?.requiredHands ?: 0

        // a weapon in the shield hand must be one that asks for no hand in
        // particular; anything that is not a weapon is only a question of class
        if (kind in WIELDED && hands != 0) return false

        return allows(champion, second)
    }

    /**
     * Whether [slot] of [champion] will take what is being held and give up
     * [inSlot], which is what one click on it does.
     *
     * A stack is not a swap, so the quiver refuses until the chain of arrows
     * in it is modelled. What a curse has stuck to a hand stays there — only
     * the hands hold a thing that way. And armour is the one slot that asks
     * whether the champion is of a class that may wear what is offered.
     */
    fun willSwap(
        champion: Champion,
        slot: InventorySlot,
        held: Item?,
        inSlot: Item?,
    ): Boolean = when {
        slot.isQuiver -> false
        slot.slot.isAHand && inSlot?.stuckToItsSlot == true -> false
        slot.slot == CarrySlot.WORN_ARMOUR && !usableBy(champion, held) -> false
        else -> mayGoIn(slot.takes, held)
    }

    /**
     * Whether what is being held may go into a slot that [takes] these kinds
     * of thing.
     *
     * An empty hand fits everywhere: putting nothing anywhere is taking, not
     * putting, and there is nothing to refuse.
     */
    fun mayGoIn(takes: SlotTakes, held: Item?): Boolean {
        if (held == null) return true
        return takes.accepts(this[held.type]?.invFlags ?: return false)
    }

    /** Whether an item is for a champion of this class at all. */
    fun usableBy(champion: Champion, item: Item?) = allows(champion, item)

    /** Nothing in a hand is something anyone may do. */
    private fun allows(champion: Champion, item: Item?): Boolean {
        if (item == null) return true
        val allowed = this[item.type]?.allowedClasses ?: return false
        return champion.countsAs.any { it in CharacterClass.setOf(allowed) }
    }

    private companion object {
        const val FIRST_HAND = 0
        const val BOTH_HANDS = 2

        /** The low seven bits of an item's extra properties say what kind it is. */
        const val KIND = 0x7F

        /** The kinds that count as being wielded rather than merely carried. */
        val WIELDED = 1..3
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
