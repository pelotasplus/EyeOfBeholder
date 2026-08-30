package pl.pelotasplus.eyeofbeholder.rendering

import kotlinx.coroutines.runBlocking
import org.junit.experimental.categories.Category
import pl.pelotasplus.eyeofbeholder.NeedsGameData
import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.Inf
import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.Maz
import pl.pelotasplus.eyeofbeholder.data.model.getWall
import pl.pelotasplus.eyeofbeholder.data.model.wallsInSight
import pl.pelotasplus.eyeofbeholder.data.repository.CpsRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.DcrRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.DecRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.InfRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.ItemsRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.MazRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.PalRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.ResourceRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.VcnRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.ViewConeRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.VmpRepositoryImpl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Walking through a wall can put the party in rooms belonging to a sublevel no
 * script sent them to, where doors have no appearance and monsters are drawn
 * from the wrong sheet. Which one they are in is read back off what is in
 * sight, each sublevel mapping its own wall indices and defining its own doors.
 *
 * What the answers should be comes from level 3's own two tables rather than
 * from what the renderer does with them: bytes 61 and 64 are mapped by its
 * second sublevel and by neither the first nor anything else, and only its
 * first sublevel defines any doors at all.
 */
@Category(NeedsGameData::class)
class SubLevelShowingTest {

    @Test
    fun `level 3 maps 61 and 64 only in its second sublevel`() = withLevel3 { inf ->
        val mappedBy = inf.subLevels.map { sub ->
            sub.decorations.mapTo(mutableSetOf()) { it.decorationWallIndex }
        }

        assertEquals(2, mappedBy.size, "level 3 should have two sublevels")
        assertTrue(61 !in mappedBy[0] && 64 !in mappedBy[0], "sublevel 0 should not map 61 or 64")
        assertTrue(61 in mappedBy[1] && 64 in mappedBy[1], "sublevel 1 should map 61 and 64")
    }

    @Test
    fun `the rooms at 7x14 are the second sublevel`() = withLevel3 { inf ->
        assertEquals(1, inf.showingAt(0, Location(7, 14), Direction.NORTH))
    }

    /** Where a script does put the party, the walls agree with it. */
    @Test
    fun `the rooms at 23x25 are the first`() = withLevel3 { inf ->
        assertEquals(0, inf.showingAt(0, Location(23, 25), Direction.WEST))
    }

    /** Being in the right sublevel already is not a reason to go looking. */
    @Test
    fun `a sublevel that can draw what is in sight is kept`() = withLevel3 { inf ->
        assertEquals(1, inf.showingAt(1, Location(7, 14), Direction.NORTH))
    }

    /**
     * A sublevel is handed the doors of the ones before it, so level 3's second
     * defines none of its own and uses its first's. A door in sight therefore
     * says nothing about which of them the party are in, and nothing pulls them
     * back out of the second.
     */
    @Test
    fun `a door tells the sublevels apart no longer`() = withLevel3 { inf ->
        assertTrue(inf.subLevels[0].doors.isNotEmpty(), "sublevel 0 should define doors")
        assertEquals(
            inf.subLevels[0].doors.size,
            inf.subLevels[1].doors.size,
            "sublevel 1 should have been handed them",
        )

        assertEquals(1, inf.showingAt(1, Location(3, 8), Direction.SOUTH))
    }

    /**
     * Walls only the second sublevel maps carry the party into it wherever they
     * come into sight, and being handed everything the first has means nothing
     * carries them back. The walls can say "further on" and never "back".
     */
    @Test
    fun `walls of the second sublevel carry the party forward only`() = withLevel3 { inf ->
        assertEquals(1, inf.showingAt(0, Location(3, 12), Direction.WEST))
        assertEquals(1, inf.showingAt(0, Location(3, 10), Direction.EAST))
        assertEquals(1, inf.showingAt(1, Location(3, 10), Direction.EAST))
    }

    /**
     * Which sublevel is showing settles what stands in a corridor, not merely
     * how the corridor is painted: a monster is drawn only by the sublevel it
     * belongs to. Two of them hold 18x15 and 19x15 for the first, so from the
     * second that stretch is empty to look at — while the party carried there
     * are still stopped by what they cannot see.
     *
     * This is the cost of the walls carrying the party forward and never back.
     */
    @Test
    fun `the corridor at 19x15 is held by monsters of the first sublevel alone`() =
        withLevel3 { inf ->
            val inTheWay = inf.monsterInstances.filter { it.x in 18..19 && it.y == 15 }

            assertEquals(2, inTheWay.size, "two of them should hold that corridor")
            assertTrue(inTheWay.all { it.subLevel == 0 }, "they belong to the first sublevel")
            assertTrue(
                inTheWay.none { it.subLevel == 1 },
                "the second sublevel draws that corridor empty",
            )
        }

    /**
     * And the walls there say nothing either way, so a party showing the
     * second sublevel at 20x15 were carried in somewhere else and cannot be
     * carried out again.
     */
    @Test
    fun `the walls at 20x15 leave the party in whichever sublevel they arrived in`() =
        withLevel3 { inf ->
            assertEquals(0, inf.showingAt(0, Location(20, 15), Direction.WEST))
            assertEquals(1, inf.showingAt(1, Location(20, 15), Direction.WEST))
        }

    private fun Inf.showingAt(showing: Int, at: Location, facing: Direction): Int {
        val maz = subLevels[showing].maz
        return subLevelShowing(
            showing = showing,
            sight = wallsInSight(at, facing) { square, side ->
                maz.square(square).getWall(side)
            },
        )
    }

    private fun withLevel3(check: (Inf) -> Unit) = runBlocking {
        val resources = ResourceRepositoryImpl()
        val palRepository = PalRepositoryImpl(resources)
        val cpsRepository = CpsRepositoryImpl(resources)
        val repository = ViewConeRepositoryImpl(
            infRepository = InfRepositoryImpl(
                resourceRepository = resources,
                mazRepository = MazRepositoryImpl(resources),
                vmpRepository = VmpRepositoryImpl(resources),
                vcnRepository = VcnRepositoryImpl(resources),
                palRepository = palRepository,
                cpsRepository = cpsRepository,
                decRepository = DecRepositoryImpl(resources),
            ),
            cpsRepository = cpsRepository,
            dcrRepository = DcrRepositoryImpl(resources),
        )

        check(repository.loadLevel("LEVEL3.INF").getOrThrow())
    }
}
