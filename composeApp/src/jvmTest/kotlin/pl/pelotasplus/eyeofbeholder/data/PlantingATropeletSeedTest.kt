package pl.pelotasplus.eyeofbeholder.data

import kotlinx.coroutines.runBlocking
import org.junit.experimental.categories.Category
import pl.pelotasplus.eyeofbeholder.NeedsGameData
import pl.pelotasplus.eyeofbeholder.data.model.ClickedWall
import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.FloorReach
import pl.pelotasplus.eyeofbeholder.data.model.GameState
import pl.pelotasplus.eyeofbeholder.data.model.ItemIndex
import pl.pelotasplus.eyeofbeholder.data.model.LevelScriptRunner
import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.PartyState
import pl.pelotasplus.eyeofbeholder.data.model.ScriptEvent
import pl.pelotasplus.eyeofbeholder.data.model.WallByte
import pl.pelotasplus.eyeofbeholder.data.model.WallSide
import pl.pelotasplus.eyeofbeholder.data.repository.CpsRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.DecRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.InfRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.ItemsRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.MazRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.PalRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.ResourceRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.VcnRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.VmpRepositoryImpl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The soft earth in the tropelet chamber on level 6, which a seed planted in
 * it turns into a way back to 15x19.
 *
 * Its script asks the hand: a click on the earth holding something of kind 40
 * worth 4 takes it and makes every side of the square wall 44, which is what
 * the party then walk into to be moved. A seed put down on the floor has
 * already left the hand by the time the square hears of it, so that is
 * answered only with the line about the floor feeling soft.
 */
@Category(NeedsGameData::class)
class PlantingATropeletSeedTest {

    private val resources = ResourceRepositoryImpl()

    private val level by lazy {
        runBlocking {
            InfRepositoryImpl(
                resourceRepository = resources,
                mazRepository = MazRepositoryImpl(resources),
                vmpRepository = VmpRepositoryImpl(resources),
                vcnRepository = VcnRepositoryImpl(resources),
                palRepository = PalRepositoryImpl(resources),
                cpsRepository = CpsRepositoryImpl(resources),
                decRepository = DecRepositoryImpl(resources),
            ).loadInf("LEVEL6.INF").getOrThrow()
        }
    }

    private val dungeonItems by lazy {
        runBlocking { ItemsRepositoryImpl(resources).loadItems().getOrThrow().items }
    }

    private val softEarth = Location(23, 25)
    private val seed = ItemIndex(260)

    private fun standingSouthOfIt(held: ItemIndex = ItemIndex(ItemIndex.NOTHING)) = GameState(
        party = PartyState(Location(23, 26), Direction.NORTH),
        items = dungeonItems,
        inHand = held,
    ).arrivingAt(level = LEVEL, places = emptyList(), maz = level.subLevels[0].maz)

    private fun run(world: GameState, event: ScriptEvent) = runBlocking {
        LevelScriptRunner(level.script, level = LEVEL).onEvent(
            triggers = level.triggers,
            event = event,
            state = world,
            at = softEarth,
        ).state
    }

    private fun GameState.sidesOfTheEarth() =
        WallSide.entries.map { wallByte(LEVEL, softEarth, it) }.toSet()

    @Test
    fun `item 260 is a seed of the kind the earth asks for`() {
        val item = dungeonItems[seed.value]

        assertEquals(40, item.type.value)
        assertEquals(4, item.value)
    }

    @Test
    fun `clicking the earth holding a seed plants it`() {
        val planted = run(standingSouthOfIt(held = seed), ScriptEvent.WALL_CLICKED)

        assertEquals(setOf(WallByte(TELEPORTER)), planted.sidesOfTheEarth())
        assertFalse(planted.inHand.isSomething, "the seed is still in the hand")
    }

    @Test
    fun `a seed put down on the earth leaves it as it was`() {
        val before = standingSouthOfIt()
        val after = run(before, ScriptEvent.ITEM_PUT_DOWN)

        assertEquals(before.sidesOfTheEarth(), after.sidesOfTheEarth())
    }

    @Test
    fun `walking onto the planted earth moves the party to 15x19`() {
        val planted = run(standingSouthOfIt(held = seed), ScriptEvent.WALL_CLICKED)
        val walkedIn = run(planted.copy(party = PartyState(softEarth, Direction.NORTH)), ScriptEvent.PARTY_ENTERED)

        assertEquals(Location(15, 19), walkedIn.party.position)
    }

    /**
     * The earth lies on the floor ahead, so it is drawn over floor a held seed
     * could be put down on. It can be planted only because the wall
     * is asked first there.
     */
    @Test
    fun `the earth is clicked before the floor under it`() {
        val earth = level.subLevels[0].decorations.first { it.decorationWallIndex == SOFT_EARTH_WALL }
        val picture = earth.dec.decorations.first { it.index == earth.decorationID }

        val onIt = (0 until 120).flatMap { y -> (0 until 176).map { x -> x to y } }
            .filter { (x, y) -> ClickedWall.hits(picture, earth.dec.rectangles, x, y) }

        assertTrue(onIt.any { (x, y) -> FloorReach.at(x, y) != null })
        assertTrue(onIt.all { (x, y) -> ClickedWall.comesBeforeTheFloor(x, y) })
    }

    private companion object {
        const val LEVEL = 6
        const val TELEPORTER = 44
        const val SOFT_EARTH_WALL = 61
    }
}
