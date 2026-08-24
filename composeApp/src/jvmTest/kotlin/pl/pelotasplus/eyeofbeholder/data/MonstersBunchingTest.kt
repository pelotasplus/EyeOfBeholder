package pl.pelotasplus.eyeofbeholder.data

import kotlinx.coroutines.runBlocking
import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.GameState
import pl.pelotasplus.eyeofbeholder.data.model.Inf
import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.MonsterInstance
import pl.pelotasplus.eyeofbeholder.data.model.MonsterPathing
import pl.pelotasplus.eyeofbeholder.data.model.MonsterSize
import pl.pelotasplus.eyeofbeholder.data.model.MonsterStepping
import pl.pelotasplus.eyeofbeholder.data.model.MonstersTurn
import pl.pelotasplus.eyeofbeholder.data.model.PartyState
import pl.pelotasplus.eyeofbeholder.data.model.SquarePlace
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
 * Two wolves on one square, in level 4's forest.
 *
 * Level 4's kinds are the ones that go two to a square, which is what lets a
 * pack close on the party in pairs rather than filing in one at a time. The
 * two share it on opposite corners, and whichever of them was there first
 * steps out of the middle to make room. A third is turned away at the edge.
 *
 * The forest run at 18x12 through 18x15 is open, which the party walk in the
 * rendering scenes.
 */
class MonstersBunchingTest {

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
        ).loadInf("LEVEL4.INF").getOrThrow()
    }

    private val sub get() = level.subLevels[0]
    private val kinds get() = sub.monsters

    private fun stepping() = MonsterStepping(level = 4, subLevel = sub, kinds = kinds)

    /**
     * The party out of the way at 18x11, with the wolves standing where this
     * test puts them rather than where the level does.
     */
    private fun forest(vararg wolves: Pair<Location, SquarePlace>): GameState {
        val world = GameState(party = PartyState(Location(18, 11), Direction.SOUTH))
            .arrivingAt(level = 4, places = level.monsterInstances, maz = sub.maz, kinds = kinds)

        val one = world.monsters.first()

        return world.copy(
            monsters = wolves.mapIndexed { index, (at, place) ->
                one.copy(index = index, block = at.asBlock, place = place)
            },
        )
    }

    private fun GameState.wolf(index: Int) = monsters.first { it.index == index }

    private fun MonsterInstance.sizeOfItsKind() =
        kinds.first { it.id == type.value }.size

    @Test
    fun `the wolves are the kind that goes two to a square`() {
        assertTrue(kinds.all { it.size == MonsterSize.TWO_TO_A_SQUARE })
    }

    /**
     * The one already there is standing in the middle, which is where anything
     * alone on a square stands. It steps back to make room, and the pair end
     * up on the two opposite corners kept for them.
     */
    @Test
    fun `a second wolf joins the one already there`() {
        val world = forest(
            Location(18, 13) to SquarePlace.MIDDLE,
            Location(18, 14) to SquarePlace.MIDDLE,
        )

        val stepped = stepping().step(
            world = world,
            monster = world.wolf(1),
            onto = Location(18, 13),
            facing = Direction.NORTH,
        )

        assertTrue(stepped is MonsterStepping.Stepped.Moved)
        assertEquals(SquarePlace.NORTH_WEST, stepped.world.wolf(0).place, "it did not stand aside")
        assertEquals(SquarePlace.SOUTH_EAST, stepped.world.wolf(1).place)
        assertEquals(Location(18, 13), stepped.world.wolf(1).let { Location(it.x, it.y) })
    }

    @Test
    fun `a third is refused`() {
        val world = forest(
            Location(18, 13) to SquarePlace.NORTH_WEST,
            Location(18, 13) to SquarePlace.SOUTH_EAST,
            Location(18, 14) to SquarePlace.MIDDLE,
        )

        val stepped = stepping().step(
            world = world,
            monster = world.wolf(2),
            onto = Location(18, 13),
            facing = Direction.NORTH,
        )

        assertEquals(MonsterStepping.Stepped.Refused, stepped)
    }

    /**
     * Both of a pair fight. A small monster has to be standing on one of the
     * two corners its arm reaches from, and by that rule the wolf on the far
     * corner could only stand and watch — but its kind is bigger than that and
     * reaches the party from either corner it may share the square on.
     */
    @Test
    fun `both of a pair reach the party from the corners they share`() {
        val world = forest(
            Location(18, 13) to SquarePlace.NORTH_WEST,
            Location(18, 13) to SquarePlace.SOUTH_EAST,
        ).let { it.copy(party = PartyState(Location(18, 14), Direction.NORTH)) }
            .let { world ->
                world.copy(monsters = world.monsters.map { it.copy(direction = Direction.SOUTH) })
            }

        world.monsters.forEach { wolf ->
            assertTrue(
                wolf.canReach(world.party, wolf.sizeOfItsKind()),
                "the wolf on ${wolf.place} cannot reach them",
            )
        }
    }

    /**
     * The rule is no use if the hunt never asks for the square. Two wolves
     * coming down the same run at a party: the leader stops beside them, and
     * the one behind steps onto the leader's square rather than being sent
     * round the long way, which is a pack closing in.
     */
    @Test
    fun `a pack closing on the party ends up two to a square`() {
        val world = forest(
            Location(18, 14) to SquarePlace.MIDDLE,
            Location(18, 15) to SquarePlace.MIDDLE,
        ).let { world ->
            world.copy(
                party = PartyState(Location(18, 13), Direction.SOUTH),
                monsters = world.monsters.map { it.copy(direction = Direction.NORTH) },
            )
        }.rousedBy(0).rousedBy(1)

        val hunting = MonsterPathing(stepping = stepping(), kinds = kinds)
        val after = generateSequence(world) { MonstersTurn(kinds).begun(it, hunting) }
            .elementAt(TURNS_TO_CLOSE)

        val squares = after.monsters.map { Location(it.x, it.y) }
        assertEquals(1, squares.toSet().size, "they closed on different squares: $squares")
        assertEquals(
            setOf(SquarePlace.NORTH_WEST, SquarePlace.SOUTH_EAST),
            after.monsters.map { it.place }.toSet(),
            "they are not on the two corners a pair share",
        )
    }

    /**
     * A pair turn together. A whole square turns with whichever
     * of them moved, and without it one of a pair ends up facing a wall while
     * the other fights.
     */
    @Test
    fun `a wolf turning turns the one sharing its square`() {
        val world = forest(
            Location(18, 13) to SquarePlace.NORTH_WEST,
            Location(18, 13) to SquarePlace.SOUTH_EAST,
        ).let { world ->
            world.copy(monsters = world.monsters.map { it.copy(direction = Direction.NORTH) })
        }

        val stepped = stepping().step(
            world = world,
            monster = world.wolf(0),
            facing = Direction.SOUTH,
        )

        assertTrue(stepped is MonsterStepping.Stepped.Turned)
        assertEquals(Direction.SOUTH, stepped.world.wolf(0).direction)
        assertEquals(Direction.SOUTH, stepped.world.wolf(1).direction, "it was left facing away")
    }

    private companion object {
        /** One turn for the leader to arrive, one for the one behind it. */
        const val TURNS_TO_CLOSE = 2
    }
}
