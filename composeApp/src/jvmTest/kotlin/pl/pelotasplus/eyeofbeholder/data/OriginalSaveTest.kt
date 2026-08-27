package pl.pelotasplus.eyeofbeholder.data

import kotlinx.coroutines.runBlocking
import pl.pelotasplus.eyeofbeholder.data.model.Champion
import pl.pelotasplus.eyeofbeholder.data.model.CharacterClass
import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.HitPoints
import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.speakerFrom
import pl.pelotasplus.eyeofbeholder.data.model.spokenBy
import pl.pelotasplus.eyeofbeholder.data.repository.ResourceRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.OriginalSaveRepositoryImpl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The party the game ships with, read back out of the file it ships in.
 *
 * The values asserted here are the ones the original game shows for the quick
 * start party — four champions of the six slots, their names, portraits and
 * hit points — not whatever this parser happened to produce first. A record is
 * 345 bytes of fixed layout, and getting one field's width wrong slides
 * everything after it, so the later fields are what actually prove the parse.
 */
class OriginalSaveTest {

    private val quickStart = runBlocking {
        OriginalSaveRepositoryImpl(ResourceRepositoryImpl())
            .loadOriginalSave(OriginalSaveRepositoryImpl.QUICK_START)
            .getOrThrow()
    }

    @Test
    fun `the file says what it is`() {
        assertEquals("QUICK START PARTY", quickStart.description)
    }

    @Test
    fun `six slots, four of them filled`() {
        assertEquals(6, quickStart.party.size, "the panel draws a box for every slot")
        assertEquals(4, quickStart.champions.size)

        assertTrue(quickStart.party.take(4).all { it.inTheParty })
        assertFalse(quickStart.party.drop(4).any { it.inTheParty })
    }

    @Test
    fun `the champions are who the game says they are`() {
        assertEquals(
            listOf("PERICLES", "\"STUMPY\"", "WOLFSPIRIT", "LAURANN"),
            quickStart.champions.map { it.name },
        )
    }

    @Test
    fun `each champion is as hurt and as armoured as the file has them`() {
        assertEquals(listOf(78, 78, 70, 28), quickStart.champions.map { it.hitPoints.current })
        assertEquals(listOf(78, 78, 70, 28), quickStart.champions.map { it.hitPoints.max })
        assertEquals(listOf(1, 0, 1, 6), quickStart.champions.map { it.armorClass.value })
        assertTrue(quickStart.champions.none { it.dead })
    }

    /**
     * The last fields of the record. Getting these right means every width
     * before them was right too, which is what the whole parse rests on.
     */
    @Test
    fun `portraits, food and classes land where they should`() {
        assertEquals(listOf(2, 24, 0, 29), quickStart.champions.map { it.portrait.value })
        assertTrue(quickStart.champions.all { it.food.value == 99 }, "nobody has eaten yet")
        assertEquals(
            listOf(
                CharacterClass.PALADIN,
                CharacterClass.FIGHTER_THIEF,
                CharacterClass.CLERIC,
                CharacterClass.MAGE,
            ),
            quickStart.champions.map { it.characterClass },
        )
    }

    @Test
    fun `a multi-class champion has a level in each of their classes`() {
        val stumpy = quickStart.champions[1]
        val pericles = quickStart.champions[0]

        assertEquals(listOf(6, 6), stumpy.levels.map { it.level }, "a fighter-thief")
        assertEquals(listOf(6), pericles.levels.map { it.level }, "one class only")
        assertEquals(listOf(34500L, 34500L), stumpy.levels.map { it.experience.count })
        assertEquals(listOf(69000L), pericles.levels.map { it.experience.count })
    }

    @Test
    fun `everyone carries twenty-seven slots, most of them empty`() {
        quickStart.champions.forEach { champion ->
            assertEquals(27, champion.carrying.size, champion.name)
        }
        assertTrue(
            quickStart.champions.any { champion -> champion.carrying.any { it.isSomething } },
            "an armed party carries something",
        )
    }

    /**
     * A message names whoever the roll lands on, and walks forward past the
     * slots nobody fills. The expected names come from the file's own order:
     * slots 4 and 5 are empty, so a roll into either wraps round to PERICLES.
     */
    @Test
    fun `a message is spoken by whoever the roll lands on`() {
        assertEquals(
            listOf("PERICLES", "\"STUMPY\"", "WOLFSPIRIT", "LAURANN", "PERICLES", "PERICLES"),
            (0 until 6).map { quickStart.party.speakerFrom(it)?.name },
        )
    }

    @Test
    fun `nobody who can speak means nobody is named`() {
        val gone = quickStart.party.map { Champion.NOBODY }

        assertNull(gone.speakerFrom(0))
        assertEquals(
            " says nothing",
            "%s says nothing".spokenBy(gone.speakerFrom(0)),
            "the gap closes rather than showing the placeholder",
        )
    }

    /** Dying does not silence a champion; dying for good does. */
    @Test
    fun `a champion knocked out can still speak, one dead for good cannot`() {
        val pericles = quickStart.champions.first()

        assertTrue(pericles.copy(hitPoints = HitPoints(0, 78)).canSpeak)
        assertTrue(pericles.copy(hitPoints = HitPoints(-9, 78)).canSpeak)
        assertFalse(pericles.copy(hitPoints = HitPoints(-10, 78)).canSpeak)
    }

    /**
     * Reading past the party lands on the position, so this is really a check
     * that the six records are exactly 345 bytes each.
     */
    @Test
    fun `the party are standing where the save left them`() {
        assertEquals(4, quickStart.level)
        assertEquals(0, quickStart.subLevel)
        assertEquals(Location(11, 5), quickStart.standing.position)
        assertEquals(Direction.SOUTH, quickStart.standing.facing)
    }
}
