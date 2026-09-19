package pl.pelotasplus.eyeofbeholder.data

import pl.pelotasplus.eyeofbeholder.data.model.PartySlot
import pl.pelotasplus.eyeofbeholder.data.model.Spell
import pl.pelotasplus.eyeofbeholder.data.model.SpellLasts
import pl.pelotasplus.eyeofbeholder.data.model.SpellsRunning
import pl.pelotasplus.eyeofbeholder.data.model.Ticks
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Spells that go on after the words are said.
 *
 * The durations are the game's own and are not worked out here: a spell lasts
 * so many half-minutes flat plus so many for every level the caster has, and
 * the two numbers come from the table the game keeps. What is tested is that
 * the arithmetic joining them is the game's arithmetic, because a duration
 * out by a factor of the caster's level looks entirely ordinary on screen.
 */
class WhatKeepsRunningAfterACastTest {

    private val caster = PartySlot(2)

    /**
     * Half a minute at fifty-five milliseconds a tick, which is the unit
     * every duration in the game is counted in.
     */
    @Test
    fun `a half-minute is 546 ticks and is thirty seconds`() {
        assertEquals(546, SpellLasts.HALF_A_MINUTE)
        assertEquals(30_030, Ticks(546).inMilliseconds)
    }

    /** A flat duration ignores the caster: a fifth-level reader gets no more. */
    @Test
    fun `a flat duration is the same however practised the caster`() {
        val flat = SpellLasts(base = 1, perLevel = 0)

        assertEquals(Ticks(546), flat.castBySomeoneOfLevel(1))
        assertEquals(Ticks(546), flat.castBySomeoneOfLevel(9))
    }

    /** And one that grows counts only the levels, with no flat part at all. */
    @Test
    fun `a duration per level counts the levels and nothing flat`() {
        val grows = SpellLasts(base = 0, perLevel = 2)

        assertEquals(Ticks(1092), grows.castBySomeoneOfLevel(1))
        assertEquals(Ticks(9828), grows.castBySomeoneOfLevel(9))
    }

    /**
     * Mystic defence is flat at one half-minute — the wand shields the party
     * for thirty seconds whoever points it.
     */
    @Test
    fun `mystic defence lasts half a minute whoever casts it`() {
        val lasts = Spell.MYSTIC_DEFENCE.lasts
        assertEquals(SpellLasts(base = 1, perLevel = 0), lasts)
        assertEquals(Ticks(546), lasts?.castBySomeoneOfLevel(9))
    }

    /**
     * Detect magic is a minute for every level of its caster, and nothing
     * flat — so read off a scroll, which is always ninth-level strength, it
     * runs for nine minutes.
     */
    @Test
    fun `detect magic lasts a minute for every level of its caster`() {
        val lasts = Spell.DETECT_MAGIC.lasts
        assertEquals(SpellLasts(base = 0, perLevel = 2), lasts)
        assertEquals(Ticks(1092), lasts?.castBySomeoneOfLevel(1))
        assertEquals(Ticks(9828), lasts?.castBySomeoneOfLevel(9))
    }

    /** The cleric's detect magic is the mage's, and lasts exactly as long. */
    @Test
    fun `a cleric's detect magic runs as long as a mage's`() {
        assertEquals(Spell.DETECT_MAGIC.lasts, Spell.A_CLERICS_DETECT_MAGIC.lasts)
    }

    @Test
    fun `nothing is running over a party that has cast nothing`() {
        val nothing = SpellsRunning()

        assertFalse(nothing.isRunning(Spell.DETECT_MAGIC))
        assertNull(nothing[Spell.DETECT_MAGIC])
    }

    @Test
    fun `a cast puts the spell on the party for its whole duration`() {
        val running = assertNotNull(
            SpellsRunning().begun(Spell.DETECT_MAGIC, caster, casterLevel = 9)
        )

        assertTrue(running.isRunning(Spell.DETECT_MAGIC))
        assertEquals(9828, running[Spell.DETECT_MAGIC]?.ticksLeft)
        assertEquals(caster, running[Spell.DETECT_MAGIC]?.castBy)
    }

    /**
     * Refused rather than restarted, so the caster can be told and the scroll
     * kept. Restarting quietly is how a scroll gets spent on a spell that was
     * already in force.
     */
    @Test
    fun `the same spell cast twice is refused`() {
        val once = assertNotNull(SpellsRunning().begun(Spell.DETECT_MAGIC, caster, 9))

        assertNull(once.begun(Spell.DETECT_MAGIC, PartySlot(0), 9))
    }

    /** Two different spells run side by side, neither touching the other. */
    @Test
    fun `two different spells both run`() {
        val running = assertNotNull(
            assertNotNull(SpellsRunning().begun(Spell.DETECT_MAGIC, caster, 9))
                .begun(Spell.MYSTIC_DEFENCE, caster, 9)
        )

        assertEquals(9828, running[Spell.DETECT_MAGIC]?.ticksLeft)
        assertEquals(546, running[Spell.MYSTIC_DEFENCE]?.ticksLeft)
    }

    /** A spell with no duration is not one that can be left running. */
    @Test
    fun `a spell that does not last cannot be begun`() {
        assertNull(SpellsRunning().begun(Spell.FIREBALL, caster, 9))
    }

    @Test
    fun `running down takes the ticks off and leaves it running`() {
        val (left, ended) = assertNotNull(
            SpellsRunning().begun(Spell.MYSTIC_DEFENCE, caster, 9)
        ).runDown(Ticks(46))

        assertEquals(500, left[Spell.MYSTIC_DEFENCE]?.ticksLeft)
        assertEquals(emptyList(), ended)
    }

    /** It runs out after its whole duration and not a tick before. */
    @Test
    fun `it ends only once the last tick is taken`() {
        val begun = assertNotNull(SpellsRunning().begun(Spell.MYSTIC_DEFENCE, caster, 9))

        val (almost, nothingYet) = begun.runDown(Ticks(545))
        assertTrue(almost.isRunning(Spell.MYSTIC_DEFENCE))
        assertEquals(emptyList(), nothingYet)

        val (gone, ended) = almost.runDown(Ticks(1))
        assertFalse(gone.isRunning(Spell.MYSTIC_DEFENCE))
        assertEquals(listOf(Spell.MYSTIC_DEFENCE), ended.map { it.spell })
    }

    /**
     * What ended is handed back rather than merely dropped, because the end
     * is announced in the caster's name and nothing else remembers who that
     * was once the spell is gone.
     */
    @Test
    fun `what ends says who cast it`() {
        val (_, ended) = assertNotNull(
            SpellsRunning().begun(Spell.MYSTIC_DEFENCE, caster, 9)
        ).runDown(Ticks(546))

        assertEquals(caster, ended.single().castBy)
    }

    /** The shorter of two ends first, and the longer goes on alone. */
    @Test
    fun `only what has run out ends`() {
        val both = assertNotNull(
            assertNotNull(SpellsRunning().begun(Spell.DETECT_MAGIC, caster, 9))
                .begun(Spell.MYSTIC_DEFENCE, caster, 9)
        )

        val (left, ended) = both.runDown(Ticks(546))

        assertEquals(listOf(Spell.MYSTIC_DEFENCE), ended.map { it.spell })
        assertTrue(left.isRunning(Spell.DETECT_MAGIC))
    }

    /** A rest ends everything at once, however long any of it had left. */
    @Test
    fun `a rest ends everything`() {
        val both = assertNotNull(
            assertNotNull(SpellsRunning().begun(Spell.DETECT_MAGIC, caster, 9))
                .begun(Spell.MYSTIC_DEFENCE, caster, 9)
        )

        assertEquals(emptyList(), both.allEnded().all)
    }

    /**
     * Spending is not ending. The shield goes and the spell runs on, so the
     * two are asked about separately.
     */
    @Test
    fun `spending what a spell was holding back leaves it running`() {
        val spent = assertNotNull(
            SpellsRunning().begun(Spell.MYSTIC_DEFENCE, caster, 9)
        ).spent(Spell.MYSTIC_DEFENCE)

        assertTrue(spent.isRunning(Spell.MYSTIC_DEFENCE))
        assertEquals(true, spent[Spell.MYSTIC_DEFENCE]?.spent)
        assertEquals(546, spent[Spell.MYSTIC_DEFENCE]?.ticksLeft)
    }

    /** And spending one says nothing about any other. */
    @Test
    fun `spending one leaves the others alone`() {
        val both = assertNotNull(
            assertNotNull(SpellsRunning().begun(Spell.DETECT_MAGIC, caster, 9))
                .begun(Spell.MYSTIC_DEFENCE, caster, 9)
        ).spent(Spell.MYSTIC_DEFENCE)

        assertEquals(false, both[Spell.DETECT_MAGIC]?.spent)
    }

    private fun <T : Any> assertNotNull(value: T?): T =
        kotlin.test.assertNotNull(value)
}
