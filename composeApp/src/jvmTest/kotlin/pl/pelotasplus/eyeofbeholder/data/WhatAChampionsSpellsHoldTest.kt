package pl.pelotasplus.eyeofbeholder.data

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.experimental.categories.Category
import pl.pelotasplus.eyeofbeholder.NeedsGameData
import pl.pelotasplus.eyeofbeholder.data.model.Abilities
import pl.pelotasplus.eyeofbeholder.data.model.Ability
import pl.pelotasplus.eyeofbeholder.data.model.ArmorClass
import pl.pelotasplus.eyeofbeholder.data.model.BookPosition
import pl.pelotasplus.eyeofbeholder.data.model.Champion
import pl.pelotasplus.eyeofbeholder.data.model.ChampionFlags
import pl.pelotasplus.eyeofbeholder.data.model.CharacterClass
import pl.pelotasplus.eyeofbeholder.data.model.ClassLevel
import pl.pelotasplus.eyeofbeholder.data.model.Discipline
import pl.pelotasplus.eyeofbeholder.data.model.Food
import pl.pelotasplus.eyeofbeholder.data.model.HitPoints
import pl.pelotasplus.eyeofbeholder.data.model.ItemIndex
import pl.pelotasplus.eyeofbeholder.data.model.PortraitId
import pl.pelotasplus.eyeofbeholder.data.model.Spell
import pl.pelotasplus.eyeofbeholder.data.model.SpellCapacity
import pl.pelotasplus.eyeofbeholder.data.model.SpellPages
import pl.pelotasplus.eyeofbeholder.data.model.SpellSlot
import pl.pelotasplus.eyeofbeholder.data.model.XpPoints
import pl.pelotasplus.eyeofbeholder.data.model.mageSpellsWrittenIn
import pl.pelotasplus.eyeofbeholder.data.repository.OriginalSaveRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.ResourceRepositoryImpl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A champion's spells: what a saved game writes for them, and how many of
 * each level they may have chosen.
 *
 * The expected numbers come from the save format's own rules and from named
 * rows of the game's tables — never from what the code reads back.
 */
@Category(NeedsGameData::class)
class WhatAChampionsSpellsHoldTest {

    // --- reading a page --------------------------------------------------------

    /**
     * A place holds a spell's number on its discipline's list: positive
     * learned, negative still to learn, nought empty.
     */
    @Test
    fun `a page is read place by place`() {
        val pages = SpellPages.read(listOf(3, -4, 0) + List(77) { 0 }, Discipline.MAGE)

        assertEquals(SpellSlot.Ready(Spell.DETECT_MAGIC), pages.slots[0])
        assertEquals(SpellSlot.Learning(Spell.MAGIC_MISSILE), pages.slots[1])
        assertEquals(SpellSlot.Empty, pages.slots[2])
    }

    /** The cleric's list follows the mage's: its twenty-ninth is turn undead. */
    @Test
    fun `a cleric's number counts from the end of the mage's list`() {
        val pages = SpellPages.read(listOf(29, -3, 28) + List(77) { 0 }, Discipline.CLERIC)

        assertEquals(SpellSlot.Ready(Spell.TURN_UNDEAD), pages.slots[0])
        assertEquals(SpellSlot.Learning(Spell.CURE_LIGHT_WOUNDS), pages.slots[1])
        assertEquals(SpellSlot.Ready(Spell.LAY_ON_HANDS), pages.slots[2])
    }

    /** A number that names nothing can only be damage, and reads as an empty place. */
    @Test
    fun `a number that names no spell is an empty place`() {
        val pages = SpellPages.read(listOf(120) + List(79) { 0 }, Discipline.MAGE)

        assertEquals(SpellSlot.Empty, pages.slots[0])
    }

    /** One bit a spell in the book, the lowest the first on the list. */
    @Test
    fun `a book is read one bit a spell`() {
        assertEquals(setOf(Spell.BURNING_HANDS, Spell.MAGIC_MISSILE), mageSpellsWrittenIn(0b1010L))
    }

    // --- the quick start party -------------------------------------------------

    private val quickStart = runBlocking {
        OriginalSaveRepositoryImpl(ResourceRepositoryImpl())
            .loadOriginalSave(OriginalSaveRepositoryImpl.QUICK_START)
            .getOrThrow()
    }

    private fun named(name: String) = quickStart.party.single { it.name == name }

    /**
     * Anybody who prays carries turn undead in the first place of their first
     * page, where the game puts it and from where it is never taken.
     */
    @Test
    fun `the cleric and the paladin carry turn undead first`() {
        listOf("WOLFSPIRIT", "PERICLES").forEach { name ->
            assertEquals(SpellSlot.Ready(Spell.TURN_UNDEAD), named(name).clericSpells.slots[0], name)
        }
    }

    /** The mage's book holds at least the one the game starts every new mage with. */
    @Test
    fun `the mage's book holds a new mage's book and more`() {
        val aNewMagesBook = mageSpellsWrittenIn(A_NEW_MAGES_BOOK)

        assertTrue(named("LAURANN").knownMageSpells.containsAll(aNewMagesBook))
    }

    // --- how many ----------------------------------------------------------------

    private fun champion(
        characterClass: CharacterClass,
        levels: List<Int>,
        wisdom: Int = 10,
        knows: Set<Spell> = emptySet(),
    ) = Champion(
        name = "Anselm",
        portrait = PortraitId(0),
        abilities = Abilities(wisdom = Ability(wisdom, wisdom)),
        hitPoints = HitPoints(20, 20),
        armorClass = ArmorClass(10),
        food = Food(100),
        characterClass = characterClass,
        levels = levels.map { ClassLevel(it, XpPoints(0)) },
        carrying = List(27) { ItemIndex(ItemIndex.NOTHING) },
        flags = ChampionFlags(1),
        knownMageSpells = knows,
    )

    /** The mage table's seventh row: four, three, two, one. */
    @Test
    fun `a seventh-level mage has the seventh row`() {
        assertEquals(
            listOf(4, 3, 2, 1, 0, 0, 0, 0),
            SpellCapacity.of(champion(CharacterClass.MAGE, listOf(7)), Discipline.MAGE),
        )
    }

    /** The ninth row is 4 3 3 2 1, and the ring doubles the fourth and fifth. */
    @Test
    fun `a ring of wizardry doubles the fourth and fifth levels`() {
        assertEquals(
            listOf(4, 3, 3, 4, 2, 0, 0, 0),
            SpellCapacity.of(champion(CharacterClass.MAGE, listOf(9)), Discipline.MAGE, wearingWizardry = true),
        )
    }

    /**
     * The cleric table's seventh row is 3 3 2 1, and wisdom eighteen adds the
     * bonus row 2 2 1 1 to it.
     */
    @Test
    fun `wisdom adds to a cleric's places`() {
        assertEquals(
            listOf(5, 5, 3, 2, 0, 0, 0, 0),
            SpellCapacity.of(champion(CharacterClass.CLERIC, listOf(7), wisdom = 18), Discipline.CLERIC),
        )
    }

    /** Only to a page that has places already: a first-level cleric gets no second-level ones. */
    @Test
    fun `wisdom adds nothing to a page that has no places`() {
        assertEquals(
            listOf(3, 0, 0, 0, 0, 0, 0, 0),
            SpellCapacity.of(champion(CharacterClass.CLERIC, listOf(1), wisdom = 18), Discipline.CLERIC),
        )
    }

    /** Twelve and below is no bonus; past nineteen is the last row there is. */
    @Test
    fun `wisdom is counted from thirteen and stops at the table's end`() {
        val cleric = { wisdom: Int -> SpellCapacity.of(champion(CharacterClass.CLERIC, listOf(7), wisdom = wisdom), Discipline.CLERIC) }

        assertEquals(listOf(3, 3, 2, 1, 0, 0, 0, 0), cleric(12))
        assertEquals(cleric(19), cleric(25))
    }

    /** A paladin prays from level nine; the quick start's sixth-level one has nothing. */
    @Test
    fun `a paladin's places start at ninth level`() {
        assertEquals(List(8) { 0 }, SpellCapacity.of(named("PERICLES"), Discipline.CLERIC))
        assertEquals(
            listOf(1, 0, 0, 0, 0, 0, 0, 0),
            SpellCapacity.of(champion(CharacterClass.PALADIN, listOf(9)), Discipline.CLERIC),
        )
    }

    /** A multi-class is counted by the level of the class that casts. */
    @Test
    fun `a fighter-mage is counted by the mage level`() {
        val both = champion(CharacterClass.FIGHTER_MAGE, levels = listOf(9, 4))

        assertEquals(4, both.levelIn(CharacterClass.MAGE))
        assertEquals(SpellCapacity.of(champion(CharacterClass.MAGE, listOf(4)), Discipline.MAGE), SpellCapacity.of(both, Discipline.MAGE))
    }

    // --- what may be chosen ----------------------------------------------------

    @Test
    fun `lay on hands is a paladin's and no cleric's`() {
        assertFalse(SpellCapacity.mayChoose(champion(CharacterClass.CLERIC, listOf(7)), Discipline.CLERIC, Spell.LAY_ON_HANDS))
        assertTrue(SpellCapacity.mayChoose(champion(CharacterClass.PALADIN, listOf(9)), Discipline.CLERIC, Spell.LAY_ON_HANDS))
    }

    /** Turn undead is always there and so never chosen. */
    @Test
    fun `turn undead is never chosen`() {
        assertFalse(SpellCapacity.mayChoose(champion(CharacterClass.CLERIC, listOf(7)), Discipline.CLERIC, Spell.TURN_UNDEAD))
    }

    /** A mage chooses only from their book. */
    @Test
    fun `a mage chooses only what is in their book`() {
        val mage = champion(CharacterClass.MAGE, listOf(7), knows = setOf(Spell.MAGIC_MISSILE))

        assertTrue(SpellCapacity.mayChoose(mage, Discipline.MAGE, Spell.MAGIC_MISSILE))
        assertFalse(SpellCapacity.mayChoose(mage, Discipline.MAGE, Spell.FIREBALL))
    }

    // --- our own saves ---------------------------------------------------------

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun `a champion's spells survive our own save`() {
        val learned = champion(CharacterClass.MAGE, listOf(7), knows = setOf(Spell.FIREBALL)).copy(
            mageSpells = SpellPages.read(listOf(4, -13) + List(78) { 0 }, Discipline.MAGE),
            bookLeftAt = BookPosition(page = 2, scrolledBy = 0, line = 1),
        )

        assertEquals(learned, json.decodeFromString(Champion.serializer(), json.encodeToString(Champion.serializer(), learned)))
    }

    /** A save written before champions had spells reads as one with none. */
    @Test
    fun `a save from before spells reads as a champion with none`() {
        val written = json.encodeToJsonElement(Champion.serializer(), champion(CharacterClass.MAGE, listOf(7))).jsonObject
        val older = JsonObject(written - setOf("mageSpells", "clericSpells", "knownMageSpells", "bookLeftAt"))

        val read = json.decodeFromJsonElement(Champion.serializer(), older)

        assertEquals(SpellPages.NONE, read.mageSpells)
        assertEquals(emptySet(), read.knownMageSpells)
    }

    private companion object {
        /** The book the game hands every new mage. */
        const val A_NEW_MAGES_BOOK = 0x81CB6L
    }
}
