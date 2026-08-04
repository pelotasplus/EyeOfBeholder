package pl.pelotasplus.eyeofbeholder.data

import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.FloorReach
import pl.pelotasplus.eyeofbeholder.data.model.SquarePlace
import pl.pelotasplus.eyeofbeholder.data.model.ViewPlace
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Which corner of which square a click on the floor reaches into.
 *
 * The corner the party see is not the corner the square has: turn round and
 * the near left of the view is the far right of the maze. Getting that
 * backwards puts an item down in the wrong place and draws it in the right
 * one, so it looks correct until the party walk round the square.
 */
class FloorReachTest {

    @Test
    fun `the lower strip is the square the party stand on`() {
        assertEquals(FloorReach.OWN_LEFT, FloorReach.at(10, 110))
        assertEquals(FloorReach.OWN_RIGHT, FloorReach.at(120, 110))
        assertTrue(FloorReach.at(10, 110)?.aheadOfTheParty == false)
    }

    @Test
    fun `the strip above it is the square in front`() {
        assertEquals(FloorReach.AHEAD_LEFT, FloorReach.at(10, 80))
        assertEquals(FloorReach.AHEAD_RIGHT, FloorReach.at(120, 80))
        assertTrue(FloorReach.at(10, 80)?.aheadOfTheParty == true)
    }

    /** Everything higher up is the walls', which is what levers hang on. */
    @Test
    fun `the rest of the view reaches no floor`() {
        assertNull(FloorReach.at(80, 40))
        assertNull(FloorReach.at(80, 0))
    }

    /**
     * The two mappings between a square's corners and the view's must undo
     * each other, whichever way the party face. This is the assertion that
     * catches the table being written out backwards.
     */
    @Test
    fun `a corner reached into is the corner it is drawn in`() {
        Direction.entries.forEach { facing ->
            FloorReach.entries.forEach { reach ->
                val place = reach.placeFacing(facing)

                assertEquals(
                    seenAt(reach),
                    place.asSeenFacing(facing),
                    "$reach facing $facing reaches $place",
                )
            }
        }
    }

    /**
     * Which corner of the view each piece of floor is: the party's own square
     * shows the two the camera has not passed, and the square in front shows
     * the two nearest.
     */
    private fun seenAt(reach: FloorReach) = when (reach) {
        FloorReach.OWN_LEFT -> ViewPlace.FAR_LEFT
        FloorReach.OWN_RIGHT -> ViewPlace.FAR_RIGHT
        FloorReach.AHEAD_LEFT -> ViewPlace.NEAR_LEFT
        FloorReach.AHEAD_RIGHT -> ViewPlace.NEAR_RIGHT
    }

    /** Facing north, nothing is turned, so the view and the maze agree. */
    @Test
    fun `facing north a corner of the view is the corner it names`() {
        assertEquals(SquarePlace.NORTH_WEST, FloorReach.OWN_LEFT.placeFacing(Direction.NORTH))
        assertEquals(SquarePlace.NORTH_EAST, FloorReach.OWN_RIGHT.placeFacing(Direction.NORTH))
        assertEquals(SquarePlace.SOUTH_WEST, FloorReach.AHEAD_LEFT.placeFacing(Direction.NORTH))
        assertEquals(SquarePlace.SOUTH_EAST, FloorReach.AHEAD_RIGHT.placeFacing(Direction.NORTH))
    }

    /** Facing south turns the square right round, so every corner swaps. */
    @Test
    fun `facing south every corner is its opposite`() {
        assertEquals(SquarePlace.SOUTH_EAST, FloorReach.OWN_LEFT.placeFacing(Direction.SOUTH))
        assertEquals(SquarePlace.SOUTH_WEST, FloorReach.OWN_RIGHT.placeFacing(Direction.SOUTH))
        assertEquals(SquarePlace.NORTH_EAST, FloorReach.AHEAD_LEFT.placeFacing(Direction.SOUTH))
        assertEquals(SquarePlace.NORTH_WEST, FloorReach.AHEAD_RIGHT.placeFacing(Direction.SOUTH))
    }

    /**
     * A niche is on a wall, and so is drawn where the wall is rather than
     * anywhere on the floor. Asking where on the square it is seen is asking
     * the wrong question, and the answer is nowhere rather than a corner.
     */
    @Test
    fun `a niche is at no corner of the view`() {
        Direction.entries.forEach { facing ->
            assertNull(SquarePlace.IN_A_NICHE.asSeenFacing(facing))
        }
    }
}
