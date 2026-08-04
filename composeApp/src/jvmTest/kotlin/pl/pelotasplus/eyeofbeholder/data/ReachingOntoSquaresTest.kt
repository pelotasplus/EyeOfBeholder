package pl.pelotasplus.eyeofbeholder.data

import kotlinx.coroutines.runBlocking
import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.GameState
import pl.pelotasplus.eyeofbeholder.data.model.SquarePlace
import pl.pelotasplus.eyeofbeholder.data.model.Inf
import pl.pelotasplus.eyeofbeholder.data.model.PartyState
import pl.pelotasplus.eyeofbeholder.data.model.WallAction
import pl.pelotasplus.eyeofbeholder.data.model.doesWhenClicked
import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.Maz
import pl.pelotasplus.eyeofbeholder.data.model.SubLevel
import pl.pelotasplus.eyeofbeholder.data.model.WallSide
import pl.pelotasplus.eyeofbeholder.data.model.canBeReachedOnto
import pl.pelotasplus.eyeofbeholder.data.model.getWall
import pl.pelotasplus.eyeofbeholder.data.model.showsWhatIsOnIt
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Where the party may put a thing down.
 *
 * Reaching is not seeing, and the two predicates are easy to mistake for each
 * other: a doorway shows the floor beyond it, so a square behind one is drawn
 * with its contents, but nothing can be put through a shut door.
 */
class ReachingOntoSquaresTest {

    private val resources = ResourceRepositoryImpl()

    private fun level(name: String): Inf = runBlocking {
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

    /** The wall the square ahead of a party standing at [from] turns to them. */
    private fun wallAhead(sublevel: SubLevel, from: Location, facing: Direction): Maz.WallType {
        val (dx, dy) = facing.transformCoordinates(0, -1)
        val ahead = Location(from.x + dx, from.y + dy)
        return sublevel.maz.square(ahead).getWall(facing.transformWallSide(WallSide.SOUTH))
    }

    /**
     * The corridor end that showed the bug: a party facing a solid wall could
     * put a thing down on the square behind it.
     */
    @Test
    fun `nothing goes through the wall at level 6's 16x2`() {
        val sublevel = level("LEVEL6.INF").subLevels[0]
        val wall = wallAhead(sublevel, Location(16, 2), Direction.NORTH)

        assertFalse(
            sublevel.canBeReachedOnto(wall),
            "reached onto the square behind $wall",
        )

        // and this is the face that was let through: it shows what is on the
        // square beyond it, which is why asking about sight let a thing be put
        // down there
        assertTrue(sublevel.showsWhatIsOnIt(wall), "$wall was never seen through")
    }

    /**
     * The same face, read as what it does rather than as a number: it is a
     * shelf, so it is worked rather than merely triggered.
     */
    @Test
    fun `the wall at level 6's 16x2 is a niche`() {
        val inf = level("LEVEL6.INF")
        val sublevel = inf.subLevels[0]
        val wall = wallAhead(sublevel, Location(16, 2), Direction.NORTH)

        val decoration = sublevel.decorations.first {
            it.decorationWallIndex == (wall as Maz.WallType.Decoration).decorationWallIndex
        }

        assertEquals(WallAction.NICHE, decoration.doesWhenClicked)
        assertFalse(decoration.doesWhenClicked.answersAnyClick)
    }

    /** And there is something on that shelf to be taken. */
    @Test
    fun `something is shelved in level 6's niche at 16x1`() {
        val dungeon = runBlocking {
            ItemsRepositoryImpl(resources).loadItems().getOrThrow()
        }
        val world = GameState(
            party = PartyState(Location(16, 2), Direction.NORTH),
            items = dungeon.items,
        )

        val shelved = world.lyingAt(level = 6, at = Location(16, 1), place = SquarePlace.IN_A_NICHE)
        assertNotNull(shelved, "nothing is shelved in the niche")

        val taken = world.takingUp(shelved)
        assertEquals(shelved, taken.inHand)
        assertNull(taken.lyingAt(level = 6, at = Location(16, 1), place = SquarePlace.IN_A_NICHE))
    }

    /** An open way is one a thing can be put through, or there is nowhere to put it. */
    @Test
    fun `a square with no wall between can be reached`() {
        val sublevel = level("LEVEL6.INF").subLevels[0]

        assertTrue(sublevel.canBeReachedOnto(Maz.WallType.NoWall))
    }

    /** A door has to be all the way out of its frame. */
    @Test
    fun `a door lets nothing past until it is open`() {
        val sublevel = level("LEVEL4.INF").subLevels[0]

        (0..3).forEach { shut ->
            assertFalse(
                sublevel.canBeReachedOnto(door(state = shut)),
                "reached through a door at state $shut",
            )
        }
        assertTrue(sublevel.canBeReachedOnto(door(state = 4)))
    }

    /**
     * The difference between the two rules, stated outright: what a doorway
     * shows is not what a doorway lets through.
     */
    @Test
    fun `a shut door is seen through but not reached through`() {
        val sublevel = level("LEVEL4.INF").subLevels[0]
        val shut = door(state = 0)

        assertTrue(sublevel.showsWhatIsOnIt(shut))
        assertFalse(sublevel.canBeReachedOnto(shut))
    }

    /** Stairs are not floor to put anything on. */
    @Test
    fun `stairs cannot be reached onto`() {
        val sublevel = level("LEVEL6.INF").subLevels[0]

        assertFalse(sublevel.canBeReachedOnto(Maz.WallType.StairUp))
        assertFalse(sublevel.canBeReachedOnto(Maz.WallType.StairDown))
    }

    private fun door(state: Int) = Maz.WallType.Door(
        doorIndex = pl.pelotasplus.eyeofbeholder.data.model.DoorIndex(0),
        hasButton = false,
        state = state,
    )
}
