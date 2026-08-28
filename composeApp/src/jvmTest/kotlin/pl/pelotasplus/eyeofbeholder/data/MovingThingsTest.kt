package pl.pelotasplus.eyeofbeholder.data

import kotlinx.coroutines.runBlocking
import pl.pelotasplus.eyeofbeholder.data.model.Dice
import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.GameState
import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.ItemTypeId
import pl.pelotasplus.eyeofbeholder.data.model.MonsterInstance
import pl.pelotasplus.eyeofbeholder.data.model.MonsterSlot
import pl.pelotasplus.eyeofbeholder.data.model.MonsterTypeId
import pl.pelotasplus.eyeofbeholder.data.model.PartyState
import pl.pelotasplus.eyeofbeholder.data.model.SquarePlace
import pl.pelotasplus.eyeofbeholder.data.repository.ItemsRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.ResourceRepositoryImpl
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A script carrying the things on a square to another one.
 *
 * Level 1's keyhole room keeps three of item type 26 on the square at 15x0,
 * off the edge of the map, and a lever there moves them onto 16x2 — the store
 * a puzzle draws from. The expected numbers are the level's own.
 */
class MovingThingsTest {

    private val resources = ResourceRepositoryImpl()
    private val dungeonItems = runBlocking {
        ItemsRepositoryImpl(resources).loadItems().getOrThrow().items
    }

    private val world get() = GameState(
        party = PartyState(Location(1, 1), Direction.NORTH),
        items = dungeonItems,
    )

    private fun GameState.count(type: Int?, at: Location, onLevel: Int = 1) =
        items.count { it.level == onLevel && it.location == at && (type == null || it.type.value == type) }

    @Test
    fun `a lever carries the items of its type to another square`() {
        assertEquals(3, world.count(type = 26, at = Location(15, 0)), "not where the level keeps them")

        val after = world.itemsMoved(
            ofType = ItemTypeId(26), fromLevel = 1, from = Location(15, 0), toLevel = 1, to = Location(16, 2),
        )

        assertEquals(0, after.count(type = 26, at = Location(15, 0)), "left some behind")
        assertEquals(3, after.count(type = 26, at = Location(16, 2)), "did not arrive")
    }

    @Test
    fun `only the named type is carried`() {
        val store = Location(15, 0)
        val mixed = world.itemsMoved(
            ofType = ItemTypeId(999), fromLevel = 1, from = store, toLevel = 1, to = Location(16, 2),
        )

        assertEquals(3, mixed.count(type = 26, at = store), "carried the wrong kind")
    }

    @Test
    fun `moving every kind clears the square`() {
        val store = Location(15, 0)
        val after = world.itemsMoved(
            ofType = null, fromLevel = 1, from = store, toLevel = 1, to = Location(16, 2),
        )

        assertEquals(0, after.count(type = null, at = store), "something stayed")
    }

    // --- what a monster leaves behind -----------------------------------------

    /** A monster carrying [pocket] as its fixed thing and [weapon] as its chance one. */
    private fun monsterOn(at: Location, pocket: Int = 0, weapon: Int = 0) = MonsterInstance(
        index = MonsterSlot(1), unit = 0, location = at, place = SquarePlace.NORTH_WEST,
        direction = Direction.NORTH, type = MonsterTypeId(0), gfxIndex = 0,
        mode = 0, pause = 0, weapon = weapon, pocketItem = pocket,
    )

    /** Rolls its lowest, so the one-in-ten comes up. */
    private val theTenthTime = Dice { times, _, modifier -> times + modifier }

    /** Rolls its highest, so it does not. */
    private val notThisTime = Dice { times, pips, modifier -> times * pips + modifier }

    @Test
    fun `a slain monster leaves its fixed thing on the square`() {
        // level 1's guards carry item 17 (type 38, a key) as the thing they always drop
        val monster = monsterOn(Location(10, 10), pocket = 17)

        val after = world.copy(monsters = listOf(monster))
            .whatAMonsterDrops(monster, level = 1, dice = notThisTime)

        assertEquals(1, after.count(type = 38, at = Location(10, 10)), "the key was not left")
    }

    @Test
    fun `its chance thing falls one time in ten and no other`() {
        // item 34 is type 1, the thing some level 1 monsters only might drop
        val monster = monsterOn(Location(10, 10), weapon = 34)
        val start = world.copy(monsters = listOf(monster))

        assertEquals(1, start.whatAMonsterDrops(monster, 1, theTenthTime).count(type = 1, at = Location(10, 10)))
        assertEquals(0, start.whatAMonsterDrops(monster, 1, notThisTime).count(type = 1, at = Location(10, 10)))
    }
}
