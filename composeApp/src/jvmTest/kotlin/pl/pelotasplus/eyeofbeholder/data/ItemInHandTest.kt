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
import pl.pelotasplus.eyeofbeholder.data.model.SquarePlace
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Taking something off the floor and putting it down again.
 *
 * The table has room for more items than a game holds, and slot zero is
 * nothing at all — the original uses it for an empty hand — so the numbers
 * here start at one.
 */
class ItemInHandTest {

    private val here = Location(5, 6)
    private val level = 4

    private fun lying(at: Location, place: SquarePlace, level: Int = this.level) = Item(
        nameUnidentified = ItemNameId(0),
        nameIdentified = ItemNameId(0),
        flags = 0,
        icon = ItemIconId(3),
        type = ItemTypeId(0),
        place = place,
        location = at,
        next = 0,
        prev = 0,
        level = level,
        value = 0,
    )

    private val nothing = lying(Item.NOWHERE, SquarePlace.NORTH_WEST, level = 0)

    /** Slot 0 is nothing; a dagger lies in the north-west corner of (5,6). */
    private val world = GameState(
        party = PartyState(here, Direction.NORTH),
        items = listOf(nothing, lying(here, SquarePlace.NORTH_WEST)),
    )

    private val dagger = ItemIndex(1)

    @Test
    fun `an empty hand holds nothing`() {
        assertNull(world.held)
        assertTrue(!world.inHand.isSomething)
    }

    @Test
    fun `what lies in a corner is found by that corner`() {
        assertEquals(dagger, world.lyingAt(level, here, SquarePlace.NORTH_WEST))
        assertNull(world.lyingAt(level, here, SquarePlace.NORTH_EAST))
        assertNull(world.lyingAt(level, Location(5, 7), SquarePlace.NORTH_WEST))
        assertNull(world.lyingAt(level + 1, here, SquarePlace.NORTH_WEST))
    }

    /**
     * What is picked up is being carried, which is not the same as not being
     * there at all: a slot of the table that holds nothing is marked another
     * way, and something in a hand is still something.
     */
    @Test
    fun `taking something up carries it and takes it off the square`() {
        val taken = world.takingUp(dagger)

        assertEquals(dagger, taken.inHand)
        assertEquals(Item.CARRIED, taken.item(dagger)?.location)
        assertEquals(Item.CARRIED_LEVEL, taken.item(dagger)?.level)
        assertTrue(taken.item(dagger)?.exists == true)
        assertNull(taken.lyingAt(level, here, SquarePlace.NORTH_WEST))
    }

    @Test
    fun `putting it down again leaves it where it was put`() {
        val moved = world.takingUp(dagger)
            .puttingDown(level, Location(7, 8), SquarePlace.SOUTH_WEST)

        assertTrue(!moved.inHand.isSomething)
        assertEquals(ItemIndex(1), moved.lyingAt(level, Location(7, 8), SquarePlace.SOUTH_WEST))
        assertEquals(level, moved.item(dagger)?.level)
    }

    /** A hand with nothing in it has nothing to put down. */
    @Test
    fun `putting down an empty hand changes nothing`() {
        assertSame(world, world.puttingDown(level, here, SquarePlace.NORTH_WEST))
    }

    /**
     * Two things in one corner come up one at a time, so a pile does not
     * vanish into a single click.
     */
    @Test
    fun `only one of two in a corner comes up at a time`() {
        val two = world.copy(items = world.items + lying(here, SquarePlace.NORTH_WEST))
        val taken = two.takingUp(two.lyingAt(level, here, SquarePlace.NORTH_WEST)!!)

        assertEquals(ItemIndex(2), taken.lyingAt(level, here, SquarePlace.NORTH_WEST))
    }
}
