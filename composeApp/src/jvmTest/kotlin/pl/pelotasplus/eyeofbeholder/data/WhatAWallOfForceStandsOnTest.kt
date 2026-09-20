package pl.pelotasplus.eyeofbeholder.data

import pl.pelotasplus.eyeofbeholder.data.model.AWallOfForce
import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.GameState
import pl.pelotasplus.eyeofbeholder.data.model.HitPoints
import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.MonsterInstance
import pl.pelotasplus.eyeofbeholder.data.model.MonsterSlot
import pl.pelotasplus.eyeofbeholder.data.model.MonsterTypeId
import pl.pelotasplus.eyeofbeholder.data.model.PartyState
import pl.pelotasplus.eyeofbeholder.data.model.SquarePlace
import pl.pelotasplus.eyeofbeholder.data.model.Ticks
import pl.pelotasplus.eyeofbeholder.data.model.WallByte
import pl.pelotasplus.eyeofbeholder.data.model.WallSide
import pl.pelotasplus.eyeofbeholder.data.model.WallsOfForce
import pl.pelotasplus.eyeofbeholder.data.model.isAWallOfForce
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A wall of force: a square shut off on all four sides for a few minutes.
 *
 * It wants an empty square and refuses anything else, which is most of what
 * there is to get wrong — put across a doorway it would be a wall nobody can
 * see, and put over a creature it would shut that creature in.
 */
class WhatAWallOfForceStandsOnTest {

    private val here = Location(10, 10)
    private val ahead = Location(10, 9)

    private fun world() = GameState(party = PartyState(here, Direction.NORTH))

    private fun aMonster(at: Location, level: Int = 1) = MonsterInstance(
        index = MonsterSlot(0),
        unit = 1,
        location = at,
        place = SquarePlace.MIDDLE,
        direction = Direction.SOUTH,
        type = MonsterTypeId(0),
        gfxIndex = 0,
        mode = 0,
        pause = 0,
        weapon = 0,
        pocketItem = 0,
        hitPoints = HitPoints(10, 10),
    ).copy(level = level)

    // ---- how long one lasts ---------------------------------------------

    /**
     * Half a minute, and another half-minute for every two levels — so a
     * scroll, always read at ninth level, stands for 3003 ticks. That is
     * about two and three quarter minutes at fifty-five milliseconds a tick.
     */
    @Test
    fun `it stands for a half-minute and one more for every two levels`() {
        assertEquals(Ticks(546), WallsOfForce.lastsForACasterOf(0))
        assertEquals(Ticks(819), WallsOfForce.lastsForACasterOf(1))
        assertEquals(Ticks(1092), WallsOfForce.lastsForACasterOf(2))
        assertEquals(Ticks(3003), WallsOfForce.lastsForACasterOf(9))
    }

    // ---- what a square has to be ----------------------------------------

    @Test
    fun `an empty square holds one`() {
        assertTrue(world().couldHoldAWallOfForce(level = 1, at = ahead))
    }

    /**
     * Not over a creature. A wall raised on something standing there would
     * shut it in rather than shut it out, which is the opposite of the point.
     */
    @Test
    fun `a square with something standing on it holds none`() {
        val guarded = world().copy(monsters = listOf(aMonster(ahead)))

        assertFalse(guarded.couldHoldAWallOfForce(level = 1, at = ahead))
        assertNull(guarded.wallOfForceRaised(level = 1, at = ahead, casterLevel = 9))
    }

    /** A creature on another floor is not standing there at all. */
    @Test
    fun `something on another floor does not block it`() {
        val elsewhere = world().copy(monsters = listOf(aMonster(ahead, level = 2)))

        assertTrue(elsewhere.couldHoldAWallOfForce(level = 1, at = ahead))
    }

    /**
     * And not in a doorway. Every one of the four sides is asked, not only
     * the one the party are looking at: a square with a wall behind it is as
     * useless to shut off as one with a wall in front.
     */
    @Test
    fun `a square with a wall on any side holds none`() {
        WallSide.entries.forEach { side ->
            val walled = world().wallChanged(level = 1, at = ahead, side = side, to = WallByte(1))

            assertFalse(
                walled.couldHoldAWallOfForce(level = 1, at = ahead),
                "a wall on $side should have refused the square",
            )
        }
    }

    // ---- raising one -----------------------------------------------------

    /** All four sides go up together, so it cannot be walked round. */
    @Test
    fun `raising one shuts all four sides`() {
        val raised = assertNotNull(world().wallOfForceRaised(1, ahead, casterLevel = 9))

        WallSide.entries.forEach { side ->
            assertTrue(
                raised.wallByte(1, ahead, side).isAWallOfForce,
                "the $side side was left open",
            )
        }
    }

    @Test
    fun `raising one records it with its whole life ahead of it`() {
        val raised = assertNotNull(world().wallOfForceRaised(1, ahead, casterLevel = 9))

        assertEquals(1, raised.wallsOfForce.standing.size)
        assertEquals(ahead, raised.wallsOfForce.standing.single().at)
        assertEquals(3003, raised.wallsOfForce.standing.single().ticksLeft)
    }

    // ---- five, and the sixth --------------------------------------------

    /**
     * Five standing, each with a different time left and the first of them
     * the shortest — so which one a sixth turns out is settled rather than a
     * matter of which the list happens to hold first.
     */
    private fun fiveUp(): GameState {
        val raised = (0 until 5).fold(world()) { world, i ->
            assertNotNull(world.wallOfForceRaised(1, Location(i, 0), casterLevel = 9))
        }

        return raised.copy(
            wallsOfForce = WallsOfForce(
                raised.wallsOfForce.standing.mapIndexed { i, wall ->
                    wall.copy(ticksLeft = 100 + i)
                }
            ),
        )
    }

    @Test
    fun `five may stand at once`() {
        assertEquals(5, WallsOfForce.AT_ONCE)
        assertEquals(5, fiveUp().wallsOfForce.standing.size)
    }

    /**
     * A sixth is not refused. It turns out whichever has least time left to
     * run — the one whose loss costs the party least — and takes its place.
     */
    @Test
    fun `a sixth turns out whichever has least left`() {
        val sixth = assertNotNull(fiveUp().wallOfForceRaised(1, ahead, casterLevel = 9))

        assertEquals(5, sixth.wallsOfForce.standing.size)
        assertTrue(sixth.wallsOfForce.standing.none { it.at == Location(0, 0) })
        assertTrue(sixth.wallsOfForce.standing.any { it.at == ahead })
    }

    /** And the square it stood on is opened again, not left shut for ever. */
    @Test
    fun `the one turned out leaves its square open`() {
        val sixth = assertNotNull(fiveUp().wallOfForceRaised(1, ahead, casterLevel = 9))

        WallSide.entries.forEach { side ->
            assertFalse(
                sixth.wallByte(1, Location(0, 0), side).isAWallOfForce,
                "the turned-out wall left its $side side standing",
            )
        }
    }

    // ---- running out -----------------------------------------------------

    @Test
    fun `it stands until its last tick`() {
        val raised = assertNotNull(world().wallOfForceRaised(1, ahead, casterLevel = 9))

        val (almost, nothingYet) = raised.wallsOfForceRunDown(Ticks(3002))
        assertTrue(almost.wallByte(1, ahead, WallSide.NORTH).isAWallOfForce)
        assertEquals(emptyList(), nothingYet)

        val (gone, ended) = almost.wallsOfForceRunDown(Ticks(1))
        assertEquals(1, ended.size)
        assertEquals(emptyList(), gone.wallsOfForce.standing)
    }

    /** Going, it opens every side it shut. */
    @Test
    fun `running out opens the square again`() {
        val raised = assertNotNull(world().wallOfForceRaised(1, ahead, casterLevel = 9))
        val (gone, _) = raised.wallsOfForceRunDown(Ticks(3003))

        WallSide.entries.forEach { side ->
            assertEquals(
                0,
                gone.wallByte(1, ahead, side).value,
                "the $side side was left standing",
            )
        }
    }

    /** One going says nothing about another with longer to run. */
    @Test
    fun `only what has run out goes`() {
        val two = assertNotNull(world().wallOfForceRaised(1, ahead, casterLevel = 9))
            .copy(
                wallsOfForce = WallsOfForce(
                    listOf(AWallOfForce(1, ahead, 100), AWallOfForce(1, Location(0, 0), 5000))
                ),
            )

        val (left, gone) = two.wallsOfForceRunDown(Ticks(100))

        assertEquals(listOf(ahead), gone.map { it.at })
        assertEquals(listOf(Location(0, 0)), left.wallsOfForce.standing.map { it.at })
    }
}
