package pl.pelotasplus.eyeofbeholder.data

import pl.pelotasplus.eyeofbeholder.data.model.Champion
import pl.pelotasplus.eyeofbeholder.data.model.ChampionFlags
import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.GameState
import pl.pelotasplus.eyeofbeholder.data.model.HitPoints
import pl.pelotasplus.eyeofbeholder.data.model.Item
import pl.pelotasplus.eyeofbeholder.data.model.ItemIconId
import pl.pelotasplus.eyeofbeholder.data.model.ItemIndex
import pl.pelotasplus.eyeofbeholder.data.model.ItemNameId
import pl.pelotasplus.eyeofbeholder.data.model.ItemTypeId
import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.MonsterInstance
import pl.pelotasplus.eyeofbeholder.data.model.MonsterSlot
import pl.pelotasplus.eyeofbeholder.data.model.MonsterTypeId
import pl.pelotasplus.eyeofbeholder.data.model.PartyState
import pl.pelotasplus.eyeofbeholder.data.model.SquarePlace
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Going straight to a floor off a list, rather than walking down to it.
 *
 * Two things have to hold, and they pull in opposite directions. The party go
 * as they are — the same champions carrying the same things, which is the
 * whole point of jumping rather than starting again. And the floor is peopled
 * as its file has it, because nobody walked in: there is no way in to come
 * back through, and a memory of a floor is a memory of having been there,
 * which the party have not.
 *
 * The second of those is what makes a jump different from stairs, and it is
 * deliberate. A floor remembered is a floor whose monsters were filed under
 * the sublevel somebody left it by; a jump arrives at the first sublevel
 * whatever they did last time. Taking the memory would leave that floor's
 * monsters standing where the view does not draw them — and nothing but the
 * drawing asks which sublevel a monster is on, so they would go on biting.
 */
class JumpingStraightToAFloorTest {

    @Test
    fun `the party arrive carrying what they carried`() {
        val before = onTheFifteenthFloor()

        val after = before.jumpingTo(
            level = 2,
            places = twoMonsters(),
            subLevel = 0,
            kinds = emptyList(),
        )

        assertEquals(before.champions, after.champions, "the party were swapped out")
        assertEquals(before.items, after.items, "the world's things changed under them")
        assertEquals(before.inHand, after.inHand, "what was in hand was dropped")
    }

    @Test
    fun `the floor is peopled as its file has it`() {
        val after = onTheFifteenthFloor().jumpingTo(
            level = 2,
            places = twoMonsters(),
            subLevel = 0,
            kinds = emptyList(),
        )

        assertEquals(2, after.monsters.size, "the file's monsters did not arrive")
        assertTrue(
            after.monsters.all { it.level == 2 },
            "monsters arrived still belonging to another floor",
        )
    }

    /**
     * Every one of them standing on the sublevel being looked at, which is
     * what makes them visible: the view draws a monster only where its
     * sublevel is the one showing.
     */
    @Test
    fun `every monster stands on the sublevel arrived at`() {
        val after = onTheFifteenthFloor().jumpingTo(
            level = 2,
            places = twoMonsters(),
            subLevel = 0,
            kinds = emptyList(),
        )

        assertTrue(
            after.monsters.all { it.subLevel == 0 },
            "a monster arrived on a sublevel nobody is looking at: " +
                after.monsters.map { it.subLevel },
        )
    }

    /**
     * And a floor the party had been on before is peopled afresh all the same.
     *
     * This is the case that sent invisible monsters after them: the memory was
     * taken, and it named a sublevel they were not arriving at.
     */
    @Test
    fun `a floor already visited is peopled from its file, not from memory`() {
        val remembered = MonsterInstance(
            index = MonsterSlot(9),
            unit = 0,
            location = Location(30, 30),
            place = SquarePlace.MIDDLE,
            direction = Direction.SOUTH,
            type = MonsterTypeId(0),
            gfxIndex = 0,
            mode = 0,
            pause = 0,
            weapon = 0,
            pocketItem = 0,
            hitPoints = HitPoints(1, 1),
            subLevel = 1,
            level = 2,
        )

        val after = onTheFifteenthFloor()
            .copy(monsters = listOf(remembered))
            .leaving(2)
            .jumpingTo(level = 2, places = twoMonsters(), subLevel = 0, kinds = emptyList())

        assertTrue(
            after.monsters.none { it.index == MonsterSlot(9) },
            "the remembered one came back, on a sublevel nobody is looking at",
        )
        assertEquals(2, after.monsters.size)
    }

    // --- the fixture -------------------------------------------------------

    private fun onTheFifteenthFloor(): GameState {
        val carrying = List(27) { ItemIndex(ItemIndex.NOTHING) }
            .toMutableList()
            .also { it[0] = ItemIndex(1) }

        return GameState(
            party = PartyState(Location(10, 10), Direction.NORTH),
            champions = listOf(
                Champion.NOBODY.copy(
                    name = "Anselm",
                    flags = ChampionFlags(1),
                    hitPoints = HitPoints(40, 40),
                    carrying = carrying,
                ),
            ),
            items = listOf(aScroll(), aScroll()),
            inHand = ItemIndex(1),
        )
    }

    private fun aScroll() = Item(
        nameUnidentified = ItemNameId(17),
        nameIdentified = ItemNameId(17),
        flags = 0,
        icon = ItemIconId(0),
        type = ItemTypeId(26),
        place = SquarePlace.MIDDLE,
        location = Item.CARRIED,
        next = 0,
        prev = 0,
        level = Item.CARRIED_LEVEL,
        value = 49,
    )

    private fun twoMonsters() = listOf(
        MonsterInstance(
            index = MonsterSlot(0),
            unit = 0,
            location = Location(3, 3),
            place = SquarePlace.MIDDLE,
            direction = Direction.SOUTH,
            type = MonsterTypeId(0),
            gfxIndex = 0,
            mode = 0,
            pause = 0,
            weapon = 0,
            pocketItem = 0,
            hitPoints = HitPoints(10, 10),
        ),
        MonsterInstance(
            index = MonsterSlot(1),
            unit = 0,
            location = Location(4, 4),
            place = SquarePlace.MIDDLE,
            direction = Direction.SOUTH,
            type = MonsterTypeId(0),
            gfxIndex = 0,
            mode = 0,
            pause = 0,
            weapon = 0,
            pocketItem = 0,
            hitPoints = HitPoints(10, 10),
        ),
    )
}
