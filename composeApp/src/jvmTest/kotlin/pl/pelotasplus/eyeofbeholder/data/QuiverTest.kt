package pl.pelotasplus.eyeofbeholder.data

import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.GameState
import pl.pelotasplus.eyeofbeholder.data.model.Item
import pl.pelotasplus.eyeofbeholder.data.model.ItemIconId
import pl.pelotasplus.eyeofbeholder.data.model.ItemIndex
import pl.pelotasplus.eyeofbeholder.data.model.ItemNameId
import pl.pelotasplus.eyeofbeholder.data.model.ItemTypeId
import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.PartyState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Arrows going into a quiver and coming back out.
 *
 * Things kept in one place are a ring rather than a list, so every one of
 * these also asserts the count, which is what walking the ring produces. A
 * ring that has been mislinked still holds every arrow but stops being
 * countable, and a ring that has been broken loses the lot.
 */
class QuiverTest {

    private fun arrow() = Item(
        nameUnidentified = ItemNameId(0),
        nameIdentified = ItemNameId(0),
        flags = 0,
        icon = ItemIconId(1),
        type = ItemTypeId(0),
        pos = 0,
        location = Item.CARRIED,
        next = 0,
        prev = 0,
        level = Item.CARRIED_LEVEL,
        value = 0,
    )

    /** Slot 0 is nothing at all; the arrows are slots 1 to 4. */
    private val world = GameState(
        party = PartyState(Location(0, 0), Direction.NORTH),
        items = List(5) { arrow() },
    )

    private val nothing = ItemIndex(ItemIndex.NOTHING)

    /** Puts [count] arrows in, one at a time, and gives back the quiver. */
    private fun quiverOf(count: Int): GameState.Stacked {
        var stacked = GameState.Stacked(world, nothing)
        repeat(count) { at ->
            stacked = stacked.world.holding(ItemIndex(at + 1)).stacking(stacked.head)
        }
        return stacked
    }

    @Test
    fun `an empty quiver counts nothing`() {
        assertEquals(0, world.stackedIn(nothing))
    }

    @Test
    fun `the first arrow makes a ring of one`() {
        val quiver = quiverOf(1)

        assertEquals(ItemIndex(1), quiver.head)
        assertEquals(1, quiver.world.stackedIn(quiver.head))
        assertFalse(quiver.world.inHand.isSomething)
    }

    @Test
    fun `each arrow put in is one more to count`() {
        (1..4).forEach { count ->
            val quiver = quiverOf(count)
            assertEquals(count, quiver.world.stackedIn(quiver.head), "after $count went in")
        }
    }

    /** What goes in last comes out first, and the rest are still there. */
    @Test
    fun `taking one out leaves the others countable`() {
        val quiver = quiverOf(3)
        val taken = quiver.world.unstacking(quiver.head)

        assertEquals(ItemIndex(3), taken.world.inHand)
        assertEquals(2, taken.world.stackedIn(taken.head))
    }

    @Test
    fun `emptying a quiver one at a time leaves it empty`() {
        var quiver = quiverOf(3)

        repeat(3) { taken ->
            quiver = quiver.world.unstacking(quiver.head)
            assertTrue(quiver.world.inHand.isSomething, "nothing came out on take $taken")
        }

        assertFalse(quiver.head.isSomething)
        assertEquals(0, quiver.world.stackedIn(quiver.head))
    }

    /** Filling, emptying and filling again must not have bent the ring. */
    @Test
    fun `a quiver refills after being emptied`() {
        var quiver = quiverOf(2)
        repeat(2) { quiver = quiver.world.unstacking(quiver.head) }

        quiver = quiver.world.holding(ItemIndex(1)).stacking(quiver.head)
        quiver = quiver.world.holding(ItemIndex(2)).stacking(quiver.head)

        assertEquals(2, quiver.world.stackedIn(quiver.head))
    }

    /** An arrow on a stack is on no level, so nothing draws it anywhere. */
    @Test
    fun `an arrow in the quiver lies nowhere on the map`() {
        val quiver = quiverOf(1)
        val stored = quiver.world.item(quiver.head)

        assertEquals(Item.ON_A_STACK, stored?.location)
        assertEquals(Item.NO_LEVEL, stored?.level)
    }

    /** One that comes back out is being carried, like anything else in hand. */
    @Test
    fun `an arrow taken out is carried`() {
        val taken = quiverOf(1).let { it.world.unstacking(it.head) }
        val held = taken.world.held

        assertEquals(Item.CARRIED, held?.location)
        assertEquals(Item.CARRIED_LEVEL, held?.level)
    }

    @Test
    fun `putting nothing in changes nothing`() {
        val quiver = quiverOf(2)
        val again = quiver.world.stacking(quiver.head)

        assertEquals(quiver.head, again.head)
        assertEquals(2, again.world.stackedIn(again.head))
    }

    @Test
    fun `taking from an empty quiver changes nothing`() {
        val taken = world.unstacking(nothing)

        assertFalse(taken.head.isSomething)
        assertFalse(taken.world.inHand.isSomething)
    }
}
