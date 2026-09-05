package pl.pelotasplus.eyeofbeholder.data

import kotlinx.coroutines.runBlocking
import org.junit.experimental.categories.Category
import pl.pelotasplus.eyeofbeholder.NeedsGameData
import pl.pelotasplus.eyeofbeholder.data.model.DamageDice
import pl.pelotasplus.eyeofbeholder.data.model.Dice
import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.Flight
import pl.pelotasplus.eyeofbeholder.data.model.GameState
import pl.pelotasplus.eyeofbeholder.data.model.HitPoints
import pl.pelotasplus.eyeofbeholder.data.model.Inf
import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.MonsterInstance
import pl.pelotasplus.eyeofbeholder.data.model.MonsterSlot
import pl.pelotasplus.eyeofbeholder.data.model.MonsterTypeId
import pl.pelotasplus.eyeofbeholder.data.model.PartySlot
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
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Which of the things standing on a square a passing bolt actually touches.
 *
 * Four creatures share a square, one to a corner, and a bolt goes down one
 * side of it: it takes the two on that side and leaves the two on the other.
 * That is what makes where a caster stands in the rank worth anything — a
 * spell loosed from the left half of the party and one from the right reach
 * different halves of the same pack.
 *
 * Something too big for a corner stands in the middle, and a bolt reaches that
 * only over the near half of the square, the half it came in by. Which corners
 * those are turns on which way the bolt is going, and the game writes it out
 * as a table of sixteen. The code reads that table back as a rule; the table
 * itself is kept here, so the reading has something to be wrong against.
 */
@Category(NeedsGameData::class)
class WhatAThingInTheAirTouchesTest {

    private val here = Location(5, 5)

    /**
     * The game's own sixteen, transcribed: for each way of going, which
     * corners of a square reach something standing in the middle of it.
     *
     * Read back it says the near half — the half a thing comes in by — but it
     * is kept here as the numbers rather than as the reading, so that the
     * reading has something to be wrong against.
     */
    private val reachesTheMiddleFrom = mapOf(
        Direction.NORTH to setOf(SquarePlace.SOUTH_WEST, SquarePlace.SOUTH_EAST),
        Direction.EAST to setOf(SquarePlace.NORTH_WEST, SquarePlace.SOUTH_WEST),
        Direction.SOUTH to setOf(SquarePlace.NORTH_WEST, SquarePlace.NORTH_EAST),
        Direction.WEST to setOf(SquarePlace.NORTH_EAST, SquarePlace.SOUTH_EAST),
    )

    // --- what fills a square -------------------------------------------------

    @Test
    fun `the game's own table says the same`() {
        Direction.entries.forEach { going ->
            val reached = CORNERS.filter { corner ->
                touched(from = corner, going = going, standing = SquarePlace.MIDDLE).isNotEmpty()
            }

            assertEquals(
                reachesTheMiddleFrom.getValue(going),
                reached.toSet(),
                "going $going",
            )
        }
    }

    /** Something over the middle itself reaches it from wherever it is. */
    @Test
    fun `a bolt already over the middle reaches what stands there`() {
        assertEquals(
            1,
            touched(
                from = SquarePlace.MIDDLE,
                going = Direction.NORTH,
                standing = SquarePlace.MIDDLE,
            ).size,
        )
    }

    // --- and what stands in a corner -----------------------------------------

    /** A bolt down one corner takes what is in that corner and nothing else. */
    @Test
    fun `only the corner it is over is touched`() {
        CORNERS.forEach { corner ->
            val reached = CORNERS.filter { standing ->
                touched(from = corner, going = Direction.NORTH, standing = standing).isNotEmpty()
            }

            assertEquals(listOf(corner), reached, "a bolt over $corner")
        }
    }

    /**
     * The whole point of it: four creatures to a square, and a bolt down the
     * western side takes the two westerly ones. Going north it crosses the
     * south-west corner and then the north-west, so both of that side are
     * reached and neither of the other.
     */
    @Test
    fun `four to a square, and a bolt takes the two on its side`() {
        val westerly = setOf(SquarePlace.SOUTH_WEST, SquarePlace.NORTH_WEST)

        val reached = westerly.flatMap { over ->
            CORNERS.filter { touched(from = over, going = Direction.NORTH, standing = it).isNotEmpty() }
        }

        assertEquals(westerly, reached.toSet())
    }

    // --- the fixture ---------------------------------------------------------

    /** Which monsters a bolt over [from] going [going] reaches, of one [standing]. */
    private fun touched(
        from: SquarePlace,
        going: Direction,
        standing: SquarePlace,
    ): List<Flight.Hurt.AMonster> {
        val monster = MonsterInstance(
            index = MonsterSlot(0),
            unit = 0,
            location = here,
            place = standing,
            direction = Direction.SOUTH,
            type = MonsterTypeId(0),
            gfxIndex = 0,
            mode = 0,
            pause = 0,
            weapon = 0,
            pocketItem = 0,
            hitPoints = HitPoints(90, 90),
        )

        val world = GameState(
            party = PartyState(Location(0, 0), Direction.NORTH),
            monsters = listOf(monster),
            inFlight = listOf(
                Projectile(
                    what = null,
                    at = here,
                    place = from,
                    going = going,
                    thrownBy = Projectile.Thrower.AChampion(PartySlot(0)),
                    harm = Projectile.Harm(dice = A_LITTLE),
                    leaving = false,
                ),
            ),
        )

        // No ticks, so it is asked what it is over without moving off it —
        // which is what makes the answer about one corner rather than a side.
        return Flight(sublevel = level.subLevels[0], level = 2, dice = alwaysOne)
            .onward(world, ticks = 0)
            .hurt
            .filterIsInstance<Flight.Hurt.AMonster>()
    }

    private val alwaysOne = Dice { times, _, modifier -> times + modifier }

    private val level: Inf = runBlocking {
        InfRepositoryImpl(
            resourceRepository = ResourceRepositoryImpl(),
            mazRepository = MazRepositoryImpl(ResourceRepositoryImpl()),
            vmpRepository = VmpRepositoryImpl(ResourceRepositoryImpl()),
            vcnRepository = VcnRepositoryImpl(ResourceRepositoryImpl()),
            palRepository = PalRepositoryImpl(ResourceRepositoryImpl()),
            cpsRepository = CpsRepositoryImpl(ResourceRepositoryImpl()),
            decRepository = DecRepositoryImpl(ResourceRepositoryImpl()),
        ).loadInf("LEVEL2.INF").getOrThrow()
    }

    private companion object {
        val CORNERS = listOf(
            SquarePlace.NORTH_WEST,
            SquarePlace.NORTH_EAST,
            SquarePlace.SOUTH_WEST,
            SquarePlace.SOUTH_EAST,
        )

        /** Enough to show up as a hit; none of this is about how much. */
        val A_LITTLE = DamageDice(times = 1, pips = 1, base = 0)
    }
}
