package pl.pelotasplus.eyeofbeholder.data

import pl.pelotasplus.eyeofbeholder.data.model.Abilities
import pl.pelotasplus.eyeofbeholder.data.model.Ability
import pl.pelotasplus.eyeofbeholder.data.model.ArmorClass
import pl.pelotasplus.eyeofbeholder.data.model.CarrySlot
import pl.pelotasplus.eyeofbeholder.data.model.Champion
import pl.pelotasplus.eyeofbeholder.data.model.ChampionFlags
import pl.pelotasplus.eyeofbeholder.data.model.CharacterClass
import pl.pelotasplus.eyeofbeholder.data.model.ClassLevel
import pl.pelotasplus.eyeofbeholder.data.model.Damage
import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.Food
import pl.pelotasplus.eyeofbeholder.data.model.GameState
import pl.pelotasplus.eyeofbeholder.data.model.HandRecovering
import pl.pelotasplus.eyeofbeholder.data.model.HitPoints
import pl.pelotasplus.eyeofbeholder.data.model.ItemIndex
import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.PartySlot
import pl.pelotasplus.eyeofbeholder.data.model.PartyState
import pl.pelotasplus.eyeofbeholder.data.model.PortraitId
import pl.pelotasplus.eyeofbeholder.data.model.SettlesOn
import pl.pelotasplus.eyeofbeholder.data.model.Spell
import pl.pelotasplus.eyeofbeholder.data.model.SpellLasts
import pl.pelotasplus.eyeofbeholder.data.model.Ticks
import pl.pelotasplus.eyeofbeholder.data.model.WhatTheBlowCameTo
import pl.pelotasplus.eyeofbeholder.data.model.XpPoints
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Haste: every champion swinging twice as often for six minutes.
 *
 * The wait after a swing comes in two parts and haste shortens only the
 * second — the first is the slot saying what the blow came to, which is
 * there to be read and is as long as ever. So it is twice the swings and not
 * four times, and that difference is the whole of what is worth asserting
 * about the number.
 */
class WhatAHasteBuysTest {

    private val reader = PartySlot(2)

    private fun aChampion(hitPoints: Int = 40) = Champion(
        name = "Anselm",
        portrait = PortraitId(0),
        abilities = Abilities(strength = Ability(10, 10), dexterity = Ability(10, 10)),
        hitPoints = HitPoints(hitPoints, hitPoints),
        armorClass = ArmorClass(10),
        food = Food(100),
        characterClass = CharacterClass.FIGHTER,
        levels = listOf(ClassLevel(1, XpPoints(0))),
        carrying = List(27) { ItemIndex(ItemIndex.NOTHING) },
        flags = ChampionFlags(1),
    )

    private fun world(party: List<Champion> = List(6) { aChampion() }) = GameState(
        party = PartyState(Location(3, 11), Direction.NORTH),
        champions = party,
    )

    // ---- the wait --------------------------------------------------------

    /**
     * Eighteen of saying and thirty-six of resting, against eighteen and
     * nine: fifty-four ticks becomes twenty-seven.
     */
    @Test
    fun `a swing costs half as long when hastened`() {
        assertEquals(Ticks(18), HandRecovering.REPORTING)
        assertEquals(Ticks(36), HandRecovering.AFTER_THE_REPORT)
        assertEquals(Ticks(9), HandRecovering.AFTER_THE_REPORT_HASTENED)

        assertEquals(Ticks(54), HandRecovering.AFTER_A_SWING)
        assertEquals(Ticks(27), HandRecovering.AFTER_A_SWING_HASTENED)
    }

    /** The saying is not hurried, which is why it is not four times as fast. */
    @Test
    fun `the report is as long as ever`() {
        val blow = WhatTheBlowCameTo.Missed

        assertEquals(Ticks(54), blow.waitFor(hastened = false))
        assertEquals(Ticks(27), blow.waitFor(hastened = true))
    }

    /**
     * An arm that never went anywhere is not hurried either: there is nothing
     * to recover from, only the saying, and that is the whole of its wait.
     */
    @Test
    fun `a swing that never happened costs the same either way`() {
        val never = WhatTheBlowCameTo.CannotReach

        assertEquals(Ticks(18), never.waitFor(hastened = false))
        assertEquals(Ticks(18), never.waitFor(hastened = true))
    }

    /** And the world charges the shorter wait to a hastened champion. */
    @Test
    fun `a hastened champion's hand comes back sooner`() {
        val hastened = assertNotNull(
            world().spellBegunWhereItSettles(Spell.HASTE, reader, casterLevel = 9)
        )

        val swung = hastened.handSwung(reader, CarrySlot(0), WhatTheBlowCameTo.TookOff(Damage(4)))
        val bare = world().handSwung(reader, CarrySlot(0), WhatTheBlowCameTo.TookOff(Damage(4)))

        assertEquals(27, swung.recovering.single().ticksLeft)
        assertEquals(54, bare.recovering.single().ticksLeft)
    }

    /**
     * A hand that threw or fired is hurried by exactly as much as one that
     * swung. They are one action as far as the hand is concerned — the game
     * runs both down the same path and only differs in what left the hand —
     * so a hasted archer looses twice as fast as well.
     */
    @Test
    fun `throwing and firing are hurried too`() {
        val hastened = assertNotNull(
            world().spellBegunWhereItSettles(Spell.HASTE, reader, casterLevel = 9)
        )

        assertEquals(27, hastened.handLoosed(reader, CarrySlot(0)).recovering.single().ticksLeft)
        assertEquals(54, world().handLoosed(reader, CarrySlot(0)).recovering.single().ticksLeft)
    }

    /**
     * Reading a scroll or pointing a wand costs the hand as long as a swing,
     * and is hurried by the same.
     *
     * The slot says nothing at the end of it — a blow has an outcome worth
     * reading and words do not — but the hand is gone just as long. Nothing
     * asserted this before, which is how it came to be a third of what it
     * should have been.
     */
    @Test
    fun `reading something aloud costs as long as a swing`() {
        val hastened = assertNotNull(
            world().spellBegunWhereItSettles(Spell.HASTE, reader, casterLevel = 9)
        )

        assertEquals(54, world().handCast(reader, CarrySlot(0)).recovering.single().ticksLeft)
        assertEquals(27, hastened.handCast(reader, CarrySlot(0)).recovering.single().ticksLeft)
    }

    /** Whatever the hand did, it is charged the same — one wait, one rule. */
    @Test
    fun `a swing, a shot and a reading all cost the same`() {
        val swung = world().handSwung(reader, CarrySlot(0), WhatTheBlowCameTo.Missed)
        val loosed = world().handLoosed(reader, CarrySlot(0))
        val read = world().handCast(reader, CarrySlot(0))

        assertEquals(54, swung.recovering.single().ticksLeft)
        assertEquals(54, loosed.recovering.single().ticksLeft)
        assertEquals(54, read.recovering.single().ticksLeft)
    }

    // ---- who it reaches --------------------------------------------------

    @Test
    fun `it lasts three half-minutes and one more per level`() {
        assertEquals(SpellLasts(base = 3, perLevel = 1), Spell.HASTE.lasts)
        assertEquals(Ticks(6552), assertNotNull(Spell.HASTE.lasts).castBySomeoneOfLevel(9))
    }

    /** All six of them, not the reader and not the party as one thing. */
    @Test
    fun `it settles on every champion separately`() {
        assertEquals(SettlesOn.EVERY_CHAMPION, Spell.HASTE.settlesOn)

        val after = assertNotNull(
            world().spellBegunWhereItSettles(Spell.HASTE, reader, casterLevel = 9)
        )

        (0 until 6).forEach { slot ->
            assertTrue(after.running.hastened(PartySlot(slot)), "slot $slot was not hurried")
        }

        // Six of them and no party-wide one, which is what lets each be
        // dispelled on its own.
        assertEquals(6, after.running.all.size)
        assertFalse(after.running.isRunning(Spell.HASTE))
    }

    /** It passes over anyone past raising — there is nothing there to hurry. */
    @Test
    fun `it passes over whoever is past raising`() {
        val party = List(6) { if (it == 3) aChampion().copy(hitPoints = HitPoints(-11, 40)) else aChampion() }

        val after = assertNotNull(
            world(party).spellBegunWhereItSettles(Spell.HASTE, reader, casterLevel = 9)
        )

        assertFalse(after.running.hastened(PartySlot(3)))
        assertTrue(after.running.hastened(PartySlot(2)))
        assertEquals(5, after.running.all.size)
    }

    /**
     * Cast again with nobody new to reach it is refused, so no scroll is
     * spent — but it is the reaching that is asked about, not the spell, so
     * somebody who joined since would still be covered.
     */
    @Test
    fun `casting it again over the same six is refused`() {
        val once = assertNotNull(
            world().spellBegunWhereItSettles(Spell.HASTE, reader, casterLevel = 9)
        )

        assertNull(once.spellBegunWhereItSettles(Spell.HASTE, reader, casterLevel = 9))
    }

    // ---- and when it goes ------------------------------------------------

    @Test
    fun `the hurry goes when it runs out`() {
        val cast = assertNotNull(
            world().spellBegunWhereItSettles(Spell.HASTE, reader, casterLevel = 9)
        )

        assertTrue(cast.spellsRunDown(Ticks(6551)).first.running.hastened(reader))
        assertFalse(cast.spellsRunDown(Ticks(6552)).first.running.hastened(reader))
    }

    /** All six end together, having all begun together. */
    @Test
    fun `all six end at once`() {
        val cast = assertNotNull(
            world().spellBegunWhereItSettles(Spell.HASTE, reader, casterLevel = 9)
        )

        val (left, ended) = cast.spellsRunDown(Ticks(6552))

        assertEquals(6, ended.size)
        assertEquals(emptyList(), left.running.all)
    }
}
