package pl.pelotasplus.eyeofbeholder.data

import pl.pelotasplus.eyeofbeholder.data.model.Ailment
import pl.pelotasplus.eyeofbeholder.data.model.Champion
import pl.pelotasplus.eyeofbeholder.data.model.ChampionFlags
import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.GameState
import pl.pelotasplus.eyeofbeholder.data.model.HitPoints
import pl.pelotasplus.eyeofbeholder.data.model.LaidOnAChampion
import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.PartySlot
import pl.pelotasplus.eyeofbeholder.data.model.PartyState
import pl.pelotasplus.eyeofbeholder.data.model.Race
import pl.pelotasplus.eyeofbeholder.data.model.Spell
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The spells that take something off a champion rather than giving anything
 * back: a poisoning lifted, stone made flesh again, and the dead raised.
 *
 * Each of them can be pointed at somebody it would do nothing for, and the
 * point of asking first is that such a casting is refused rather than spent.
 * A scroll of three is worth more than the gesture.
 */
class WhatASpellLiftsOffAChampionTest {

    @Test
    fun `neutralize poison lifts a poisoning and nothing else`() {
        val lifts = LaidOnAChampion.Lifts(Ailment.POISON)

        assertTrue(lifts.wouldHelp(poisoned()), "a poisoned champion wants it")
        assertFalse(lifts.wouldHelp(hale()), "a hale one does not")
        assertFalse(lifts.wouldHelp(stone()), "and it is no use against stone")

        assertFalse(Ailment.POISON.liftedFrom(poisoned()).poisoned)
    }

    @Test
    fun `stone to flesh lifts being stone and nothing else`() {
        val lifts = LaidOnAChampion.Lifts(Ailment.BEING_STONE)

        assertTrue(lifts.wouldHelp(stone()))
        assertFalse(lifts.wouldHelp(hale()))
        assertFalse(lifts.wouldHelp(poisoned()), "poison is not stone")

        assertFalse(Ailment.BEING_STONE.liftedFrom(stone()).petrified)
    }

    /**
     * Being turned to stone takes everything else with it, so coming back out
     * brings nothing back: a champion poisoned before they were stone is not
     * poisoned afterwards.
     */
    @Test
    fun `coming out of stone brings nothing back with it`() {
        val wasPoisonedThenStoned = poisoned().turnedToStone()

        assertFalse(wasPoisonedThenStoned.poisoned, "stone took the poison with it")
        assertFalse(Ailment.BEING_STONE.liftedFrom(wasPoisonedThenStoned).poisoned)
    }

    /**
     * Raising reaches ten below and nowhere else. Somebody merely knocked down
     * wants mending, and is told so rather than having the scroll spent on
     * them.
     */
    @Test
    fun `raising reaches the dead and only the dead`() {
        assertTrue(LaidOnAChampion.Raises.wouldHelp(lying(at = Champion.BEYOND_RAISING)))

        assertFalse(
            LaidOnAChampion.Raises.wouldHelp(lying(at = Champion.BEYOND_RAISING + 1)),
            "somebody a point above raising wants mending, not raising",
        )
        assertFalse(LaidOnAChampion.Raises.wouldHelp(lying(at = 0)), "down is not gone")
        assertFalse(LaidOnAChampion.Raises.wouldHelp(hale()))
    }

    /**
     * And not an elf. What an elf leaves behind does not answer to it, which
     * is what the spell nobody has written was for — so these scrolls are
     * worth nothing to a party of them.
     */
    @Test
    fun `no elf is raised`() {
        val elf = lying(at = Champion.BEYOND_RAISING).copy(race = Race.ELF)
        val dwarf = lying(at = Champion.BEYOND_RAISING).copy(race = Race.DWARF)

        assertFalse(LaidOnAChampion.Raises.wouldHelp(elf))
        assertTrue(LaidOnAChampion.Raises.wouldHelp(dwarf))
    }

    /** Somebody raised comes back with a point in them and nothing more. */
    @Test
    fun `raising gives back one point and no more`() {
        val world = worldWith(lying(at = Champion.BEYOND_RAISING).copy(race = Race.HUMAN))

        val after = world.withTheSpellLaidOn(
            PartySlot(0),
            LaidOnAChampion.Raises,
            byWhom = hale(),
        )

        assertEquals(Champion.RAISED_WITH, after.champions[0].hitPoints.current)
        assertFalse(after.champions[0].deadForGood, "they are back among the living")
    }

    /** The world carries a lifting through as well as a mending. */
    @Test
    fun `laying a lifting on somebody takes it off them`() {
        val world = worldWith(poisoned())

        val after = world.withTheSpellLaidOn(
            PartySlot(0),
            LaidOnAChampion.Lifts(Ailment.POISON),
            byWhom = hale(),
        )

        assertFalse(after.champions[0].poisoned)
    }

    /** And the three spells are pointed at the three things. */
    @Test
    fun `the three spells name what they lift`() {
        assertEquals(
            LaidOnAChampion.Lifts(Ailment.POISON),
            Spell.NEUTRALIZE_POISON.laidOn,
        )
        assertEquals(
            LaidOnAChampion.Lifts(Ailment.BEING_STONE),
            Spell.STONE_TO_FLESH.laidOn,
        )
        assertEquals(LaidOnAChampion.Raises, Spell.RAISE_DEAD.laidOn)
    }

    // --- the fixture -------------------------------------------------------

    private fun hale() = Champion.NOBODY.copy(
        name = "Anselm",
        race = Race.HUMAN,
        flags = ChampionFlags(IN_THE_PARTY),
        hitPoints = HitPoints(40, 40),
    )

    private fun poisoned() = hale().poisoned(true)

    private fun stone() = hale().turnedToStone()

    private fun lying(at: Int) = hale().copy(hitPoints = HitPoints(at, 40))

    private fun worldWith(who: Champion) = GameState(
        party = PartyState(Location(3, 11), Direction.NORTH),
        champions = listOf(who),
    )

    private companion object {
        const val IN_THE_PARTY = 0x01
    }
}
