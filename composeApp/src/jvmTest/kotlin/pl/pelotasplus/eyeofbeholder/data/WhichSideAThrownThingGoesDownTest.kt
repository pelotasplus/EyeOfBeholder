package pl.pelotasplus.eyeofbeholder.data

import kotlinx.coroutines.runBlocking
import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.Flight
import pl.pelotasplus.eyeofbeholder.data.model.GameState
import pl.pelotasplus.eyeofbeholder.data.model.HitPoints
import pl.pelotasplus.eyeofbeholder.data.model.Inf
import pl.pelotasplus.eyeofbeholder.data.model.ItemIndex
import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.MonsterInstance
import pl.pelotasplus.eyeofbeholder.data.model.MonsterSlot
import pl.pelotasplus.eyeofbeholder.data.model.MonsterTypeId
import pl.pelotasplus.eyeofbeholder.data.model.PartyState
import pl.pelotasplus.eyeofbeholder.data.model.Projectile
import pl.pelotasplus.eyeofbeholder.data.model.SquarePlace
import pl.pelotasplus.eyeofbeholder.data.repository.CpsRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.DecRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.InfRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.MazRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.PalRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.ResourceRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.VcnRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.VmpRepositoryImpl
import org.junit.experimental.categories.Category
import pl.pelotasplus.eyeofbeholder.NeedsGameData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Which of a square's four corners a thing in the air can reach.
 *
 * It is over one quarter of a square and not the whole of it, and crossing
 * takes it down one side — in at the near corner of that side, out at the far
 * one. So it meets what stands on its own side and passes what stands across
 * the way, which is what makes throwing from the left different from throwing
 * from the right, and what lets a stone be thrown past a monster at all.
 *
 * Asking only which square something is on makes every occupied square a wall.
 *
 * The corridor on the second floor runs north from 3x11 up to 3x8, which is
 * the one this suite already uses for the fireball. Travelling north, a thing
 * enters a square at its south edge and leaves by the north, keeping to the
 * side it started on.
 */
@Category(NeedsGameData::class)
class WhichSideAThrownThingGoesDownTest {

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
        ).loadInf("LEVEL2.INF").getOrThrow()
    }

    private val loosedFrom = Location(3, 11)
    private val crossing = Location(3, 10)

    /**
     * Loosed by the level rather than by anybody, so nothing rolls to hit and
     * what is struck is only a question of where it stood.
     */
    private fun thrownDown(side: SquarePlace, everybody: Boolean = false) = Projectile(
        what = null,
        at = loosedFrom,
        place = side,
        going = Direction.NORTH,
        thrownBy = Projectile.Thrower.TheLevel,
        harm = if (everybody) Projectile.Harm.ofABurst(1) else Projectile.Harm.ofAThrownThing,
    )

    private fun standing(where: SquarePlace) = MonsterInstance(
        index = MonsterSlot(0),
        unit = 0,
        location = crossing,
        place = where,
        direction = Direction.SOUTH,
        type = MonsterTypeId(0),
        gfxIndex = 0,
        mode = 0,
        pause = 0,
        weapon = 0,
        pocketItem = 0,
        hitPoints = HitPoints(10, 10),
    )

    private fun world(monster: MonsterInstance, flying: Projectile) = GameState(
        party = PartyState(loosedFrom, Direction.NORTH),
        monsters = listOf(monster),
        inFlight = listOf(flying),
    )

    private fun flight() = Flight(sublevel = level.subLevels[0], level = 2)

    /** One square's worth, which puts it on [crossing] at the near corner. */
    private fun onward(world: GameState) =
        flight().onward(world, ticks = Projectile.ACROSS_A_SQUARE)

    /** And a moment more, which carries it to the far corner of the same side. */
    private fun aMomentMore(world: GameState) = flight().onward(world, ticks = 3)

    private fun Flight.Moved.struckAnything() = hurt.isNotEmpty()

    @Test
    fun `it meets what stands in the corner it comes in at`() {
        val moved = onward(
            world(standing(SquarePlace.SOUTH_WEST), thrownDown(SquarePlace.SOUTH_WEST)),
        )

        assertTrue(moved.struckAnything(), "it passed straight through what was in its way")
    }

    @Test
    fun `and passes what stands across the way`() {
        val moved = onward(
            world(standing(SquarePlace.SOUTH_EAST), thrownDown(SquarePlace.SOUTH_WEST)),
        )

        assertTrue(
            !moved.struckAnything(),
            "it struck something standing on the other side of the corridor",
        )
        assertEquals(
            listOf(crossing),
            moved.flewOnto,
            "it should have carried on over the square rather than stopping",
        )
    }

    /** Thrown down the other side, the same monster is the one in the way. */
    @Test
    fun `which side it was thrown from is what decides it`() {
        val moved = onward(
            world(standing(SquarePlace.SOUTH_EAST), thrownDown(SquarePlace.SOUTH_EAST)),
        )

        assertTrue(moved.struckAnything(), "throwing down its own side missed it")
    }

    @Test
    fun `whatever fills the square is in the way of both sides`() {
        listOf(SquarePlace.SOUTH_WEST, SquarePlace.SOUTH_EAST).forEach { side ->
            val moved = onward(world(standing(SquarePlace.MIDDLE), thrownDown(side)))

            assertTrue(moved.struckAnything(), "something in the middle was passed on the $side")
        }
    }

    /**
     * The far corner of its own side is reached a moment after the near one,
     * so a monster standing at the top of the square is met on the way out
     * rather than on the way in.
     */
    @Test
    fun `the far corner of its side is reached a moment later`() {
        val arriving = onward(
            world(standing(SquarePlace.NORTH_WEST), thrownDown(SquarePlace.SOUTH_WEST)),
        )

        assertTrue(!arriving.struckAnything(), "it reached the far corner as it came in")
        assertTrue(
            aMomentMore(arriving.world).struckAnything(),
            "it never reached the far corner of its own side",
        )
    }

    // --- and where it comes to rest ------------------------------------------

    /**
     * A thing that lands has been put down, and the square has to be told so
     * — it is the only way something heavy can reach a pressure plate that
     * nobody can walk to.
     */
    @Test
    fun `a thing that comes down says where it landed`() {
        val stopped = onward(
            world(standing(SquarePlace.SOUTH_WEST), aStoneDown(SquarePlace.SOUTH_WEST)),
        )

        assertEquals(
            listOf(crossing),
            stopped.settled,
            "it came to rest and no square was told",
        )
    }

    /** Stopped by a wall rather than by a monster, and it still lands. */
    @Test
    fun `and so does one that runs out of corridor`() {
        val world = GameState(
            party = PartyState(loosedFrom, Direction.NORTH),
            inFlight = listOf(aStoneDown(SquarePlace.SOUTH_WEST).copy(squaresLeft = 1)),
        )

        assertEquals(
            listOf(crossing),
            onward(world).settled,
            "a thing that simply stopped told nobody",
        )
    }

    /** A conjured bolt is on no floor and leaves nothing behind. */
    @Test
    fun `nothing lands where there was nothing to land`() {
        val stopped = onward(
            world(standing(SquarePlace.SOUTH_WEST), thrownDown(SquarePlace.SOUTH_WEST)),
        )

        assertEquals(
            emptyList(),
            stopped.settled,
            "a bolt that exists only in the air was put down somewhere",
        )
    }

    @Test
    fun `and nothing lands while it is still going`() {
        val world = GameState(
            party = PartyState(loosedFrom, Direction.NORTH),
            inFlight = listOf(aStoneDown(SquarePlace.SOUTH_WEST)),
        )

        assertEquals(
            emptyList(),
            onward(world).settled,
            "it was still in the air and something was told it had landed",
        )
    }

    /** The same thing, carrying an item, so that it has something to leave. */
    private fun aStoneDown(side: SquarePlace) =
        thrownDown(side).copy(what = ItemIndex(1))

    /** A burst is not thrown down a side. It takes the square whole. */
    @Test
    fun `a burst takes the square whole`() {
        val moved = onward(
            world(
                standing(SquarePlace.SOUTH_EAST),
                thrownDown(SquarePlace.SOUTH_WEST, everybody = true),
            ),
        )

        assertTrue(moved.struckAnything(), "a burst spared what stood across the way")
    }
}
