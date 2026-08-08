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

/**
 * Which of several things in one corner the party come away holding.
 *
 * A file lays its items out in table order, each one put down on what came
 * before it, so the last slot is on top; from then on it is whatever was put
 * down most recently. Either way the screen is painted in the same order and
 * the hand closes on the end of it.
 *
 * Reading the two from separate rules is not a near miss: you see one thing,
 * take another, and the pile comes off backwards.
 */
class WhatIsOnTopTest {

    private val here = Location(9, 4)
    private val corner = SquarePlace.NORTH_EAST
    private val level = 2

    /** Three in one corner, in the order the table holds them. */
    private val robe = ItemIndex(1)
    private val dagger = ItemIndex(2)
    private val key = ItemIndex(3)

    private val world = GameState(
        party = PartyState(here, Direction.NORTH),
        items = listOf(nothing(), lying(icon = 10), lying(icon = 20), lying(icon = 30)),
    )

    @Test
    fun `the last of the table is the one taken`() {
        assertEquals(key, world.lyingAt(level, here, corner))
    }

    /** And the pile comes off from the top down, not from underneath. */
    @Test
    fun `a pile is taken from the top`() {
        val taken = mutableListOf<ItemIndex>()

        var left = world
        while (true) {
            val next = left.lyingAt(level, here, corner) ?: break
            taken += next
            left = left.takingUp(next).handEmptied()
        }

        assertEquals(listOf(key, dagger, robe), taken)
    }

    /**
     * A thing put down lands on what is already there, whichever slot of the
     * table it happens to occupy — the robe is the first of the three and
     * still covers them once it has been dropped on them.
     */
    @Test
    fun `what is put down lies on top of what was there`() {
        val dropped = world.takingUp(robe).puttingDown(level, here, corner)

        assertEquals(robe, dropped.lyingAt(level, here, corner))
    }

    /** And a pile put down comes back up in the order it went down. */
    @Test
    fun `a pile put down comes off in the order it was made`() {
        var world = this.world
        for (slot in listOf(key, robe, dagger)) {
            world = world.takingUp(slot).puttingDown(level, here, corner)
        }

        val taken = mutableListOf<ItemIndex>()
        while (true) {
            val next = world.lyingAt(level, here, corner) ?: break
            taken += next
            world = world.takingUp(next).handEmptied()
        }

        assertEquals(listOf(dagger, robe, key), taken)
    }

    /** What lies elsewhere on the same square is not what is under the hand. */
    @Test
    fun `another corner is another pile`() {
        val alsoHere = world.copy(
            items = world.items + lying(icon = 40, place = SquarePlace.SOUTH_WEST),
        )

        assertEquals(key, alsoHere.lyingAt(level, here, corner))
        assertEquals(
            ItemIndex(4),
            alsoHere.lyingAt(level, here, SquarePlace.SOUTH_WEST),
        )
    }

    private fun lying(icon: Int, place: SquarePlace = corner) = Item(
        nameUnidentified = ItemNameId(0),
        nameIdentified = ItemNameId(0),
        flags = 0,
        icon = ItemIconId(icon),
        type = ItemTypeId(0),
        place = place,
        location = here,
        next = 0,
        prev = 0,
        level = level,
        value = 0,
    )

    private fun nothing() = lying(icon = 0).copy(location = Item.NOWHERE, level = 0)
}
