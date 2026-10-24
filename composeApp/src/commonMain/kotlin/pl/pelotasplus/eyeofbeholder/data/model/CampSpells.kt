package pl.pelotasplus.eyeofbeholder.data.model

/** The three things the camp offers to do with spells. */
enum class CampSpellWork(val discipline: Discipline, val label: String) {
    MEMORIZE(Discipline.MAGE, "Memorize Spells"),
    PRAY(Discipline.CLERIC, "Pray for Spells"),
    SCRIBE(Discipline.MAGE, "Scribe Scrolls");

    /** Whether [champion] is of a class this is for at all, whatever state they are in. */
    fun isFor(champion: Champion): Boolean = when (this) {
        MEMORIZE, SCRIBE -> CharacterClass.MAGE in champion.countsAs
        PRAY -> CharacterClass.CLERIC in champion.countsAs || CharacterClass.PALADIN in champion.countsAs
    }
}

/** What asking who is to do it comes to. */
sealed interface WhoDoesIt {

    /** Nobody can, and this is said instead. */
    data class Refused(val says: List<String>) : WhoDoesIt

    /** One champion can, and is taken without asking. */
    data class Only(val whose: PartySlot) : WhoDoesIt

    /** Several can: the player clicks one of [able], the rest shown greyed. */
    data class Ask(val able: Set<PartySlot>, val says: List<String>) : WhoDoesIt
}

/**
 * Who is to memorize, pray or scribe.
 *
 * Only a champion of the right class, standing, and fed can. When nobody can,
 * what is said depends on whether there is anybody of the class at all — a
 * party with a mage lying unconscious is told something different from a
 * party with no mage. One who can is taken without the question, and several
 * are offered as portraits to click.
 */
object WhoDoesTheWork {

    fun asked(work: CampSpellWork, party: List<Champion>): WhoDoesIt {
        val ofTheClass = party.withIndex().filter { (_, it) -> it.inTheParty && work.isFor(it) }
        val able = able(work, party)

        return when (able.size) {
            0 -> WhoDoesIt.Refused(nobody(work, anybodyOfTheClass = ofTheClass.isNotEmpty()))
            1 -> {
                val only = able.single()
                if (tooLowAPaladin(party[only.index])) WhoDoesIt.Refused(CampSpellMessages.PALADIN_TOO_LOW)
                else WhoDoesIt.Only(only)
            }
            else -> WhoDoesIt.Ask(able.toSet(), asking(work))
        }
    }

    /**
     * What stops [champion], once clicked, from doing it after all — or none.
     *
     * Asked only of a champion picked from several: a paladin praying below
     * ninth level, and a mage with no spellbook about them. One taken without
     * the question has only the paladin's asked of them, so a lone mage with
     * no book is let into the menu, which only ever offers what their book
     * would hold anyway.
     */
    fun refusalOnceClicked(work: CampSpellWork, champion: Champion, carriesASpellbook: Boolean): List<String>? =
        when (work) {
            CampSpellWork.PRAY -> CampSpellMessages.PALADIN_TOO_LOW.takeIf { tooLowAPaladin(champion) }
            CampSpellWork.MEMORIZE, CampSpellWork.SCRIBE -> CampSpellMessages.NO_SPELLBOOK.takeIf { !carriesASpellbook }
        }

    /**
     * Whose names are checkered over while the work is asked about and done:
     * everyone in the party who cannot do it, even when only one can and is
     * taken without asking. When nobody can, nobody is.
     */
    fun greyed(work: CampSpellWork, party: List<Champion>): Set<PartySlot> {
        val able = able(work, party)
        if (able.isEmpty()) return emptySet()
        return party.indices.filter { party[it].inTheParty }.map(::PartySlot).toSet() - able.toSet()
    }

    private fun able(work: CampSpellWork, party: List<Champion>): List<PartySlot> =
        party.withIndex()
            .filter { (_, it) -> it.inTheParty && work.isFor(it) && it.canDoSpellWork }
            .map { PartySlot(it.index) }

    private val Champion.canDoSpellWork: Boolean
        get() = hitPoints.current > 0 && !petrified && food.value > 0

    private fun tooLowAPaladin(champion: Champion): Boolean {
        val paladin = champion.levelIn(CharacterClass.PALADIN) ?: return false
        return paladin < PALADIN_PRAYS_FROM
    }

    private fun nobody(work: CampSpellWork, anybodyOfTheClass: Boolean): List<String> = when (work) {
        CampSpellWork.MEMORIZE ->
            if (anybodyOfTheClass) CampSpellMessages.NOBODY_CAN_MEMORIZE else CampSpellMessages.NO_MAGE
        CampSpellWork.PRAY ->
            if (anybodyOfTheClass) CampSpellMessages.NOBODY_CAN_PRAY else CampSpellMessages.NO_CLERIC
        CampSpellWork.SCRIBE -> CampSpellMessages.NO_MAGE_TO_SCRIBE
    }

    private fun asking(work: CampSpellWork): List<String> = when (work) {
        CampSpellWork.MEMORIZE -> CampSpellMessages.WHO_MEMORIZES
        CampSpellWork.PRAY -> CampSpellMessages.WHO_PRAYS
        CampSpellWork.SCRIBE -> CampSpellMessages.WHO_SCRIBES
    }

    private const val PALADIN_PRAYS_FROM = 9
}

/** A mage scroll somewhere in the party, and where it is. */
data class AScroll(val spell: Spell, val carriedBy: PartySlot, val slot: CarrySlot)

/**
 * Scribing: a mage copying a scroll into their book.
 *
 * Every mage scroll anywhere in the party is looked for — the first of each
 * spell, a champion and a slot at a time — and the mage is offered the ones
 * their book lacks, six at most. Copying one writes it in their book and
 * uses the scroll up. Nothing can go wrong with it.
 */
object Scribing {

    fun scrollsIn(world: GameState, types: ItemTypes): List<AScroll> =
        (1..LAST_MAGE_SPELL).mapNotNull { number ->
            val spell = Discipline.MAGE.spellNumbered(number) ?: return@mapNotNull null
            world.champions.indices.firstNotNullOfOrNull { slot ->
                val who = world.champions[slot]
                if (!who.inTheParty) return@firstNotNullOfOrNull null
                CarrySlot.EVERY_ONE.firstOrNull { carry ->
                    val item = world.item(who.holding(carry)) ?: return@firstOrNull false
                    types.kindOf(item) == ItemKind.MAGE_SCROLL && item.value == number
                }?.let { AScroll(spell, PartySlot(slot), it) }
            }
        }

    /** The scrolls [mage] could copy, six at most, in the order of the list. */
    fun offeredTo(mage: Champion, scrolls: List<AScroll>): List<AScroll> =
        scrolls.filter { it.spell !in mage.knownMageSpells }.take(SHOWN)

    /** The world with [scroll] copied into [mage]'s book and gone from where it was. */
    fun copied(world: GameState, mage: PartySlot, scroll: AScroll, types: ItemTypes): GameState =
        world.withChampion(mage) { it.copy(knownMageSpells = it.knownMageSpells + scroll.spell) }
            .carrying(scroll.carriedBy, scroll.slot, ItemIndex(ItemIndex.NOTHING), types)

    const val SHOWN = 6

    private const val LAST_MAGE_SPELL = 32
}

/**
 * What the camp says about spells, as the game writes it: a list a line,
 * spaces where the game has them, and an empty line where it starts a line
 * early to push the words down.
 */
object CampSpellMessages {
    val WHO_MEMORIZES = listOf("", "", " Select a character", " from your party who", " would like to learn", " spells.")
    val WHO_PRAYS = listOf("", "", "", " Select a character", " from your party", " who would like to", " pray for spells.")
    val WHO_SCRIBES = listOf("", "", "", " Select a Mage", " from your party", " who would like to", " scribe spells.")

    val PALADIN_TOO_LOW = listOf("", " Your Paladin is ", " too low a level", " for spells.")
    val NO_SPELLBOOK = listOf("", "", " The Mage has no", " Spell Book!")

    val NOBODY_CAN_PRAY = listOf("", " No party members", " are capable of", " praying for new", " spells.")
    val NOBODY_CAN_MEMORIZE = listOf("", " No party members", " are capable of", " memorizing new", " spells.")
    val NO_MAGE = listOf(" ", " You do not have", " a mage in your", " party.")
    val NO_CLERIC = listOf(" ", " You do not have", " a cleric or", " paladin in your", " party.")

    /** The game's own spelling. */
    val NO_SCROLLS = listOf(" You don't have", " any scolls to be", " scribed.")
    val NO_SCROLLS_THIS_MAGE_NEEDS = listOf(" You don't have", " any scrolls that", " this Mage needs.")
    val NO_MAGE_TO_SCRIBE = listOf("", " You don't have", " any Mage able to", " scribe scrolls.")
    val CHOOSE_SCROLLS = listOf("Select the scroll(s)", "you wish to scribe.")

    val NEEDS_A_REST = listOf(" Your party needs", " to rest to gain", " spells.")

    const val SPELLS_AVAILABLE = "Spells Available:"
    const val CLEAR = "Clear"

    fun remaining(left: Int, of: Int) = "$left of $of Remaining.  "

    /** A spell's line: its name in eighteen, and how many are chosen. */
    fun line(name: String, chosen: Int) = "${name.padEnd(18)} $chosen"

    /**
     * What a spell is called on the menus, which is not what it is called when
     * cast: shorter, and capitalised. The game's own words, its misspelling
     * among them.
     */
    fun menuNameOf(spell: Spell, discipline: Discipline): String {
        val names = if (discipline == Discipline.MAGE) MAGE_MENU_NAMES else CLERIC_MENU_NAMES
        return names.getOrElse(discipline.numberOf(spell)) { spell.calledIt }
    }

    private val MAGE_MENU_NAMES = listOf(
        "", "Armor", "Burning Hands", "Detect Magic", "Magic Missile", "Shield",
        "Shocking Grasp", "Blur", "Detect Invisible", "Imp Identify", "Invisibility",
        "M's Acid Arrow", "Dispel Magic", "Fireball", "Haste", "Hold Person",
        "Invisibility 10'", "Lightning Bolt", "Vampiric Touch", "Fear", "Ice Storm",
        "Imp Invisibility", "Remove Curse", "Cone of Cold", "Hold Monster", "Wall of Force",
        "Disintegrate", "Flesh to Stone", "Stone to Flesh", "True Seeing", "Finger of Death",
        "Power Word Stun", "Bigby's Fist",
    )

    private val CLERIC_MENU_NAMES = listOf(
        "", "Bless", "Cause Light Wnds", "Cure Light Wnds", "Detect Magic", "Protect-Evil",
        "Aid", "Flame Blade", "Hold Person", "Slow Poison", "Create Food", "Dispel Magic",
        "Magical Vestment", "Prayer", "Remove Paralysis", "Cause Serious", "Cure Serious",
        "Neutral-Poison", "Protect-Evil 10'", "Cause Critical", "Cure Critical", "Flame Strike",
        "Raise Dead", "Slay Living", "True Seeing", "Harm", "Heal", "Ressurection",
        "Lay on Hands", "Turn undead",
    )
}
