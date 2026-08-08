package pl.pelotasplus.eyeofbeholder.data

import kotlinx.coroutines.runBlocking
import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.Maz
import pl.pelotasplus.eyeofbeholder.data.model.WallByte
import pl.pelotasplus.eyeofbeholder.data.model.WallSide
import pl.pelotasplus.eyeofbeholder.data.model.canBeReachedOnto
import pl.pelotasplus.eyeofbeholder.data.model.canBeWalkedOnto
import pl.pelotasplus.eyeofbeholder.data.model.getWall
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
 * What stops the party walking.
 *
 * The wall types come from the maze format rather than from what the code
 * makes of them: 0 is an empty side, 1 and 2 are solid, and a door is five
 * bytes running from shut to fully open, of which only the last lets anything
 * past.
 */
class WalkingIntoWallsTest {

    private val resources = ResourceRepositoryImpl()

    private fun groundFloorOf(level: String) = runBlocking {
        InfRepositoryImpl(
            resourceRepository = resources,
            mazRepository = MazRepositoryImpl(resources),
            vmpRepository = VmpRepositoryImpl(resources),
            vcnRepository = VcnRepositoryImpl(resources),
            palRepository = PalRepositoryImpl(resources),
            cpsRepository = CpsRepositoryImpl(resources),
            decRepository = DecRepositoryImpl(resources),
        ).loadInf(level).getOrThrow().subLevels[0]
    }

    private val sublevel by lazy { groundFloorOf("LEVEL1.INF") }

    /** The forest outside the temple, which is where the illusions are. */
    private val forest by lazy { groundFloorOf("LEVEL4.INF") }

    @Test
    fun `an empty side is walked through and a solid one is not`() {
        assertTrue(sublevel.canBeWalkedOnto(Maz.WallType.NoWall))
        assertFalse(sublevel.canBeWalkedOnto(Maz.WallType.fromInt(1)))
        assertFalse(sublevel.canBeWalkedOnto(Maz.WallType.fromInt(2)))
    }

    @Test
    fun `a door stops the party until it is all the way open`() {
        // 3 is shut, 7 is out of its frame; the three between are it sliding.
        (3..6).forEach { shutOrMoving ->
            assertFalse(
                sublevel.canBeWalkedOnto(Maz.WallType.fromInt(shutOrMoving)),
                "a door at $shutOrMoving is not open yet",
            )
        }

        assertTrue(sublevel.canBeWalkedOnto(Maz.WallType.fromInt(7)))
    }

    @Test
    fun `stairs are a wall to walk into rather than a way through`() {
        assertFalse(sublevel.canBeWalkedOnto(Maz.WallType.StairUp))
        assertFalse(sublevel.canBeWalkedOnto(Maz.WallType.StairDown))
    }

    // --- what a decorated wall says for itself ----------------------------

    /**
     * What a wall looks like is not what it does. The party stand at 17x15 on
     * level 4, are told the bushes to the south look strange, and walk through
     * them into the way on — so the wall the square to the south turns towards
     * them has to let them past, drawn as solid or not.
     */
    @Test
    fun `the strange bushes on level 4 are walked through`() {
        val bushes = forest.maz.square(Location(17, 16)).getWall(WallSide.NORTH)

        assertTrue(forest.canBeWalkedOnto(bushes), "the bushes stop the party")
        assertTrue(forest.canBeReachedOnto(bushes), "and nothing can be put past them")
    }

    /**
     * And the other way about: a wall can have nothing drawn behind it and
     * still stop the party, so what is drawn cannot answer this either.
     */
    @Test
    fun `a wall with nothing behind it can still stop the party`() {
        val nothingToSee = Maz.WallType.of(WallByte(74))

        val noWallBehind = 0
        assertEquals(
            noWallBehind,
            forest.decorations.first { it.decorationWallIndex == 74 }.wallType,
            "wall 74 is the one drawn as nothing at all",
        )
        assertFalse(forest.canBeWalkedOnto(nothingToSee))
    }

    /**
     * The claim [canBeWalkedOnto] makes about itself: it is a different
     * question from reaching, and the answers happen to agree on everything
     * the game ships. If a level ever disagrees, one of the two is wrong for
     * it and this is where that shows up.
     */
    @Test
    fun `walking and reaching agree on every wall a level can hold`() {
        val disagree = (0..255)
            .map { Maz.WallType.of(WallByte(it)) }
            .filter { sublevel.canBeWalkedOnto(it) != sublevel.canBeReachedOnto(it) }

        assertEquals(emptyList(), disagree)
    }
}
