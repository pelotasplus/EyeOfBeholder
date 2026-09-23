package pl.pelotasplus.eyeofbeholder.data

import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.GameState
import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.PartySlot
import pl.pelotasplus.eyeofbeholder.data.model.PartyState
import pl.pelotasplus.eyeofbeholder.data.model.SettlesOn
import pl.pelotasplus.eyeofbeholder.data.model.Spell
import pl.pelotasplus.eyeofbeholder.data.model.SpellLasts
import pl.pelotasplus.eyeofbeholder.data.model.SpellsRunning
import pl.pelotasplus.eyeofbeholder.data.model.Ticks
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Blur, and the per-champion half of the running-spells store it needed.
 *
 * Nothing about it can be seen happening: it takes two off a monster's roll
 * to hit the champion it is on, and the only sign of it is the frame round
 * their portrait. So what is worth asserting is that it lands on one champion
 * rather than the party, that the two off the roll are taken off the roll and
 * not off armour, and that it runs as long as the game says.
 */
class WhatABlurIsWorthTest {

    private val reader = PartySlot(2)
    private val someoneElse = PartySlot(4)

    private fun world() = GameState(party = PartyState(Location(3, 11), Direction.NORTH))

    // ---- how long, and on whom -----------------------------------------

    /**
     * Three half-minutes flat and another for every level of the caster, so
     * read off a scroll — always ninth level — six minutes.
     */
    @Test
    fun `it lasts three half-minutes and one more per level`() {
        val lasts = assertNotNull(Spell.BLUR.lasts)

        assertEquals(SpellLasts(base = 3, perLevel = 1), lasts)
        assertEquals(Ticks(2184), lasts.castBySomeoneOfLevel(1))
        assertEquals(Ticks(6552), lasts.castBySomeoneOfLevel(9))
        assertEquals(360_360, Ticks(6552).inMilliseconds)
    }

    /** On whoever read it, and without asking anybody which champion. */
    @Test
    fun `it settles on whoever cast it`() {
        assertEquals(SettlesOn.WHOEVER_CAST_IT, Spell.BLUR.settlesOn)

        val after = assertNotNull(
            world().spellBegunWhereItSettles(Spell.BLUR, reader, casterLevel = 9)
        )

        assertTrue(after.running.isOn(Spell.BLUR, reader))
        assertFalse(after.running.isOn(Spell.BLUR, someoneElse))
    }

    /** And it is nobody's party-wide spell, however many champions carry one. */
    @Test
    fun `it is never over the party`() {
        val after = assertNotNull(
            world().spellBegunWhereItSettles(Spell.BLUR, reader, casterLevel = 9)
        )

        assertFalse(after.running.isRunning(Spell.BLUR))
        assertNull(after.running[Spell.BLUR])
    }

    /**
     * One champion's is no reason to refuse another theirs, which is the
     * whole of why the store had to learn about slots.
     */
    @Test
    fun `two champions may each carry their own`() {
        val both = assertNotNull(
            assertNotNull(world().spellBegunWhereItSettles(Spell.BLUR, reader, 9))
                .spellBegunWhereItSettles(Spell.BLUR, someoneElse, 9)
        )

        assertTrue(both.running.isOn(Spell.BLUR, reader))
        assertTrue(both.running.isOn(Spell.BLUR, someoneElse))
    }

    /** The same champion casting it twice is refused, so no scroll is spent. */
    @Test
    fun `the same champion is refused a second`() {
        val once = assertNotNull(world().spellBegunWhereItSettles(Spell.BLUR, reader, 9))

        assertNull(once.spellBegunWhereItSettles(Spell.BLUR, reader, 9))
    }

    // ---- what it is worth ----------------------------------------------

    @Test
    fun `it takes two off a roll to hit whoever carries it`() {
        val blurred = assertNotNull(
            world().spellBegunWhereItSettles(Spell.BLUR, reader, 9)
        ).running

        assertEquals(2, blurred.hindranceStriking(reader))
        assertEquals(0, blurred.hindranceStriking(someoneElse))
    }

    /** Nobody is harder to hit before anything has been cast. */
    @Test
    fun `an unblurred party is no harder to hit`() {
        assertEquals(0, SpellsRunning().hindranceStriking(reader))
    }

    /** Running down to nothing takes the two back off again. */
    @Test
    fun `it is worth nothing once it has run out`() {
        val cast = assertNotNull(world().spellBegunWhereItSettles(Spell.BLUR, reader, 9))

        assertEquals(2, cast.spellsRunDown(Ticks(6551)).first.running.hindranceStriking(reader))
        assertEquals(0, cast.spellsRunDown(Ticks(6552)).first.running.hindranceStriking(reader))
    }

    // ---- what is drawn --------------------------------------------------

    @Test
    fun `a blurred champion is framed and the others are not`() {
        val after = assertNotNull(
            world().spellBegunWhereItSettles(Spell.BLUR, reader, 9)
        )

        assertTrue(after.running.blurred(reader))
        assertFalse(after.running.blurred(someoneElse))
    }

    // ---- the store's own new half ---------------------------------------

    /**
     * Asked from a champion's own side, a spell over the party and a spell on
     * them are the same thing — which is what lets one sum stand for both.
     */
    @Test
    fun `everything over a champion counts what is on them and on the party`() {
        val both = assertNotNull(
            assertNotNull(world().spellBegunWhereItSettles(Spell.BLUR, reader, 9))
                .spellBegunWhereItSettles(Spell.DETECT_MAGIC, someoneElse, 9)
        ).running

        assertEquals(
            listOf(Spell.BLUR, Spell.DETECT_MAGIC),
            both.over(reader).map { it.spell },
        )

        // The other champion is under the party's spell but not the blur.
        assertEquals(listOf(Spell.DETECT_MAGIC), both.over(someoneElse).map { it.spell })
    }

    /** A party-wide spell is still asked for without naming anybody. */
    @Test
    fun `a party spell is found without a slot`() {
        val after = assertNotNull(
            world().spellBegunWhereItSettles(Spell.DETECT_MAGIC, reader, 9)
        )

        assertTrue(after.running.isRunning(Spell.DETECT_MAGIC))
        assertFalse(after.running.isOn(Spell.DETECT_MAGIC, reader))
    }
}
