package pl.pelotasplus.eyeofbeholder.data

import pl.pelotasplus.eyeofbeholder.data.model.CarrySlot
import pl.pelotasplus.eyeofbeholder.data.model.Champion
import pl.pelotasplus.eyeofbeholder.data.model.ChampionFlags
import pl.pelotasplus.eyeofbeholder.data.model.Dice
import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.FlagBit
import pl.pelotasplus.eyeofbeholder.data.model.GameState
import pl.pelotasplus.eyeofbeholder.data.model.HitPoints
import pl.pelotasplus.eyeofbeholder.data.model.Item
import pl.pelotasplus.eyeofbeholder.data.model.ItemIconId
import pl.pelotasplus.eyeofbeholder.data.model.ItemIndex
import pl.pelotasplus.eyeofbeholder.data.model.ItemNameId
import pl.pelotasplus.eyeofbeholder.data.model.ItemTypeId
import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.NpcId
import pl.pelotasplus.eyeofbeholder.data.model.NpcMeeting
import pl.pelotasplus.eyeofbeholder.data.model.PartyState
import pl.pelotasplus.eyeofbeholder.data.model.SquarePlace
import pl.pelotasplus.eyeofbeholder.data.model.insalGoneInTheNight
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What the game's own rule says: a living Insal in a party of five or more
 * goes, taking a table of things and leaving a note made from item 28, valued 26.
 */
class InsalLeavingTest {

    private val here = Location(9, 9)
    private val insal = requireNotNull(NpcMeeting.joiningAs(NpcId(0)))

    private fun someone(name: String, vararg carried: Int) = Champion.NOBODY.copy(
        name = name,
        flags = ChampionFlags(1),
        hitPoints = HitPoints(30, 30),
        carrying = CarrySlot.NOTHING_IN_ANY.toMutableList().also { pack ->
            carried.forEachIndexed { slot, what -> pack[slot] = ItemIndex(what) }
        },
    )

    private val blank = Item(
        nameUnidentified = ItemNameId(0),
        nameIdentified = ItemNameId(0),
        flags = 0,
        icon = ItemIconId(1),
        type = ItemTypeId(0),
        place = SquarePlace.MIDDLE,
        location = Item.NOWHERE,
        next = 0,
        prev = 0,
        level = 0,
        value = 0,
    )

    private fun held(type: Int, value: Int) = blank.copy(
        type = ItemTypeId(type),
        value = value,
        location = Item.CARRIED,
        level = Item.CARRIED_LEVEL,
    )

    /** Index 28 is the note's template; everything else the party might hold sits at 1 and on. */
    private fun world(champions: List<Champion>, vararg things: Item): GameState {
        val table = MutableList(40) { blank }
        things.forEachIndexed { at, it -> table[at + 1] = it }
        table[28] = blank.copy(type = ItemTypeId(36), location = Location(0, 0), value = -1)
        return GameState(
            party = PartyState(here, Direction.NORTH),
            champions = champions,
            items = table,
        )
    }

    private val four = (1..4).map { someone("P$it") }

    private fun GameState.carriedTypes() =
        champions.filter { it.inTheParty }.flatMap { it.carrying }.filter { it.isSomething }
            .map { requireNotNull(item(it)).type.value }

    @Test
    fun `a party of five loses him, with the gap behind them`() {
        val after = assertNotNull(world(listOf(insal) + four).insalGoneInTheNight(LONG_ENOUGH, 3, Dice.random))

        assertFalse(after.champions.any { it.inTheParty && it.name == "Insal" })
        assertTrue(after.champions.take(4).all { it.inTheParty }, "the front was left with a hole")
    }

    @Test
    fun `a party of four keeps him`() {
        assertNull(world(listOf(insal) + four.take(3)).insalGoneInTheNight(LONG_ENOUGH, 3, Dice.random))
    }

    @Test
    fun `he has to be there, and able to walk`() {
        assertNull(world(four + someone("P5")).insalGoneInTheNight(LONG_ENOUGH, 3, Dice.random))

        val dead = insal.copy(hitPoints = HitPoints(Champion.BEYOND_RAISING, 39))
        assertNull(world(listOf(dead) + four).insalGoneInTheNight(LONG_ENOUGH, 3, Dice.random))
    }

    @Test
    fun `he takes what the table says and no more`() {
        val party = listOf(insal, someone("A", 1, 2, 3, 4, 5, 6), someone("B", 7, 8, 9, 10, 11)) + four.take(2)
        val after = assertNotNull(
            world(
                party,
                held(1, 1), held(1, 1), held(2, 1), // one of type 1, so type 2 is spared
                held(31, 25), held(31, 50), held(31, 25), // all three
                held(31, 50), held(31, -1), // the fourth is past his limit; -1 he never wants
                held(39, 1), held(39, 2), // only worth exactly 1
                held(47, 0),
            ).insalGoneInTheNight(LONG_ENOUGH, 3, Dice.random),
        )

        assertEquals(
            listOf(1, 2, 31, 31, 39).sorted(),
            after.carriedTypes().sorted(),
        )
    }

    @Test
    fun `no type 1 means a type 2 goes instead`() {
        val party = listOf(insal, someone("A", 1, 2)) + four.take(3)
        val after = assertNotNull(
            world(party, held(1, 0), held(2, 2)).insalGoneInTheNight(LONG_ENOUGH, 3, Dice.random),
        )

        assertEquals(listOf(1), after.carriedTypes())
    }

    @Test
    fun `what he owned stays, and a note lies at their feet`() {
        val his = insal.copy(carrying = CarrySlot.NOTHING_IN_ANY.toMutableList().also { it[0] = ItemIndex(1) })
        val after = assertNotNull(world(listOf(his) + four, held(1, 5)).insalGoneInTheNight(LONG_ENOUGH, 3, Dice.random))

        val onTheFloor = after.items.filter { it.exists && it.location == here && it.level == 3 }
        assertEquals(setOf(1, 36), onTheFloor.map { it.type.value }.toSet())
        assertEquals(26, onTheFloor.single { it.type.value == 36 }.value)
    }

    @Test
    fun `he clears two flags and not the one saying he joined`() {
        val before = world(listOf(insal) + four)
            .globalFlagSet(FlagBit(1)).globalFlagSet(FlagBit(2)).globalFlagSet(FlagBit(6))
        val after = assertNotNull(before.insalGoneInTheNight(LONG_ENOUGH, 3, Dice.random))

        assertFalse(after.isGlobalFlagSet(FlagBit(1)))
        assertFalse(after.isGlobalFlagSet(FlagBit(2)))
        assertTrue(after.isGlobalFlagSet(FlagBit(6)))
    }

    @Test
    fun `he waits for a rest of more than four hours`() {
        val party = world(listOf(insal) + four)

        assertNull(party.insalGoneInTheNight(4, 3, Dice.random))
        assertNotNull(party.insalGoneInTheNight(5, 3, Dice.random))
    }

    @Test
    fun `from the back row of a full party he leaves his gap`() {
        val six = four + insal + someone("Last")
        val after = assertNotNull(world(six).insalGoneInTheNight(LONG_ENOUGH, 3, Dice.random))

        assertFalse(after.champions[4].inTheParty)
        assertEquals("Last", after.champions[5].name)
    }

    private companion object {
        const val LONG_ENOUGH = 5
    }
}
