package pl.pelotasplus.eyeofbeholder.data

import pl.pelotasplus.eyeofbeholder.data.model.Champion
import pl.pelotasplus.eyeofbeholder.data.model.ChampionBox
import pl.pelotasplus.eyeofbeholder.data.model.ChampionFlags
import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.GameState
import pl.pelotasplus.eyeofbeholder.data.model.HitPoints
import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.PartySlot
import pl.pelotasplus.eyeofbeholder.data.model.PartyState
import pl.pelotasplus.eyeofbeholder.data.model.championBoxes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Moving two champions around the party, which is how the front rank is
 * chosen: only the first two can reach what is in front of them, so who stands
 * where is the difference between a fighter swinging and a mage being hit.
 *
 * Whole people change places rather than their belongings, so everything they
 * are goes with them.
 */
class SwappingChampionsTest {

    private fun world() = GameState(
        party = PartyState(Location(1, 1), Direction.NORTH),
        champions = List(6) { slot ->
            Champion.NOBODY.copy(
                name = "Name$slot",
                flags = ChampionFlags(if (slot < 4) 1 else 0),
                hitPoints = HitPoints(10 + slot, 20 + slot),
            )
        },
    )

    private val first = PartySlot(0)
    private val third = PartySlot(2)
    private val empty = PartySlot(5)

    @Test
    fun `two champions change places whole`() {
        val swapped = world().championsSwapped(first, third)

        assertEquals("Name2", swapped.champions[0].name)
        assertEquals("Name0", swapped.champions[2].name)
        assertEquals(
            HitPoints(12, 22),
            swapped.champions[0].hitPoints,
            "the person moved but their hit points did not go with them",
        )
    }

    @Test
    fun `the rest of the party stay where they are`() {
        val before = world()
        val swapped = before.championsSwapped(first, third)

        listOf(1, 3, 4, 5).forEach { slot ->
            assertEquals(
                before.champions[slot],
                swapped.champions[slot],
                "slot $slot moved and had nothing to do with it",
            )
        }
    }

    /** Naming the same champion twice is how the party change their mind. */
    @Test
    fun `swapping somebody with themselves changes nothing`() {
        val before = world()

        assertEquals(before, before.championsSwapped(first, first))
    }

    /**
     * An empty place is a place: a party of four decide which two of them lead
     * by moving somebody into one of the two boxes nobody is in.
     */
    @Test
    fun `somebody may be moved into an empty place`() {
        val swapped = world().championsSwapped(first, empty)

        assertFalse(swapped.champions[0].inTheParty, "the empty place did not come back")
        assertEquals("Name0", swapped.champions[5].name)
        assertTrue(swapped.champions[5].inTheParty)
    }

    // --- where the click has to land ------------------------------------------

    /**
     * The strip runs the width of the box, so a short name is as easy to hit as
     * a long one, and stops where the portrait starts.
     */
    @Test
    fun `the name strip is the top of the box and nothing below it`() {
        val box = ChampionBox(left = 10, top = 20)

        assertTrue(box.showsNameAt(10, 20), "its top left corner")
        assertTrue(box.showsNameAt(10 + ChampionBox.WIDTH - 1, 20), "its top right")
        assertTrue(box.showsNameAt(40, 28), "the last row of the strip")

        assertFalse(box.showsNameAt(40, 29), "the portrait below it")
        assertFalse(box.showsNameAt(9, 20), "the box to the left")
        assertFalse(box.showsNameAt(10 + ChampionBox.WIDTH, 20), "the box to the right")
    }

    /** Six boxes, six strips, and no two of them overlapping. */
    @Test
    fun `each box has a strip of its own`() {
        championBoxes.forEachIndexed { slot, box ->
            val hits = championBoxes.count { it.showsNameAt(box.left + 1, box.top + 1) }

            assertEquals(1, hits, "the strip of box $slot is shared with another")
        }
    }
}
