package pl.pelotasplus.eyeofbeholder.data

import kotlinx.coroutines.runBlocking
import pl.pelotasplus.eyeofbeholder.data.model.Champion
import pl.pelotasplus.eyeofbeholder.data.model.ClickedWall
import pl.pelotasplus.eyeofbeholder.data.model.Dice
import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.ForcingADoor
import pl.pelotasplus.eyeofbeholder.data.model.GameState
import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.Maz
import pl.pelotasplus.eyeofbeholder.data.model.PartyState
import pl.pelotasplus.eyeofbeholder.data.model.WallAction
import pl.pelotasplus.eyeofbeholder.data.model.WallSide
import pl.pelotasplus.eyeofbeholder.data.repository.CpsRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.DecRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.InfRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.MazRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.OriginalSaveRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.PalRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.ResourceRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.VcnRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.VmpRepositoryImpl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Working the walls that have something on them to work.
 *
 * A click on a wall usually only runs its script. These few do something
 * first: a lever flips, a door slides, a door stuck in its frame is forced.
 *
 * The dungeon has 162 levers of one kind and 2 of the other, 10 doors stuck
 * fast, and 51 niches. The stuck ones are on level 2 at 8x3, level 7 at 15x13
 * and level 8 at 12x20, each barring a corridor across both faces of its
 * square.
 */
class WorkingAWallTest {

    private val resources = ResourceRepositoryImpl()

    private fun level(name: String) = runBlocking {
        InfRepositoryImpl(
            resourceRepository = resources,
            mazRepository = MazRepositoryImpl(resources),
            vmpRepository = VmpRepositoryImpl(resources),
            vcnRepository = VcnRepositoryImpl(resources),
            palRepository = PalRepositoryImpl(resources),
            cpsRepository = CpsRepositoryImpl(resources),
            decRepository = DecRepositoryImpl(resources),
        ).loadInf(name).getOrThrow()
    }

    private fun world(name: String, on: Int, at: Location) = level(name).let { inf ->
        GameState(party = PartyState(at, Direction.NORTH))
            .arrivingAt(
                level = on,
                places = emptyList(),
                maz = inf.subLevels[0].maz,
            )
    }

    /** Level 8's doorway at 20x1, which has a button on both of its faces. */
    private val doorway = Location(20, 1)

    private fun theDoor(world: GameState) =
        world.wall(8, doorway, WallSide.EAST) as Maz.WallType.Door

    @Test
    fun `a door slides open a step at a time`() {
        var world = world("LEVEL8.INF", on = 8, at = doorway)

        val opening = (1..Maz.WallType.Door.TRAVEL).map {
            world = world.doorStepped(8, doorway, WallSide.EAST, opening = true)
            theDoor(world).state
        }

        assertEquals(listOf(1, 2, 3, 4), opening, "a door does not jump to open")
    }

    /** And stops there however often it is pushed. */
    @Test
    fun `a door stops at either end of its travel`() {
        var world = world("LEVEL8.INF", on = 8, at = doorway)

        repeat(Maz.WallType.Door.TRAVEL + 3) {
            world = world.doorStepped(8, doorway, WallSide.EAST, opening = true)
        }
        assertTrue(theDoor(world).isOpen)

        repeat(Maz.WallType.Door.TRAVEL + 3) {
            world = world.doorStepped(8, doorway, WallSide.EAST, opening = false)
        }
        assertEquals(0, theDoor(world).state)
    }

    /**
     * A doorway is one square with a door across both of its faces, and both
     * move together — otherwise it would stand open one way and shut the
     * other. Each keeps its own kind: the face with the button on it does not
     * hand the button to the face without one.
     */
    @Test
    fun `both faces of a doorway move together`() {
        var world = world("LEVEL8.INF", on = 8, at = doorway)
        val faces = listOf(WallSide.EAST, WallSide.WEST)

        val before = faces.map { world.wall(8, doorway, it) as Maz.WallType.Door }
        repeat(Maz.WallType.Door.TRAVEL) {
            world = world.doorStepped(8, doorway, WallSide.EAST, opening = true)
        }
        val after = faces.map { world.wall(8, doorway, it) as Maz.WallType.Door }

        assertTrue(after.all { it.isOpen }, "one face was left behind: $after")
        assertEquals(
            before.map { it.hasButton },
            after.map { it.hasButton },
            "a face was given a button it never had",
        )
    }

    /**
     * A lever is two wall shapes side by side, one for each way it points, so
     * throwing it is a step from the one to the other. Level 1's first lever
     * is on the south face of 4x4.
     */
    @Test
    fun `a lever steps to its other shape and back`() {
        val lever = Location(4, 4)
        val world = world("LEVEL1.INF", on = 1, at = lever)

        val was = world.wallByte(1, lever, WallSide.SOUTH).value
        val thrown = world.leverThrown(1, lever, WallSide.SOUTH, up = true)
        assertEquals(was + 1, thrown.wallByte(1, lever, WallSide.SOUTH).value)

        val back = thrown.leverThrown(1, lever, WallSide.SOUTH, up = false)
        assertEquals(was, back.wallByte(1, lever, WallSide.SOUTH).value)
    }

    /**
     * Level 2's stuck door bars 8x3 on both faces. Forced, it becomes a real
     * doorway there — shut, so that it can be seen to swing — and one with no
     * button, the wall it replaces having had none to press.
     */
    @Test
    fun `a forced door becomes a doorway that is still shut`() {
        val stuck = Location(8, 3)
        val world = world("LEVEL2.INF", on = 2, at = stuck)

        assertTrue(
            world.wall(2, stuck, WallSide.NORTH) is Maz.WallType.Decoration,
            "a door stuck fast is a wall with a picture of a door on it",
        )

        val forced = world.forcedOutOfItsFrame(2, stuck, WallSide.NORTH)

        listOf(WallSide.NORTH, WallSide.SOUTH).forEach { face ->
            val door = forced.wall(2, stuck, face)
            assertTrue(door is Maz.WallType.Door, "$face is not a doorway: $door")
            assertEquals(0, door.state, "$face should still be shut")
            assertTrue(!door.hasButton, "$face was given a button")
        }
    }

    /**
     * A door stuck in its frame is shoved rather than aimed at: what answers
     * is the doorway, which is the middle of the view, and not the picture of
     * a door hanging on the wall.
     */
    @Test
    fun `a stuck door is pushed by its doorway`() {
        assertTrue(WallAction.STUCK_DOOR.isShoved)
        assertTrue(ClickedWall.hitsTheDoorway(88, 60), "the middle of the doorway")
        assertTrue(ClickedWall.hitsTheDoorway(40, 16), "its top left corner")
        assertTrue(ClickedWall.hitsTheDoorway(136, 88), "its bottom right")
        assertTrue(!ClickedWall.hitsTheDoorway(88, 110), "the floor below it")
        assertTrue(!ClickedWall.hitsTheDoorway(10, 60), "the wall beside it")
    }

    /** Nobody standing and free to try is answered rather than rolled for. */
    @Test
    fun `a party who cannot try are told so`() {
        val outcome = ForcingADoor.tried(listOf(Champion.NOBODY), neverRolls)

        assertEquals(ForcingADoor.Outcome.NobodyCan, outcome)
    }

    /**
     * The throw is one twenty-sided die against a chance that runs from one in
     * twenty to twelve. The quick start party's strongest is PERICLES at 18,
     * so 11 gives and 12 does not.
     */
    @Test
    fun `whether it gives is the strongest champion's throw`() {
        val party = runBlocking {
            OriginalSaveRepositoryImpl(resources)
                .loadOriginalSave(OriginalSaveRepositoryImpl.QUICK_START)
                .getOrThrow()
                .party
        }

        assertEquals(18, party.filter { it.canAct }.maxOf { it.abilities.strength.current })
        assertEquals(ForcingADoor.Outcome.Gives, ForcingADoor.tried(party, throws(11)))
        assertEquals(ForcingADoor.Outcome.Holds, ForcingADoor.tried(party, throws(12)))
    }

    private fun throws(number: Int) = Dice { _, _, _ -> number }

    private val neverRolls = Dice { _, _, _ -> error("nobody can try, so nothing is thrown") }
}
