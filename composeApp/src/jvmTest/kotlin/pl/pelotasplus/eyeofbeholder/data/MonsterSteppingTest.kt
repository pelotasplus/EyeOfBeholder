package pl.pelotasplus.eyeofbeholder.data

import kotlinx.coroutines.runBlocking
import org.junit.experimental.categories.Category
import pl.pelotasplus.eyeofbeholder.NeedsGameData
import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.GameState
import pl.pelotasplus.eyeofbeholder.data.model.Inf
import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.MonsterInstance
import pl.pelotasplus.eyeofbeholder.data.model.MonsterProperty
import pl.pelotasplus.eyeofbeholder.data.model.MonsterSize
import pl.pelotasplus.eyeofbeholder.data.model.MonsterSlot
import pl.pelotasplus.eyeofbeholder.data.model.MonsterStepping
import pl.pelotasplus.eyeofbeholder.data.model.MonsterTypeId
import pl.pelotasplus.eyeofbeholder.data.model.PartyState
import pl.pelotasplus.eyeofbeholder.data.model.SquarePlace
import pl.pelotasplus.eyeofbeholder.data.model.TrackIndex
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
 * A monster taking one step, in level 5's temple.
 *
 * The geometry is the level's own and not a fixture: the two clerics share
 * 13x8, the square north of them at 13x7 is walled on all four faces, 12x8 and
 * 12x9 are open, and the temple door at 11x9 is shut. Their kind is four to a
 * square and can work a door.
 */
@Category(NeedsGameData::class)
class MonsterSteppingTest {

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

    /** The party met at 13x9, straight in front of the pair on 13x8. */
    private fun world() = GameState(party = PartyState(Location(13, 9), Direction.NORTH))
        .arrivingAt(
            level = 5,
            places = level.monsterInstances,
            maz = sub.maz,
            kinds = kinds,
        )

    private fun stepping(kinds: List<MonsterProperty> = this.kinds) =
        MonsterStepping(level = 5, subLevel = sub, kinds = kinds)

    private fun GameState.monster(slot: MonsterSlot) = monsters.first { it.index == slot }

    private val aCleric = MonsterSlot(16)

    @Test
    fun `a wall refuses the step`() {
        val world = world()

        val stepped = stepping().step(
            world = world,
            monster = world.monster(aCleric),
            onto = Location(13, 7),
            facing = Direction.NORTH,
        )

        assertEquals(MonsterStepping.Stepped.Refused, stepped)
    }

    /**
     * A monster does not reach the party by walking into them. It stops on the
     * square beside them and swings from there, which is why the square they
     * stand on is refused however open it is.
     */
    @Test
    fun `the party's own square refuses the step`() {
        val world = world()

        val stepped = stepping().step(
            world = world,
            monster = world.monster(aCleric),
            onto = world.party.position,
            facing = Direction.SOUTH,
        )

        assertEquals(MonsterStepping.Stepped.Refused, stepped)
    }

    @Test
    fun `a step onto an open square is taken, and heard`() {
        val world = world()

        val stepped = stepping().step(
            world = world,
            monster = world.monster(aCleric),
            onto = Location(12, 8),
            facing = Direction.WEST,
        )

        assertTrue(stepped is MonsterStepping.Stepped.Moved, "the step was refused")

        val moved = stepped.world.monster(aCleric)
        assertEquals(12, moved.x)
        assertEquals(8, moved.y)
        assertEquals(Direction.WEST, moved.direction)

        // Its kind's second track number, which is what it sounds like moving.
        assertEquals(TrackIndex(36), stepped.heard)
    }

    /**
     * Opening a door is the whole of a step: the monster ends the turn facing
     * the door and standing where it started, and walks through on the next
     * one.
     */
    @Test
    fun `a shut door takes the step rather than refusing it`() {
        val world = world().let { it.copy(monsters = listOf(it.monster(aCleric).at(12, 9))) }

        val stepped = stepping().step(
            world = world,
            monster = world.monster(aCleric),
            onto = Location(11, 9),
            facing = Direction.WEST,
        )

        assertTrue(stepped is MonsterStepping.Stepped.Turned, "the door was not worked")
        assertEquals(Location(11, 9), stepped.opened)

        val turned = stepped.world.monster(aCleric)
        assertEquals(12, turned.x, "it went through the shut door")
        assertEquals(Direction.WEST, turned.direction)
    }

    @Test
    fun `a door stops a monster whose kind cannot work one`() {
        val world = world().let { it.copy(monsters = listOf(it.monster(aCleric).at(12, 9))) }

        val stepped = stepping(kinds.map { it.copy(capsFlags = 0) }).step(
            world = world,
            monster = world.monster(aCleric),
            onto = Location(11, 9),
            facing = Direction.WEST,
        )

        assertEquals(MonsterStepping.Stepped.Refused, stepped)
    }

    /** Turning is a step of its own, and all the monster does with the turn. */
    @Test
    fun `a monster given nowhere to go turns where it stands`() {
        val world = world()

        val stepped = stepping().step(
            world = world,
            monster = world.monster(aCleric),
            facing = Direction.EAST,
        )

        assertTrue(stepped is MonsterStepping.Stepped.Turned)
        assertNull(stepped.opened)

        val turned = stepped.world.monster(aCleric)
        assertEquals(13, turned.x)
        assertEquals(8, turned.y)
        assertEquals(Direction.EAST, turned.direction)
    }

    @Test
    fun `four of a small kind share a square and a fifth is refused`() {
        val crowd = world().let { world ->
            val one = world.monster(aCleric)
            world.copy(
                monsters = listOf(
                    SquarePlace.NORTH_WEST,
                    SquarePlace.NORTH_EAST,
                    SquarePlace.SOUTH_WEST,
                    SquarePlace.SOUTH_EAST,
                ).mapIndexed { at, corner ->
                    one.copy(
                        index = MonsterSlot(20 + at),
                        location = Location(12, 8),
                        place = corner,
                    )
                } + one,
            )
        }

        val stepped = stepping().step(
            world = crowd,
            monster = crowd.monster(aCleric),
            onto = Location(12, 8),
            facing = Direction.WEST,
        )

        assertEquals(MonsterStepping.Stepped.Refused, stepped)
    }

    @Test
    fun `a monster arriving takes a corner nobody is standing on`() {
        val crowd = world().let { world ->
            val one = world.monster(aCleric)
            world.copy(
                monsters = listOf(
                    one.copy(
                        index = MonsterSlot(20),
                        location = Location(12, 8),
                        place = SquarePlace.NORTH_WEST,
                    ),
                    one.copy(
                        index = MonsterSlot(21),
                        location = Location(12, 8),
                        place = SquarePlace.NORTH_EAST,
                    ),
                    one,
                ),
            )
        }

        val stepped = stepping().step(
            world = crowd,
            monster = crowd.monster(aCleric),
            onto = Location(12, 8),
            facing = Direction.WEST,
        )

        assertTrue(stepped is MonsterStepping.Stepped.Moved)
        assertEquals(SquarePlace.SOUTH_WEST, stepped.world.monster(aCleric).place)
    }

    /** A square takes monsters of one size only, whichever got there first. */
    @Test
    fun `a small monster will not join something that fills the square`() {
        val big = kinds.map {
            if (it.id == 1) it.copy(size = MonsterSize.FILLS_THE_SQUARE) else it
        }

        val blocked = world().let { world ->
            val one = world.monster(aCleric)
            world.copy(
                monsters = listOf(
                    one.copy(
                        index = MonsterSlot(20),
                        type = MonsterTypeId(1),
                        location = Location(12, 8),
                        place = SquarePlace.MIDDLE,
                    ),
                    one,
                ),
            )
        }

        val stepped = stepping(big).step(
            world = blocked,
            monster = blocked.monster(aCleric),
            onto = Location(12, 8),
            facing = Direction.WEST,
        )

        assertEquals(MonsterStepping.Stepped.Refused, stepped)
    }

    private fun MonsterInstance.at(x: Int, y: Int) = copy(location = Location(x, y))
}
