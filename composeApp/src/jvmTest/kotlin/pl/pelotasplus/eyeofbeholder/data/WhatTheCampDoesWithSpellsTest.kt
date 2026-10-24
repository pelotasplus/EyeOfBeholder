package pl.pelotasplus.eyeofbeholder.data

import kotlinx.coroutines.runBlocking
import org.junit.experimental.categories.Category
import pl.pelotasplus.eyeofbeholder.NeedsGameData
import pl.pelotasplus.eyeofbeholder.data.model.CampSpellMessages
import pl.pelotasplus.eyeofbeholder.data.model.CampSpellWork
import pl.pelotasplus.eyeofbeholder.data.model.CarrySlot
import pl.pelotasplus.eyeofbeholder.data.model.Champion
import pl.pelotasplus.eyeofbeholder.data.model.Discipline
import pl.pelotasplus.eyeofbeholder.data.model.GameState
import pl.pelotasplus.eyeofbeholder.data.model.HitPoints
import pl.pelotasplus.eyeofbeholder.data.model.ItemIndex
import pl.pelotasplus.eyeofbeholder.data.model.ItemKind
import pl.pelotasplus.eyeofbeholder.data.model.ItemTypes
import pl.pelotasplus.eyeofbeholder.data.model.PartySlot
import pl.pelotasplus.eyeofbeholder.data.model.Scribing
import pl.pelotasplus.eyeofbeholder.data.model.Spell
import pl.pelotasplus.eyeofbeholder.data.model.SpellChoosing
import pl.pelotasplus.eyeofbeholder.data.model.SpellPages
import pl.pelotasplus.eyeofbeholder.data.model.SpellSlot
import pl.pelotasplus.eyeofbeholder.data.model.WhoDoesIt
import pl.pelotasplus.eyeofbeholder.data.model.WhoDoesTheWork
import pl.pelotasplus.eyeofbeholder.data.repository.ItemTypesRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.OriginalSaveRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.ResourceRepositoryImpl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Memorize, Pray and Scribe, on the quick start party: Pericles a sixth-level
 * paladin, Stumpy a fighter/thief, Wolfspirit a seventh-level cleric of wisdom
 * eighteen, Laurann a seventh-level mage.
 *
 * What is offered and how much room there is are worked out from the game's
 * tables and from what Laurann's book holds — not from what the code shows.
 */
@Category(NeedsGameData::class)
class WhatTheCampDoesWithSpellsTest {

    private val resources = ResourceRepositoryImpl()

    private val save = runBlocking {
        OriginalSaveRepositoryImpl(resources).loadOriginalSave(OriginalSaveRepositoryImpl.QUICK_START).getOrThrow()
    }

    private val types: ItemTypes = runBlocking { ItemTypesRepositoryImpl(resources).loadItemTypes().getOrThrow() }

    private val world get() = GameState(party = save.standing, champions = save.party, items = save.items)

    private val pericles = PartySlot(0)
    private val wolfspirit = PartySlot(2)
    private val laurann = PartySlot(3)

    private fun champion(slot: PartySlot) = save.party[slot.index]

    private fun memorizing(who: Champion = champion(laurann)) =
        SpellChoosing.opening(who, laurann, Discipline.MAGE)

    // --- memorizing --------------------------------------------------------------

    /**
     * Her book's first-level spells in list order — burning hands, detect
     * magic, magic missile, shield, shocking grasp — and the seventh row of the
     * mage table for room: four, three, two, one.
     */
    @Test
    fun `a mage is offered her book, a level a page`() {
        val menu = memorizing()

        assertEquals(listOf(4, 3, 2, 1, 0, 0, 0, 0), menu.places)
        assertEquals(
            listOf(Spell.BURNING_HANDS, Spell.DETECT_MAGIC, Spell.MAGIC_MISSILE, Spell.SHIELD, Spell.SHOCKING_GRASP),
            menu.offered[0],
        )
        assertEquals(listOf(Spell.DETECT_INVISIBILITY, Spell.MELFS_ACID_ARROW), menu.offered[1])
    }

    /** "4 of 4 Remaining." until something is chosen, and no more than four. */
    @Test
    fun `no more can be chosen than a page has room for`() {
        var menu = memorizing().onLine(2)
        repeat(6) { menu = menu.added() }

        assertEquals(4, menu.countOf(Spell.MAGIC_MISSILE))
        assertEquals(0, menu.remaining())
    }

    @Test
    fun `clearing empties only the page it is on`() {
        val menu = memorizing().onLine(2).added().onPage(1).added().onPage(0).cleared()

        assertEquals(0, menu.countOf(Spell.MAGIC_MISSILE))
        assertEquals(1, menu.countOf(Spell.DETECT_INVISIBILITY))
    }

    /**
     * Written out in the order of the list, each in the first free place, and
     * all of it still to learn: nothing is learned in camp.
     */
    @Test
    fun `leaving writes the page out, all of it still to learn`() {
        val menu = memorizing().onLine(2).added().added().onLine(0).added()

        assertEquals(
            listOf(
                SpellSlot.Learning(Spell.BURNING_HANDS),
                SpellSlot.Learning(Spell.MAGIC_MISSILE),
                SpellSlot.Learning(Spell.MAGIC_MISSILE),
            ) + List(7) { SpellSlot.Empty },
            menu.chosenPages().page(0),
        )
        assertTrue(menu.needsARest)
    }

    /**
     * What was learned stays learned. Two magic missiles held, one taken off
     * and two put on: two learned and one still to learn.
     */
    @Test
    fun `what was learned stays learned`() {
        val holding = champion(laurann).copy(
            mageSpells = SpellPages.read(listOf(4, 4) + List(78) { 0 }, Discipline.MAGE),
        )

        val menu = memorizing(holding).onLine(2).takenOff().added().added()

        assertEquals(
            listOf(
                SpellSlot.Ready(Spell.MAGIC_MISSILE),
                SpellSlot.Ready(Spell.MAGIC_MISSILE),
                SpellSlot.Learning(Spell.MAGIC_MISSILE),
            ) + List(7) { SpellSlot.Empty },
            menu.chosenPages().page(0),
        )
    }

    /**
     * Opening and leaving keeps what was held. Whether a rest is wanted
     * afterwards is whether anything is still to learn — a spell chosen
     * earlier and not learned yet counts, since only a rest will teach it.
     */
    @Test
    fun `leaving unchanged keeps what was held, and wants a rest only for what is unlearned`() {
        val allLearned = champion(laurann).copy(
            mageSpells = SpellPages.read(listOf(4, 2) + List(78) { 0 }, Discipline.MAGE),
        )
        val oneToLearn = champion(laurann).copy(
            mageSpells = SpellPages.read(listOf(4, -2) + List(78) { 0 }, Discipline.MAGE),
        )

        assertEquals(allLearned.mageSpells.page(0).toSet(), memorizing(allLearned).chosenPages().page(0).toSet())
        assertFalse(memorizing(allLearned).needsARest)
        assertTrue(memorizing(oneToLearn).needsARest)
    }

    /** A spell held and no longer offered is kept, not thrown away. */
    @Test
    fun `a spell held but not offered is kept`() {
        val holding = champion(laurann).copy(
            mageSpells = SpellPages.read(List(20) { 0 } + listOf(13) + List(59) { 0 }, Discipline.MAGE)
                .let { pages -> pages.copy(slots = pages.slots.toMutableList().also { it[20] = SpellSlot.Ready(Spell.FIREBALL) }) },
            knownMageSpells = champion(laurann).knownMageSpells - Spell.FIREBALL,
        )

        assertEquals(SpellSlot.Ready(Spell.FIREBALL), memorizing(holding).chosenPages().page(2)[0])
    }

    // --- praying -----------------------------------------------------------------

    /**
     * The seventh row of the cleric table, 3 3 2 1, and wisdom eighteen's
     * bonus, 2 2 1 1, on top.
     */
    @Test
    fun `a cleric is offered her pages with her wisdom's places`() {
        val menu = SpellChoosing.opening(champion(wolfspirit), wolfspirit, Discipline.CLERIC)

        assertEquals(listOf(5, 5, 3, 2, 0, 0, 0, 0), menu.places)
        assertTrue(menu.offered.flatten().none { it == Spell.TURN_UNDEAD || it == Spell.LAY_ON_HANDS })
        assertTrue(menu.offered.drop(4).all { it.isEmpty() }, "a page with no places offered something")
    }

    /** Turn undead stays first, whatever was chosen. */
    @Test
    fun `turn undead stays in a cleric's first place`() {
        val menu = SpellChoosing.opening(champion(wolfspirit), wolfspirit, Discipline.CLERIC).added()

        assertEquals(SpellSlot.Ready(Spell.TURN_UNDEAD), menu.chosenPages().slots[0])
    }

    // --- who --------------------------------------------------------------------

    /** One mage in the party, taken without the question. */
    @Test
    fun `the only mage is taken without asking`() {
        assertEquals(WhoDoesIt.Only(laurann), WhoDoesTheWork.asked(CampSpellWork.MEMORIZE, save.party))
        assertEquals(WhoDoesIt.Only(laurann), WhoDoesTheWork.asked(CampSpellWork.SCRIBE, save.party))
    }

    /** Taken without asking, and everybody else still checkered over. */
    @Test
    fun `the others are greyed even when nobody is asked`() {
        assertEquals(
            setOf(pericles, PartySlot(1), wolfspirit),
            WhoDoesTheWork.greyed(CampSpellWork.MEMORIZE, save.party),
        )
        assertEquals(setOf(PartySlot(1), laurann), WhoDoesTheWork.greyed(CampSpellWork.PRAY, save.party))
    }

    /** When nobody can, nobody is greyed: the camp only says why. */
    @Test
    fun `nobody is greyed when nobody can`() {
        val noMage = save.party.mapIndexed { slot, it -> if (slot == laurann.index) Champion.NOBODY else it }

        assertEquals(emptySet(), WhoDoesTheWork.greyed(CampSpellWork.MEMORIZE, noMage))
    }

    /**
     * A cleric and a paladin can both pray, so the player is asked. Picking the
     * sixth-level paladin is refused once picked.
     */
    @Test
    fun `a cleric and a paladin are both offered, and the young paladin refused`() {
        val asked = assertIs<WhoDoesIt.Ask>(WhoDoesTheWork.asked(CampSpellWork.PRAY, save.party))

        assertEquals(setOf(pericles, wolfspirit), asked.able)
        assertEquals(
            CampSpellMessages.PALADIN_TOO_LOW,
            WhoDoesTheWork.refusalOnceClicked(CampSpellWork.PRAY, champion(pericles), carriesASpellbook = false),
        )
        assertNull(WhoDoesTheWork.refusalOnceClicked(CampSpellWork.PRAY, champion(wolfspirit), carriesASpellbook = false))
    }

    /** A party with no mage are told so; one whose mage is down, that nobody can. */
    @Test
    fun `nobody to memorize is told why`() {
        val noMage = save.party.mapIndexed { slot, it -> if (slot == laurann.index) Champion.NOBODY else it }
        val mageDown = save.party.mapIndexed { slot, it ->
            if (slot == laurann.index) it.copy(hitPoints = HitPoints(0, it.hitPoints.max)) else it
        }

        assertEquals(WhoDoesIt.Refused(CampSpellMessages.NO_MAGE), WhoDoesTheWork.asked(CampSpellWork.MEMORIZE, noMage))
        assertEquals(
            WhoDoesIt.Refused(CampSpellMessages.NOBODY_CAN_MEMORIZE),
            WhoDoesTheWork.asked(CampSpellWork.MEMORIZE, mageDown),
        )
    }

    /**
     * A paladin alone, too young, is refused without the question — but as
     * one who could, so the fighter and the mage are greyed behind it.
     */
    @Test
    fun `a lone young paladin is refused straight away`() {
        val onlyThePaladin = save.party.mapIndexed { slot, it -> if (slot == wolfspirit.index) Champion.NOBODY else it }

        assertEquals(
            WhoDoesIt.Refused(CampSpellMessages.PALADIN_TOO_LOW),
            WhoDoesTheWork.asked(CampSpellWork.PRAY, onlyThePaladin),
        )
        assertEquals(setOf(PartySlot(1), laurann), WhoDoesTheWork.greyed(CampSpellWork.PRAY, onlyThePaladin))
    }

    // --- scribing ---------------------------------------------------------------

    /** A mage scroll for a spell not in her book, put in Stumpy's pack. */
    private fun withAScroll(): Pair<GameState, Spell> {
        val (index, scroll) = save.items.withIndex().first { (_, item) ->
            item.exists && types.kindOf(item) == ItemKind.MAGE_SCROLL &&
                Discipline.MAGE.spellNumbered(item.value)?.let { it !in champion(laurann).knownMageSpells } == true
        }
        val spell = assertNotNull(Discipline.MAGE.spellNumbered(scroll.value))
        return world.carrying(PartySlot(1), CarrySlot(IN_THE_PACK), ItemIndex(index)) to spell
    }

    @Test
    fun `a scroll anywhere in the party is offered to the mage`() {
        val (world, spell) = withAScroll()

        val scrolls = Scribing.scrollsIn(world, types)

        val found = assertNotNull(scrolls.firstOrNull { it.spell == spell })
        assertEquals(PartySlot(1), found.carriedBy)
        assertTrue(Scribing.offeredTo(champion(laurann), scrolls).any { it.spell == spell })
    }

    /** One she already has is not offered. */
    @Test
    fun `a scroll the mage already has is not offered`() {
        val (world, spell) = withAScroll()
        val knowing = champion(laurann).copy(knownMageSpells = champion(laurann).knownMageSpells + spell)

        assertTrue(Scribing.offeredTo(knowing, Scribing.scrollsIn(world, types)).none { it.spell == spell })
    }

    /** Copied: in her book, and gone from Stumpy's pack. */
    @Test
    fun `copying writes it in the book and uses the scroll up`() {
        val (world, spell) = withAScroll()
        val scroll = Scribing.scrollsIn(world, types).first { it.spell == spell }

        val after = Scribing.copied(world, laurann, scroll, types)

        assertTrue(spell in after.champions[laurann.index].knownMageSpells)
        assertFalse(after.champions[1].holding(CarrySlot(IN_THE_PACK)).isSomething, "the scroll is still there")
    }

    private companion object {
        /** A slot that is neither hand nor finger. */
        const val IN_THE_PACK = 10
    }
}
