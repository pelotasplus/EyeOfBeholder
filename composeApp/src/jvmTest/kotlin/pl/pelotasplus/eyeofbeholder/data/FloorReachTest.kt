package pl.pelotasplus.eyeofbeholder.data

import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.FloorReach
import pl.pelotasplus.eyeofbeholder.data.model.viewRelativeSubPosition
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
                val quadrant = reach.quadrantFacing(facing)

                assertEquals(
                    expectedCorner(reach),
                    viewRelativeSubPosition(facing, quadrant),
                    "$reach facing $facing reaches quadrant $quadrant",
                )
            }
        }
    }

    /**
     * Which corner of the view each piece of floor is: the party's own square
     * shows the two the camera has not passed, and the square in front shows
     * the two nearest.
     */
    private fun expectedCorner(reach: FloorReach) = when (reach) {
        FloorReach.OWN_LEFT -> 0
        FloorReach.OWN_RIGHT -> 1
        FloorReach.AHEAD_LEFT -> 2
        FloorReach.AHEAD_RIGHT -> 3
    }

    /** Facing north, nothing is turned, so the two numberings agree. */
    @Test
    fun `facing north a corner is its own quadrant`() {
        assertEquals(0, FloorReach.OWN_LEFT.quadrantFacing(Direction.NORTH))
        assertEquals(1, FloorReach.OWN_RIGHT.quadrantFacing(Direction.NORTH))
        assertEquals(2, FloorReach.AHEAD_LEFT.quadrantFacing(Direction.NORTH))
        assertEquals(3, FloorReach.AHEAD_RIGHT.quadrantFacing(Direction.NORTH))
    }

    /** Facing south turns the square right round, so every corner swaps. */
    @Test
    fun `facing south every corner is its opposite`() {
        assertEquals(3, FloorReach.OWN_LEFT.quadrantFacing(Direction.SOUTH))
        assertEquals(2, FloorReach.OWN_RIGHT.quadrantFacing(Direction.SOUTH))
        assertEquals(1, FloorReach.AHEAD_LEFT.quadrantFacing(Direction.SOUTH))
        assertEquals(0, FloorReach.AHEAD_RIGHT.quadrantFacing(Direction.SOUTH))
    }
}
