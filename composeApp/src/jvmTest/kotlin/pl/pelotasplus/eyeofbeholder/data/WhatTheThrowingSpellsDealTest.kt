package pl.pelotasplus.eyeofbeholder.data

import pl.pelotasplus.eyeofbeholder.data.model.Burst
import pl.pelotasplus.eyeofbeholder.data.model.ConjuredBolt
import pl.pelotasplus.eyeofbeholder.data.model.CountedBy
import pl.pelotasplus.eyeofbeholder.data.model.DamageDice
import pl.pelotasplus.eyeofbeholder.data.model.HarmKind
import pl.pelotasplus.eyeofbeholder.data.model.MonsterSpell
import pl.pelotasplus.eyeofbeholder.data.model.SavingThrow
import pl.pelotasplus.eyeofbeholder.data.model.Spell
import pl.pelotasplus.eyeofbeholder.data.model.ThrownSpell
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What the spells that send something down a corridor deal when they arrive.
 *
 * Every number here is the spell's own, and none of it is worked out: an acid
 * arrow is two dice of four counted once for every three levels the caster
 * has, and a flame strike is six dice of eight counted once whoever calls it
 * down. Getting either wrong is a spell that still flies and still lands and
 * is merely the wrong strength, which nothing else would catch.
 */
class WhatTheThrowingSpellsDealTest {

    @Test
    fun `an acid arrow is two dice of four, once for every three levels`() {
        val thrown = assertNotNull(Spell.MELFS_ACID_ARROW.throws)

        assertEquals(DamageDice(times = 2, pips = 4, base = 0), thrown.dealing)
        assertEquals(CountedBy.EVERY_THIRD_LEVEL, thrown.counted)
        assertEquals(setOf(HarmKind.MAGIC, HarmKind.ACID), thrown.hurting)
    }

    /**
     * Everything cast is magical, whatever else it is.
     *
     * Each spell names the kinds of harm it does, and every one of them names
     * magic among them: a creature that turns spells aside turns all of them
     * aside, and one that merely shrugs off fire still feels a bolt of
     * lightning. Leaving magic off a spell is the quiet kind of mistake —
     * nothing fails, a handful of creatures simply stop being immune to
     * something they should be immune to.
     */
    @Test
    fun `every spell that throws something is magical`() {
        val throwers = Spell.entries.mapNotNull { spell -> spell.throws?.let { spell to it } }

        // The two that hold rather than hurt roll no damage and name no kinds.
        val hurting = throwers.filter { (_, thrown) -> thrown.holds == null }

        assertTrue(hurting.isNotEmpty(), "nothing throws damage, which cannot be right")
        hurting.forEach { (spell, thrown) ->
            assertTrue(
                HarmKind.MAGIC in thrown.hurting,
                "${spell.calledIt} does not count as magic",
            )
        }
    }

    /** A ninth-level caster is three counts of it, and a scroll is one of those. */
    @Test
    fun `an acid arrow off a scroll is counted three times`() {
        val harm = assertNotNull(Spell.MELFS_ACID_ARROW.throws)
            .dealtBy(ThrownSpell.AS_READ_FROM_A_SCROLL)

        assertEquals(3, harm.times)
    }

    /** And never fewer than once, however new the caster. */
    @Test
    fun `a caster too new for three levels still throws one`() {
        assertEquals(1, CountedBy.EVERY_THIRD_LEVEL.forACasterOf(1))
        assertEquals(1, CountedBy.EVERY_THIRD_LEVEL.forACasterOf(2))
        assertEquals(1, CountedBy.EVERY_THIRD_LEVEL.forACasterOf(3))
        assertEquals(2, CountedBy.EVERY_THIRD_LEVEL.forACasterOf(6))
    }

    /**
     * Nothing is thrown against an acid arrow. It picks one creature out and
     * that creature has no say in it.
     */
    @Test
    fun `nothing is thrown against an acid arrow`() {
        val thrown = assertNotNull(Spell.MELFS_ACID_ARROW.throws)

        assertNull(thrown.thrownOff)
        assertTrue(!thrown.takesTheWholeSquare, "an acid arrow finds one mark")
    }

    @Test
    fun `a flame strike is six dice of eight over the whole square`() {
        val thrown = assertNotNull(Spell.FLAME_STRIKE.throws)

        assertEquals(DamageDice(times = 6, pips = 8, base = 0), thrown.dealing)
        assertEquals(SavingThrow.A_SPELL, thrown.thrownOff)
        assertTrue(thrown.takesTheWholeSquare, "a flame strike takes the square")
    }

    /** However practised the reader: nine counts of it would be absurd. */
    @Test
    fun `a flame strike is the same off a scroll as off any caster`() {
        val thrown = assertNotNull(Spell.FLAME_STRIKE.throws)

        assertEquals(1, thrown.dealtBy(ThrownSpell.AS_READ_FROM_A_SCROLL).times)
        assertEquals(1, thrown.dealtBy(1).times)
        assertEquals(1, thrown.dealtBy(12).times)
    }

    /**
     * An acid arrow is drawn as an arrow rather than as a bolt of light, and
     * a picture is what decides whether it is ever drawn at all: one without
     * is never put in the air.
     */
    @Test
    fun `an acid arrow flies as an arrow`() {
        assertEquals(
            ConjuredBolt.LIKE_AN_ARROW,
            MonsterSpell.MELFS_ACID_ARROW.looksLike,
            "with no picture it would never leave the hand",
        )
        assertEquals(
            MonsterSpell.MELFS_ACID_ARROW,
            assertNotNull(Spell.MELFS_ACID_ARROW.throws).flies,
        )
    }

    /**
     * A flame strike is a fireball to look at — the same bolt down the
     * corridor and the same colours where it goes off — and differs only in
     * what it rolls and who it takes.
     *
     * This is asserted rather than shown, because a golden of it would be
     * the fireball's goldens over again, pixel for pixel. What is worth
     * guarding is the mapping: change either line below and a flame strike
     * quietly becomes some other spell to look at, with nothing to catch it.
     */
    @Test
    fun `a flame strike flies and bursts as fire`() {
        assertEquals(
            MonsterSpell.FLAME_STRIKE,
            assertNotNull(Spell.FLAME_STRIKE.throws).flies,
        )
        assertEquals(ConjuredBolt.LIKE_FIRE, MonsterSpell.FLAME_STRIKE.looksLike)
        assertEquals(Burst.LIKE_FIRE, MonsterSpell.FLAME_STRIKE.burstsLike)
    }
}
