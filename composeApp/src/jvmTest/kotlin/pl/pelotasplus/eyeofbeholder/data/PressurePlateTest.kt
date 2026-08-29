package pl.pelotasplus.eyeofbeholder.data

import kotlinx.coroutines.runBlocking
import org.junit.experimental.categories.Category
import pl.pelotasplus.eyeofbeholder.NeedsGameData
import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.GameState
import pl.pelotasplus.eyeofbeholder.data.model.Item
import pl.pelotasplus.eyeofbeholder.data.model.ItemIconId
import pl.pelotasplus.eyeofbeholder.data.model.ItemNameId
import pl.pelotasplus.eyeofbeholder.data.model.ItemTypeId
import pl.pelotasplus.eyeofbeholder.data.model.LevelScriptRunner
import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.Maz
import pl.pelotasplus.eyeofbeholder.data.model.PartyState
import pl.pelotasplus.eyeofbeholder.data.model.ScriptEvent
import pl.pelotasplus.eyeofbeholder.data.model.SquarePlace
import pl.pelotasplus.eyeofbeholder.data.model.WallSide
import pl.pelotasplus.eyeofbeholder.data.repository.CpsRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.DecRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.InfRepositoryImpl
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
 * A plate set into the floor, which weighs what is on it and works a door.
 *
 * Level 1's first is at 11x8 and the door it works is at 13x8. Its script is
 * two questions, each answered by opening or closing that door:
 *
 * ```
 * 385  the party stepped on and nothing lies here
 *      or something was put down and one thing lies here and the party are off it
 * 429  open the door at 13x8
 * 432  the party stepped off and nothing lies here
 *      or something was taken and nothing lies here and the party are off it
 *      -> close it again
 * ```
 *
 * So the plate answers a foot or a weight, and the two are interchangeable —
 * which is the puzzle: something has to stay on it while the party walk away.
 */
@Category(NeedsGameData::class)
class PressurePlateTest {

    private val resources = ResourceRepositoryImpl()

    private val level = runBlocking {
        InfRepositoryImpl(
            resourceRepository = resources,
            mazRepository = MazRepositoryImpl(resources),
            vmpRepository = VmpRepositoryImpl(resources),
            vcnRepository = VcnRepositoryImpl(resources),
            palRepository = PalRepositoryImpl(resources),
            cpsRepository = CpsRepositoryImpl(resources),
            decRepository = DecRepositoryImpl(resources),
        ).loadInf("LEVEL1.INF").getOrThrow()
    }

    private val plate = Location(11, 8)
    private val doorway = Location(13, 8)

    private fun world(standingOn: Location, itemsOnThePlate: Int = 0) = GameState(
        party = PartyState(standingOn, Direction.EAST),
        items = List(itemsOnThePlate) { onThePlate },
    ).arrivingAt(level = 1, places = emptyList(), maz = level.subLevels[0].maz)

    private val onThePlate = Item(
        nameUnidentified = ItemNameId(0),
        nameIdentified = ItemNameId(0),
        flags = 0,
        icon = ItemIconId(3),
        type = ItemTypeId(0),
        place = SquarePlace.NORTH_WEST,
        location = Location(11, 8),
        next = 0,
        prev = 0,
        level = 1,
        value = 0,
    )

    /**
     * The world once the script has run and the doors it started have arrived.
     *
     * A script sets a door going and ends without waiting for it, so the state
     * it hands back has the door one position along and still travelling.
     * What this puzzle is about is where the door ends up, not how it got
     * there, so the clock that would move it in the game is wound on here.
     */
    private fun ran(event: ScriptEvent, from: GameState, at: Location) = runBlocking {
        var world = LevelScriptRunner(level.script, level = 1).onEvent(
            triggers = level.triggers,
            event = event,
            state = from,
            at = at,
        ).state

        while (world.swinging.isNotEmpty()) world = world.doorsStepped().world
        world
    }

    private fun GameState.theDoor() =
        wall(1, doorway, WallSide.EAST) as Maz.WallType.Door

    @Test
    fun `the door starts shut`() {
        assertEquals(0, world(standingOn = plate).theDoor().state)
    }

    @Test
    fun `standing on the plate opens the door`() {
        val stoodOn = ran(ScriptEvent.PARTY_ENTERED, world(standingOn = plate), plate)

        assertTrue(stoodOn.theDoor().isOpen, "the door did not open")
    }

    /** And stepping off it again closes it, which is what the puzzle is about. */
    @Test
    fun `stepping off closes it`() {
        val stoodOn = ran(ScriptEvent.PARTY_ENTERED, world(standingOn = plate), plate)
        val steppedOff = ran(ScriptEvent.PARTY_LEFT, stoodOn, plate)

        assertEquals(0, steppedOff.theDoor().state, "the door stayed open")
    }

    /**
     * A weight does instead of a foot: something put on the plate opens the
     * door with the party standing elsewhere, and it stays open when they walk
     * away — the plate asks how many things lie on it, not what they are.
     */
    @Test
    fun `something left on the plate holds the door open`() {
        val weighted = world(standingOn = doorway, itemsOnThePlate = 1)

        val putDown = ran(ScriptEvent.ITEM_PUT_DOWN, weighted, plate)
        assertTrue(putDown.theDoor().isOpen, "a weight did not open the door")

        val walkedOff = ran(ScriptEvent.PARTY_LEFT, putDown, plate)
        assertTrue(walkedOff.theDoor().isOpen, "the weight did not hold it open")
    }

    /**
     * The level's other floor button, at 6x10, which looks like this one in
     * the floor and is not the same thing. Its flag word takes the party
     * arriving and nothing else, and its whole script is one instruction:
     * open the door at 6x8. Nothing anywhere on the level shuts that door
     * again, so there is nothing for a weight to hold — and a weight is not
     * something the button would feel in any case.
     */
    @Test
    fun `the button at 6x10 opens its door for good and ignores a weight`() {
        val button = Location(6, 10)
        val itsDoorway = Location(6, 8)
        val trigger = level.triggers.first { it.location == button }

        assertTrue(trigger.flags.reactsTo(ScriptEvent.PARTY_ENTERED))
        listOf(ScriptEvent.PARTY_LEFT, ScriptEvent.ITEM_PUT_DOWN, ScriptEvent.ITEM_TAKEN)
            .forEach { assertFalse(trigger.flags.reactsTo(it), "$it works the button") }

        fun GameState.itsDoor() = wall(1, itsDoorway, WallSide.SOUTH) as Maz.WallType.Door

        val dropped = ran(ScriptEvent.ITEM_PUT_DOWN, world(standingOn = itsDoorway), button)
        assertEquals(0, dropped.itsDoor().state, "a weight worked a button that takes a foot")

        val stoodOn = ran(ScriptEvent.PARTY_ENTERED, world(standingOn = button), button)
        assertTrue(stoodOn.itsDoor().isOpen, "the button did not open the door")

        val steppedOff = ran(ScriptEvent.PARTY_LEFT, stoodOn, button)
        assertTrue(steppedOff.itsDoor().isOpen, "the door shut behind the party")
    }

    /** Take the weight off and it shuts again. */
    @Test
    fun `taking it away closes the door`() {
        val weighted = world(standingOn = doorway, itemsOnThePlate = 1)
        val putDown = ran(ScriptEvent.ITEM_PUT_DOWN, weighted, plate)

        val takenBack = ran(
            ScriptEvent.ITEM_TAKEN,
            putDown.copy(items = emptyList()),
            plate,
        )

        assertEquals(0, takenBack.theDoor().state, "the door stayed open")
    }
}
