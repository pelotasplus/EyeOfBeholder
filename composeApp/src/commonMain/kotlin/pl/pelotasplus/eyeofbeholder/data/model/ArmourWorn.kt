package pl.pelotasplus.eyeofbeholder.data.model

/**
 * What a spell holds a champion's armour class at while it runs.
 *
 * A ceiling and not a bonus, which is the whole of how these spells behave:
 * one is worth nothing at all to somebody already better armoured, two of
 * them do not add, and taking plate off while one runs costs the wearer
 * nothing down to the ceiling.
 */
sealed interface ArmourHeldAt {

    /** A flat one, however nimble the champion is. */
    data class At(val armourClass: ArmorClass) : ArmourHeldAt

    /**
     * Their own nimbleness and [points] worse, which is what conjured armour
     * comes to: a suit of nothing, worn by somebody quick.
     *
     * [points] is a count and not an armour class of its own — there is no
     * armour class six, only six worse than however they stand.
     */
    data class OverTheirNimbleness(val points: Int) : ArmourHeldAt

    /**
     * What a vestment comes to: five, and a point better for every three
     * levels of cleric past the fifth.
     *
     * Better, and written that way on purpose. The number this is taken from
     * runs the other way and leaves a veteran vested worse than a novice —
     * worse, from the eighth level, than a first-level mage's shield — which
     * is a sign the wrong way round rather than a rule anybody meant. The
     * base of five and the step of three are the pen-and-paper spell's own;
     * only the direction differed, which is what makes it a slip and not a
     * choice. Turning it back would make the spell worth less the better its
     * caster gets.
     */
    data object AVestment : ArmourHeldAt

    /** What it holds [who] at. */
    fun forSomeone(who: Champion): ArmorClass = when (this) {
        is At -> armourClass
        is OverTheirNimbleness -> ArmorClass(who.nimbleness + points)
        AVestment -> ArmorClass(
            VESTED - ((who.praysAsSomeoneOfLevel - VESTED) / EVERY_THREE).coerceAtLeast(0),
        )
    }

    private companion object {
        const val VESTED = 5
        const val EVERY_THREE = 3
    }
}

/**
 * How practised a champion is at a cleric's prayers.
 *
 * A cleric's own level, or a paladin's less the eight they spend learning to
 * pray at all — and one for anybody else, so that a thing which asks never
 * has to deal with nobody.
 */
val Champion.praysAsSomeoneOfLevel: Int
    get() = levelIn(CharacterClass.CLERIC)
        ?: levelIn(CharacterClass.PALADIN)?.takeIf { it > A_PALADIN_PRAYS_FROM }?.minus(A_PALADIN_PRAYS_FROM)
        ?: 1

private const val A_PALADIN_PRAYS_FROM = 8

/**
 * What a champion's armour class comes to, from how nimble they are and what
 * they have on.
 *
 * Gear only. What a spell holds them at is a ceiling over this rather than a
 * part of it — see [GameState.armourClassOf] — and is kept out so that this
 * stays the thing recomputed when something is worn or taken off, and nothing
 * has to be recomputed when a spell begins or ends.
 *
 * A saved game carries a number, but that number is only the answer to this
 * as it stood when the game was saved, and a champion who takes a breastplate
 * off and keeps the old one is untouchable in a shirt.
 *
 * Ten is bare and unhurried; everything after that takes it down.
 */
fun ItemTypes.armourClassOf(champion: Champion, items: List<Item>): ArmorClass {
    val nimbleness = champion.nimbleness
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

/**
 * What being nimble is worth to this champion's armour class, which a spell
 * that armours them has to know as well as their gear does.
 */
val Champion.nimbleness: Int get() = dexterityIsWorth(abilities.dexterity.current)

/** As good as armour gets, which neither gear nor a spell goes under. */
const val THE_BEST_ARMOUR_CLASS_THERE_IS = -10

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
