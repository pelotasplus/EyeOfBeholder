package pl.pelotasplus.eyeofbeholder.data

import kotlinx.coroutines.runBlocking
import pl.pelotasplus.eyeofbeholder.data.model.Bearing
import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.GameState
import pl.pelotasplus.eyeofbeholder.data.model.Inf
import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.MonsterInstance
import pl.pelotasplus.eyeofbeholder.data.model.MonsterPathing
import pl.pelotasplus.eyeofbeholder.data.model.MonsterProperty
import pl.pelotasplus.eyeofbeholder.data.model.MonsterStepping
import pl.pelotasplus.eyeofbeholder.data.model.PartyState
import pl.pelotasplus.eyeofbeholder.data.model.SquarePlace
import pl.pelotasplus.eyeofbeholder.data.model.WallByte
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A monster choosing which way to walk, in level 5's temple.
 *
 * Its geometry again: 12x8, 13x8, 12x9 through 15x9, 12x10 and 13x10 are open,
 * 11x8, 13x7 and 14x8 are walled on every face, and the temple door at 11x9 is
 * shut.
 */
class MonsterPathingTest {

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
        ).loadInf("LEVEL5.INF").getOrThrow()
    }

    private val sub get() = level.subLevels[0]
    private val kinds get() = sub.monsters

    /** One cleric, put where the test wants it, with the party out of the way. */
    private fun world(
        at: Location,
        facing: Direction,
        party: Location = Location(15, 9),
    ): GameState {
        val placed = GameState(party = PartyState(party, Direction.NORTH)).arrivingAt(
            level = 5,
            places = level.monsterInstances,
            maz = sub.maz,
            kinds = kinds,
        )

        val one = placed.monsters.first { it.index == CLERIC }
        return placed.copy(
            monsters = listOf(one.copy(block = at.asBlock, direction = facing)),
        )
    }

    private fun pathing(kinds: List<MonsterProperty> = this.kinds) = MonsterPathing(
        stepping = MonsterStepping(level = 5, subLevel = sub, kinds = kinds),
        kinds = kinds,
    )

    private fun GameState.theCleric() = monsters.first { it.index == CLERIC }

    /** The same world with more of the same kind standing on [at]. */
    private fun GameState.crowdedOn(at: Location, vararg places: SquarePlace) = copy(
        monsters = monsters + places.mapIndexed { index, place ->
            theCleric().copy(index = 20 + index, block = at.asBlock, place = place)
        },
    )

    private fun MonsterStepping.Stepped.landedOn(): Location {
        assertTrue(this is MonsterStepping.Stepped.Moved, "it did not move")
        val it = world.monsters.first { it.index == CLERIC }
        return Location(it.x, it.y)
    }

    @Test
    fun `a bearing names each of the eight neighbours`() {
        val here = Location(13, 9)

        assertEquals(Bearing(0), Bearing.from(here, Location(13, 8)))
        assertEquals(Bearing(1), Bearing.from(here, Location(14, 8)))
        assertEquals(Bearing(2), Bearing.from(here, Location(14, 9)))
        assertEquals(Bearing(3), Bearing.from(here, Location(14, 10)))
        assertEquals(Bearing(4), Bearing.from(here, Location(13, 10)))
        assertEquals(Bearing(5), Bearing.from(here, Location(12, 10)))
        assertEquals(Bearing(6), Bearing.from(here, Location(12, 9)))
        assertEquals(Bearing(7), Bearing.from(here, Location(12, 8)))
    }

    /**
     * A bearing is a wedge of the compass rather than a line, so somewhere
     * well off to one side still reads as one of the eight.
     */
    @Test
    fun `somewhere further off still reads as one of the eight`() {
        val here = Location(13, 9)

        assertEquals(Bearing(1), Bearing.from(here, Location(16, 6)))
        assertEquals(Bearing(2), Bearing.from(here, Location(20, 10)))
        assertNull(Bearing.from(here, here))
    }

    @Test
    fun `only a cardinal bearing is a way something can face`() {
        assertEquals(Direction.NORTH, Bearing(0).asDirection)
        assertEquals(Direction.EAST, Bearing(2).asDirection)
        assertEquals(Direction.SOUTH, Bearing(4).asDirection)
        assertEquals(Direction.WEST, Bearing(6).asDirection)

        listOf(1, 3, 5, 7).forEach { assertNull(Bearing(it).asDirection) }
    }

    /**
     * Both fans start straight ahead and widen a step at a time, and each is
     * the other's mirror — which is the whole of the difference between them.
     */
    @Test
    fun `the two ways round are mirror images`() {
        val right = MonsterPathing.WayRound.RIGHT_FIRST.fan
        val left = MonsterPathing.WayRound.LEFT_FIRST.fan

        assertEquals(0, right.first())
        assertEquals(0, left.first())
        assertEquals(right.map { -it }, left)
        assertEquals(listOf(0, 1, 1, 2, 2, 3, 3, 4), right.map { kotlin.math.abs(it) })
    }

    @Test
    fun `a clear way is walked straight`() {
        val world = world(at = Location(13, 8), facing = Direction.SOUTH)

        val stepped = pathing().towards(
            world = world,
            monster = world.theCleric(),
            destination = Location(13, 10),
            wayRound = MonsterPathing.WayRound.RIGHT_FIRST,
        )

        assertEquals(Location(13, 9), stepped.landedOn())
    }

    /**
     * A monster whose destination is the next square along has arrived: it
     * turns to face the square and stops short of it. That is how one comes to
     * a halt beside the party, in reach and facing them, rather than trying to
     * stand where they stand.
     */
    @Test
    fun `arriving beside the party is a turn and not a step`() {
        val world = world(
            at = Location(13, 9),
            facing = Direction.EAST,
            party = Location(13, 8),
        )

        val stepped = pathing().towards(
            world = world,
            monster = world.theCleric(),
            destination = Location(13, 8),
            wayRound = MonsterPathing.WayRound.RIGHT_FIRST,
        )

        assertTrue(stepped is MonsterStepping.Stepped.Turned)

        val turned = stepped.world.theCleric()
        assertEquals(Direction.NORTH, turned.direction)
        assertEquals(Location(13, 9), Location(turned.x, turned.y))
    }

    /**
     * With the way ahead shut the fan takes the monster round — one way or the
     * other, which is the only thing the two orders decide.
     */
    @Test
    fun `blocked straight ahead, it goes round the way it is told`() {
        val world = world(
            at = Location(13, 9),
            facing = Direction.NORTH,
            party = Location(13, 8),
        )

        val right = pathing().towards(
            world = world,
            monster = world.theCleric(),
            destination = Location(13, 7),
            wayRound = MonsterPathing.WayRound.RIGHT_FIRST,
        )
        val left = pathing().towards(
            world = world,
            monster = world.theCleric(),
            destination = Location(13, 7),
            wayRound = MonsterPathing.WayRound.LEFT_FIRST,
        )

        assertEquals(Location(14, 9), right.landedOn())
        assertEquals(Location(12, 9), left.landedOn())
    }

    /**
     * A shut door on the way is worked rather than gone round, and takes the
     * whole step doing it.
     */
    @Test
    fun `a door on the straight line is opened`() {
        val world = world(at = Location(12, 9), facing = Direction.WEST)

        val stepped = pathing().towards(
            world = world,
            monster = world.theCleric(),
            destination = Location(10, 9),
            wayRound = MonsterPathing.WayRound.RIGHT_FIRST,
        )

        assertTrue(stepped is MonsterStepping.Stepped.Turned)
        assertEquals(Location(11, 9), stepped.opened)
    }

    /**
     * Nothing walks a diagonal. Asked for the square off its shoulder, a
     * monster takes whichever half of the diagonal it is not already facing —
     * so one facing north comes at a square to its north-west by going west.
     *
     * It sidles rather than turning: still facing north when it gets there, so
     * arriving beside the party leaves it one turn from swinging and not two.
     */
    @Test
    fun `a square off the shoulder is come at sideways`() {
        val world = world(at = Location(13, 9), facing = Direction.NORTH)

        val stepped = pathing().towards(
            world = world,
            monster = world.theCleric(),
            destination = Location(12, 8),
            wayRound = MonsterPathing.WayRound.RIGHT_FIRST,
        )

        assertTrue(stepped is MonsterStepping.Stepped.Moved, "it did not move")
        assertEquals(Location(12, 9), stepped.landedOn())
        assertEquals(Direction.NORTH, stepped.world.theCleric().direction)
    }

    /**
     * A sidestep is judged by the wall across the face the monster is looking
     * at, not the one it walks through, and that is what stops it sidling
     * along beside the party for ever.
     *
     * Facing south and sidling west, it asks about the *north* face of the
     * square beside it — the one opposite the way it is looking, which has
     * nothing to do with the way it is going. Where that is shut the sidestep
     * is refused, and being refused
     * is what sends it to the fan — which goes the same way but turns it as it
     * goes, so it arrives facing west, no longer facing the party, and has to
     * spend another turn coming round. That turn is the room a party have.
     */
    @Test
    fun `a wall on the face it looks at refuses the sidestep and turns it`() {
        val open = world(at = Location(13, 9), facing = Direction.SOUTH, party = Location(12, 10))

        // Sidling west it keeps facing south, and can swing again at once.
        val sidled = pathing().towards(
            world = open,
            monster = open.theCleric(),
            destination = Location(12, 10),
            wayRound = MonsterPathing.WayRound.RIGHT_FIRST,
        )
        assertEquals(Location(12, 9), sidled.landedOn())
        assertEquals(Direction.SOUTH, (sidled as MonsterStepping.Stepped.Moved).world.theCleric().direction)

        // Shut the face it is looking at, leaving the one it walks through open.
        val shut = open.wallChanged(5, Location(12, 9), WallSide.NORTH, WallByte(1))

        val fanned = pathing().towards(
            world = shut,
            monster = shut.theCleric(),
            destination = Location(12, 10),
            wayRound = MonsterPathing.WayRound.RIGHT_FIRST,
        )

        assertEquals(Location(12, 9), fanned.landedOn(), "the fan did not take it west")
        assertEquals(
            Direction.WEST,
            (fanned as MonsterStepping.Stepped.Moved).world.theCleric().direction,
            "it sidled instead of being refused and fanning",
        )
    }

    /**
     * Somebody standing on the next square is not a wall. The original asks
     * the square for a free place only once it knows something is on it, and
     * walks on when it gets one — which is how a pack crowds onto the squares
     * around the party instead of queueing up behind one another.
     */
    @Test
    fun `a square its own kind stands on is walked onto`() {
        val world = world(at = Location(13, 8), facing = Direction.SOUTH)
            .crowdedOn(Location(13, 9), SquarePlace.MIDDLE)

        val stepped = pathing().towards(
            world = world,
            monster = world.theCleric(),
            destination = Location(13, 10),
            wayRound = MonsterPathing.WayRound.RIGHT_FIRST,
        )

        assertEquals(Location(13, 9), stepped.landedOn())

        // Out of the middle and into the corner its own facing names, which
        // for one looking south is the south-east one.
        val after = (stepped as MonsterStepping.Stepped.Moved).world
        assertEquals(
            SquarePlace.SOUTH_EAST,
            after.monsters.first { it.index == 20 }.place,
            "the one already there did not stand aside",
        )
        assertEquals(
            SquarePlace.SOUTH_WEST,
            after.theCleric().place,
            "the one arriving did not take the best corner left",
        )
    }

    /**
     * A square with no place left is a refusal like any other, and a refusal
     * is what the fan is for: the walk goes round rather than stopping.
     */
    @Test
    fun `a full square is gone round rather than into`() {
        val world = world(at = Location(13, 8), facing = Direction.SOUTH).crowdedOn(
            Location(13, 9),
            SquarePlace.NORTH_WEST,
            SquarePlace.NORTH_EAST,
            SquarePlace.SOUTH_WEST,
            SquarePlace.SOUTH_EAST,
        )

        val stepped = pathing().towards(
            world = world,
            monster = world.theCleric(),
            destination = Location(13, 10),
            wayRound = MonsterPathing.WayRound.RIGHT_FIRST,
        )

        assertEquals(Location(12, 8), stepped.landedOn(), "the fan did not take it round")
    }

    @Test
    fun `a monster already where it wants to be does nothing`() {
        val world = world(at = Location(13, 9), facing = Direction.NORTH)

        val stepped = pathing().towards(
            world = world,
            monster = world.theCleric(),
            destination = Location(13, 9),
            wayRound = MonsterPathing.WayRound.RIGHT_FIRST,
        )

        assertEquals(MonsterStepping.Stepped.Refused, stepped)
    }

    private companion object {
        const val CLERIC = 16
    }
}
