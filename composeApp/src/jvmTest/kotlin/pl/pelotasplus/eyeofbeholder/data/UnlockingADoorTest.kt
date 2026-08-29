package pl.pelotasplus.eyeofbeholder.data

import kotlinx.coroutines.runBlocking
import org.junit.experimental.categories.Category
import pl.pelotasplus.eyeofbeholder.NeedsGameData
import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.GameState
import pl.pelotasplus.eyeofbeholder.data.model.ItemIndex
import pl.pelotasplus.eyeofbeholder.data.model.LevelScriptRunner
import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.Maz
import pl.pelotasplus.eyeofbeholder.data.model.PartyState
import pl.pelotasplus.eyeofbeholder.data.model.ScriptEvent
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
 * Unlocking a door with the key it asks for.
 *
 * A keyhole's script asks what the hand holds and compares it against a type
 * and a value; level 2 has seven locks and six of them name a key that exists.
 * The Skull Key is the shortest of them to reach: it lies on the same floor as
 * three of its own locks.
 */
@Category(NeedsGameData::class)
class UnlockingADoorTest {

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
            ).loadInf("LEVEL2.INF").getOrThrow()
        }
    }

    private val dungeonItems by lazy {
        runBlocking { ItemsRepositoryImpl(resources).loadItems().getOrThrow().items }
    }

    /** The keyhole beside the locked door at 2x8, worked from the square east of it. */
    private val keyhole = Location(2, 9)
    private val lockedDoor = Location(2, 8)

    /** The one lying on this floor at 9x4, which is what this lock wants. */
    private val skullKey = ItemIndex(130)

    /** And the one at 14x3, which is the wrong shape for it. */
    private val darkMoonKey = ItemIndex(141)

    private fun holding(key: ItemIndex?) = GameState(
        party = PartyState(keyhole, Direction.WEST),
        items = dungeonItems,
        inHand = key ?: ItemIndex(ItemIndex.NOTHING),
    ).arrivingAt(level = 2, places = emptyList(), maz = level.subLevels[0].maz)

    private fun clicked(world: GameState) = runBlocking {
        LevelScriptRunner(level.script, level = 2).onEvent(
            triggers = level.triggers,
            event = ScriptEvent.WALL_CLICKED,
            state = world,
            at = keyhole,
        ).state
    }

    private fun GameState.theDoor() = wall(2, lockedDoor, WallSide.EAST) as Maz.WallType.Door

    @Test
    fun `the skull key is what this lock asks for`() {
        val key = dungeonItems[skullKey.value]

        assertEquals(38, key.type.value, "keys are type 38")
        assertEquals(5, key.value, "and this lock wants the one worth 5")
    }

    @Test
    fun `the right key sets the door going`() {
        val opened = clicked(holding(skullKey))

        assertEquals(1, opened.swinging.size, "the door was not started")
        assertEquals(lockedDoor, opened.swinging.first().at)
    }

    /** And the lock keeps it: a key opens its door once. */
    @Test
    fun `the right key is taken out of the hand`() {
        val opened = clicked(holding(skullKey))

        assertFalse(opened.inHand.isSomething, "the hand still holds something")
        assertFalse(opened.items[skullKey.value].exists, "the key is still in the world")
    }

    @Test
    fun `the wrong key leaves the door alone and is not taken`() {
        val refused = clicked(holding(darkMoonKey))

        assertEquals(emptyList(), refused.swinging, "the door moved")
        assertEquals(darkMoonKey, refused.inHand, "the key was taken anyway")
        assertTrue(refused.items[darkMoonKey.value].exists)
    }

    /**
     * An empty hand is slot zero of the table rather than nothing, so the lock
     * is answered rather than skipped — and answered wrongly for a key.
     */
    @Test
    fun `an empty hand opens nothing`() {
        val nothing = clicked(holding(key = null))

        assertEquals(emptyList(), nothing.swinging)
    }

    /** What was already open is not worked again. */
    @Test
    fun `a door already open is left where it is`() {
        val open = (1..Maz.WallType.Door.TRAVEL).fold(holding(skullKey)) { world, _ ->
            world.doorStepped(2, lockedDoor, WallSide.EAST, opening = true)
        }

        val again = clicked(open)

        assertEquals(emptyList(), again.swinging)
        assertEquals(skullKey, again.inHand, "the key was spent on an open door")
    }
}
