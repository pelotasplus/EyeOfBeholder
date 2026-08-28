package pl.pelotasplus.eyeofbeholder.data.model

/**
 * What a champion's armour class comes to, from how nimble they are and what
 * they have on.
 *
 * It has to be worked out again every time anything is worn or taken off. A
 * saved game carries a number, but that number is only the answer to this as
 * it stood when the game was saved, and a champion who takes a breastplate off
 * and keeps the old one is untouchable in a shirt.
 *
 * Ten is bare and unhurried; everything after that takes it down.
 */
fun ItemTypes.armourClassOf(champion: Champion, items: List<Item>): ArmorClass {
    val nimbleness = dexterityIsWorth(champion.abilities.dexterity.current)
    var armour = UNARMOURED + nimbleness

    // A hand is worth armour only when it holds a shield. Anything else in it
    // is a weapon or a torch, and neither turns a blow.
    WORN_FOR_PROTECTION.forEach { slot ->
        val item = champion.worn(slot, items) ?: return@forEach
        val kind = this[item.type] ?: return@forEach

        if (!usableBy(champion, item)) return@forEach
        if (kind.extraProperties.kind != ItemKind.ARMOUR) return@forEach
        if (slot.isAHand && item.type.value !in SHIELDS) return@forEach

        armour += kind.armorClass
        armour -= item.value
    }

    // A ring is worth nothing over armour that is already enchanted, and two
    // of them are worth only the better.
    if ((champion.worn(CarrySlot.WORN_ARMOUR, items)?.value ?: 0) == 0) {
        armour -= CarrySlot.RINGS
            .mapNotNull { champion.worn(it, items) }
            .filter { kindOf(it) == ItemKind.ARMOUR }
            .maxOfOrNull { it.value }
            ?: 0
    }

    return ArmorClass(armour)
}

private fun Champion.worn(slot: CarrySlot, items: List<Item>): Item? =
    carrying.getOrNull(slot.index)
        ?.takeIf { it.isSomething }
        ?.let { items.getOrNull(it.value) }

/**
 * What being nimble is worth, by the game's own table. Clumsy makes a
 * champion easier to hit and quick makes them harder, and most of the range
 * in between is worth nothing at all.
 */
private fun dexterityIsWorth(dexterity: Int): Int =
    DEXTERITY.getOrElse(dexterity) { if (dexterity < 0) DEXTERITY.first() else DEXTERITY.last() }

private val DEXTERITY = listOf(
    5, 5, 5, 4, 3, 2, 1, 0, 0, 0, 0, 0, 0,
    0, 0, -1, -2, -3, -4, -4, -5, -5, -5, -6, -6,
)

/** A champion in nothing at all, standing still. */
private const val UNARMOURED = 10

/** The two kinds of thing a hand can hold that turn a blow. */
private val SHIELDS = listOf(27, 57)

/** Body, both hands and head — the four that are added up. */
private val WORN_FOR_PROTECTION = listOf(
    CarrySlot.WORN_ARMOUR,
    CarrySlot(0),
    CarrySlot(1),
    CarrySlot.WORN_HELMET,
)
