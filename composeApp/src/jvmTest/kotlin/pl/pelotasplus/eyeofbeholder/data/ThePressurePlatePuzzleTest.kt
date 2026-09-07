package pl.pelotasplus.eyeofbeholder.data

import kotlinx.coroutines.runBlocking
import org.junit.experimental.categories.Category
import pl.pelotasplus.eyeofbeholder.NeedsGameData
import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.Flight
import pl.pelotasplus.eyeofbeholder.data.model.GameState
import pl.pelotasplus.eyeofbeholder.data.model.Inf
import pl.pelotasplus.eyeofbeholder.data.model.ItemIndex
import pl.pelotasplus.eyeofbeholder.data.model.LevelScriptRunner
import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.Maz
import pl.pelotasplus.eyeofbeholder.data.model.PartySlot
import pl.pelotasplus.eyeofbeholder.data.model.PartyState
import pl.pelotasplus.eyeofbeholder.data.model.Projectile
import pl.pelotasplus.eyeofbeholder.data.model.ScriptEvent
import pl.pelotasplus.eyeofbeholder.data.model.SquarePlace
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
import kotlin.test.assertTrue

/**
 * The room on the twelfth floor that is worked by throwing things into it.
 *
 * Six plates are set into two alcoves nobody can walk into — three down the
 * west wall at 15x15, 15x17 and 15x19, three down the east at 21x15, 21x17
 * and 21x19. Weighing all six down at once opens the way north out of the
 * room. The only way to reach them is to throw something into one of the two
 * teleporters, at 19x13 and 17x21, and be carried there.
 *
 * Where it is carried to is set by the plates the party stand on first. The
 * three down the middle of the room — 18x16, 18x17, 18x18 — pick the row, and
 * the two beside them — 17x17 and 19x17 — pick the alcove, each by arming the
 * teleporter on its own side and disarming the other.
 *
 * What makes it work at all is that a thing carried by a teleporter is carried
 * while still in the air. It arrives over the alcove going the way the
 * teleporter turned it, flies into the wall a step later, and it is coming to
 * rest there — not the carrying — that presses the plate. Carry the thing
 * without carrying the flight and the party watch six plates fill up with
 * things while the door stays shut.
 */
@Category(NeedsGameData::class)
class ThePressurePlatePuzzleTest {

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
        ).loadInf("LEVEL12.INF").getOrThrow()
    }

    private val dungeonItems = runBlocking {
        ItemsRepositoryImpl(resources).loadItems().getOrThrow().items
    }

    // --- what a single throw does --------------------------------------------

    @Test
    fun `the middle plate picks which row of the alcove the throw reaches`() {
        val reached = MIDDLE_PLATES.map { middle ->
            played(listOf(Throw(middle, EAST_PLATE))).settled
        }

        assertEquals(
            listOf(listOf(Location(21, 15)), listOf(Location(21, 17)), listOf(Location(21, 19))),
            reached,
            "the row is set by which of 18x16, 18x17, 18x18 was trodden on",
        )
    }

    @Test
    fun `the outer plate picks which alcove it reaches`() {
        val east = played(listOf(Throw(Location(18, 17), EAST_PLATE))).settled
        val west = played(listOf(Throw(Location(18, 17), WEST_PLATE))).settled

        assertEquals(listOf(Location(21, 17)), east, "19x17 should send it east")
        assertEquals(listOf(Location(15, 17)), west, "17x17 should send it west")
    }

    /**
     * The beat the room turns on. A thing carried into an alcove has to come
     * to rest there for the plate to know about it — being put down is the
     * only thing a plate answers to, nobody being able to stand on one.
     */
    @Test
    fun `a thing carried into an alcove comes to rest there`() {
        val world = played(listOf(Throw(Location(18, 17), EAST_PLATE))).world

        assertTrue(world.inFlight.isEmpty(), "it is still flying about the alcove")
        assertEquals(
            Location(21, 17),
            world.items[FIRST_THING].location,
            "it should be lying on the plate it was thrown at",
        )
    }

    @Test
    fun `and that presses the plate`() {
        val world = played(listOf(Throw(Location(18, 17), EAST_PLATE))).world

        assertEquals(PLATE_DOWN, world.plateAt(Location(21, 17)), "the plate did not go down")
    }

    // --- and what six of them do ---------------------------------------------

    @Test
    fun `five plates are not enough`() {
        val world = played(ALL_SIX.dropLast(1)).world

        assertTrue(world.swinging.isEmpty(), "the door was sent open early")
    }

    @Test
    fun `the sixth opens the way north`() {
        var world = played(ALL_SIX).world

        assertEquals(
            PLATES.associateWith { PLATE_DOWN },
            PLATES.associateWith { world.plateAt(it) },
            "not every plate went down",
        )

        repeat(A_DOOR_OPENING) { world = world.doorsStepped().world }
        val door = world.wall(LEVEL, THE_WAY_OUT, WallSide.NORTH) as? Maz.WallType.Door

        assertTrue(door?.isOpen == true, "17x14 should stand open, and is $door")
    }

    /**
     * And lets the party have their things back: the two faces the alcoves are
     * seen through are taken away at the same moment the door opens.
     */
    @Test
    fun `the sixth also opens the way to what was thrown`() {
        val world = played(ALL_SIX).world

        listOf(Location(16, 17), Location(20, 17)).forEach {
            assertEquals(
                CLEARED,
                world.wallByte(LEVEL, it, WallSide.EAST).value,
                "$it still shuts the party out of the alcove",
            )
        }
    }

    // --- the fixture ---------------------------------------------------------

    /** One go at it: tread on a plate down the middle, one at the side, throw. */
    private class Throw(val middle: Location, val outer: Location)

    private class Played(val world: GameState, val settled: List<Location>)

    private val here get() = level.subLevels[0]
    private val runner get() = LevelScriptRunner(level.script, level = LEVEL)

    private fun played(throws: List<Throw>): Played = runBlocking {
        var world = GameState(
            party = PartyState(THROWN_FROM_NORTH, Direction.NORTH),
            items = dungeonItems,
        ).arrivingAt(level = LEVEL, places = emptyList(), maz = here.maz)

        val settled = mutableListOf<Location>()

        throws.forEachIndexed { n, go ->
            world = world.steppingOn(go.middle).steppingOn(go.outer)

            val going = if (go.outer == EAST_PLATE) Direction.NORTH else Direction.SOUTH
            val thrown = threw(
                world = world,
                what = FIRST_THING + n,
                from = if (going == Direction.NORTH) THROWN_FROM_NORTH else THROWN_FROM_SOUTH,
                going = going,
            )
            world = thrown.world
            settled += thrown.settled
        }

        Played(world, settled)
    }

    private suspend fun GameState.steppingOn(where: Location): GameState =
        runner.onEvent(
            triggers = level.triggers,
            event = ScriptEvent.PARTY_ENTERED,
            state = copy(party = party.copy(position = where)),
            at = where,
        ).state

    /** A throw let go of, and everything it sets off, until nothing is moving. */
    private suspend fun threw(
        world: GameState,
        what: Int,
        from: Location,
        going: Direction,
    ): Played {
        var now = world.copy(
            party = world.party.copy(position = from, facing = going),
            inFlight = world.inFlight + Projectile(
                what = ItemIndex(what),
                at = from,
                place = SquarePlace.NORTH_WEST,
                going = going,
                thrownBy = Projectile.Thrower.AChampion(PartySlot(0)),
            ),
        )

        val flying = Flight(sublevel = here, level = LEVEL)
        val settled = mutableListOf<Location>()

        repeat(UNTIL_IT_STOPS) {
            val moved = flying.onward(now)
            now = moved.world

            moved.flewOnto.forEach {
                now = runner.onEvent(level.triggers, ScriptEvent.SOMETHING_FLEW_IN, now, at = it).state
            }
            moved.struckWalls.forEach {
                now = runner.onEvent(level.triggers, ScriptEvent.SOMETHING_FLEW_IN, now, at = it.at).state
            }
            moved.settled.forEach {
                settled += it
                now = runner.onEvent(level.triggers, ScriptEvent.ITEM_PUT_DOWN, now, at = it).state
            }
        }

        return Played(now, settled)
    }

    /**
     * A plate is a wall the party walk over, and which of the two it is
     * standing at is the whole of what the room remembers.
     */
    private fun GameState.plateAt(at: Location) = wallByte(LEVEL, at, WallSide.NORTH).value

    private companion object {
        const val LEVEL = 12

        /** The two states a plate's wall byte takes, as the room's script writes them. */
        const val PLATE_DOWN = 36
        const val CLEARED = 0

        val MIDDLE_PLATES = listOf(Location(18, 16), Location(18, 17), Location(18, 18))
        val EAST_PLATE = Location(19, 17)
        val WEST_PLATE = Location(17, 17)

        val PLATES = listOf(
            Location(15, 15), Location(15, 17), Location(15, 19),
            Location(21, 15), Location(21, 17), Location(21, 19),
        )

        val THE_WAY_OUT = Location(17, 14)

        /** In front of each teleporter, which is the only place to throw from. */
        val THROWN_FROM_NORTH = Location(19, 15)
        val THROWN_FROM_SOUTH = Location(17, 19)

        /** Which item of the dungeon's table the first throw uses. */
        const val FIRST_THING = 1

        /** Long enough for a throw to cross the room, be carried, and land. */
        const val UNTIL_IT_STOPS = 60

        /** And for a door sent open to finish opening. */
        const val A_DOOR_OPENING = 20

        val ALL_SIX = listOf(
            Throw(Location(18, 16), EAST_PLATE),
            Throw(Location(18, 17), EAST_PLATE),
            Throw(Location(18, 18), EAST_PLATE),
            Throw(Location(18, 16), WEST_PLATE),
            Throw(Location(18, 17), WEST_PLATE),
            Throw(Location(18, 18), WEST_PLATE),
        )
    }
}
