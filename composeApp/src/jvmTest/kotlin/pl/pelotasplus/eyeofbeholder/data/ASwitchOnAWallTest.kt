package pl.pelotasplus.eyeofbeholder.data

import kotlinx.coroutines.runBlocking
import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.GameState
import pl.pelotasplus.eyeofbeholder.data.model.Inf
import pl.pelotasplus.eyeofbeholder.data.model.LevelScriptRunner
import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.Maz
import pl.pelotasplus.eyeofbeholder.data.model.MonsterInstance
import pl.pelotasplus.eyeofbeholder.data.model.PartyState
import pl.pelotasplus.eyeofbeholder.data.model.ScriptEvent
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
import kotlin.test.assertTrue

/**
 * The lever on level 1's wall at 4x4, which works the door on 5x4.
 *
 * Its script is two instructions: work the switch on that square, and make a
 * noise. Working a switch is not opening a door — it is asking the door to do
 * whatever it is not doing, which is what lets one lever both open and shut.
 */
class ASwitchOnAWallTest {

    private val resources = ResourceRepositoryImpl()

    private val level: Inf = runBlocking {
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

    private val lever = Location(4, 4)
    private val doorway = Location(5, 4)

    private fun world(standingOn: Location = Location(4, 5)) = GameState(
        party = PartyState(standingOn, Direction.NORTH),
    ).arrivingAt(level = 1, places = emptyList(), maz = level.subLevels[0].maz)

    /** The world after the lever has been thrown, doors arrived. */
    private fun thrown(from: GameState, monsters: List<MonsterInstance> = emptyList()) =
        runBlocking {
            var world = LevelScriptRunner(level.script, level = 1).onEvent(
                triggers = level.triggers,
                event = ScriptEvent.WALL_CLICKED,
                state = from.copy(monsters = monsters),
                at = lever,
            ).state

            while (world.swinging.isNotEmpty()) world = world.doorsStepped().world
            world
        }

    /** Whichever of the square's faces the door is on, as the world has it. */
    private fun GameState.theDoor(): Maz.WallType.Door {
        val side = doorFacing(1, doorway) ?: error("no door at $doorway")
        return wall(1, doorway, side) as Maz.WallType.Door
    }

    @Test
    fun `the door on the next square starts shut`() {
        assertTrue(world().theDoor().isShut)
    }

    @Test
    fun `throwing the lever opens it`() {
        assertTrue(thrown(world()).theDoor().isOpen, "the lever did nothing")
    }

    /** And throwing it again shuts it, one lever doing both. */
    @Test
    fun `throwing it again shuts it`() {
        val opened = thrown(world())

        assertTrue(thrown(opened).theDoor().isShut, "the lever only works one way")
    }

    /**
     * A door is not shut on whatever is standing in it. The whole switch is
     * refused rather than half worked, so the lever does nothing at all while
     * something is there.
     */
    @Test
    fun `it will not shut a door with something standing in it`() {
        val opened = thrown(world())
        val standing = level.monsterInstances.first().copy(block = doorway.asBlock)

        assertTrue(
            thrown(opened, monsters = listOf(standing)).theDoor().isOpen,
            "the door came down on somebody",
        )
    }

    /** Nor is it worked from the square it would shut on the party. */
    @Test
    fun `it will not work the square the party stand on`() {
        val opened = thrown(world())
        val standingInTheDoorway = opened.copy(
            party = opened.party.copy(position = doorway),
        )

        assertEquals(
            opened.theDoor().state,
            thrown(standingInTheDoorway).theDoor().state,
            "the party shut the door on themselves",
        )
    }
}
