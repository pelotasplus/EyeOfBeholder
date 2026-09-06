package pl.pelotasplus.eyeofbeholder.data

import pl.pelotasplus.eyeofbeholder.data.model.Champion
import pl.pelotasplus.eyeofbeholder.data.model.ChampionFlags
import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.GameState
import pl.pelotasplus.eyeofbeholder.data.model.HitPoints
import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.PartyState
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * When a party count as lost, which is not when they count as dead.
 *
 * The question is who is still on their feet, and being on your feet asks more
 * than being raisable. A champion knocked senseless is down, and one turned to
 * stone is down, though a cleric could bring either back — so a party can be
 * lost with all six of them saveable and nobody left to do the saving.
 *
 * It asks less than being able to act, though: held and paralysed champions are
 * standing, and a party held where they are have not lost anything yet.
 */
class WhenThePartyAreLostTest {

    @Test
    fun `a party with one on their feet are not lost`() {
        assertFalse(partyOf(standing(), out(), out(), out(), out(), out()).nobodyIsStanding)
    }

    @Test
    fun `a party all knocked senseless are lost, raisable or not`() {
        val party = partyOf(out(), out(), out(), out(), out(), out())

        assertTrue(party.nobodyIsStanding, "somebody was still counted standing")
        assertTrue(
            party.champions.none { it.deadForGood },
            "the fixture killed them outright, which is the easy case",
        )
    }

    @Test
    fun `a party all turned to stone are lost`() {
        assertTrue(partyOf(stone(), stone(), stone(), stone(), stone(), stone()).nobodyIsStanding)
    }

    /**
     * Held is not down. Nothing in the game raises a party who are merely
     * pinned, and treating them as lost would end a run that a wearing-off
     * clock was about to give back.
     */
    @Test
    fun `a party held where they stand are not lost`() {
        val held = standing().paralysed(true)

        assertFalse(partyOf(held, held, held, held, held, held).nobodyIsStanding)
    }

    /**
     * And a world with no roster in it is a game that has not finished
     * starting, not a lost one — the floor is put up before the party are.
     */
    @Test
    fun `a world with nobody in it at all is not lost`() {
        assertFalse(partyOf().nobodyIsStanding)
        assertFalse(partyOf(Champion.NOBODY, Champion.NOBODY).nobodyIsStanding)
    }

    // --- the fixture ---------------------------------------------------------

    private fun partyOf(vararg champions: Champion) = GameState(
        party = PartyState(Location(3, 11), Direction.NORTH),
        champions = champions.toList(),
    )

    private fun standing() = Champion.NOBODY.copy(
        name = "One",
        flags = ChampionFlags(IN_THE_PARTY),
        hitPoints = HitPoints(40, 40),
    )

    /** Down but well short of past raising. */
    private fun out() = standing().copy(hitPoints = HitPoints(0, 40))

    private fun stone() = standing().turnedToStone()

    private companion object {
        const val IN_THE_PARTY = 0x01
    }
}
