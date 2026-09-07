package pl.pelotasplus.eyeofbeholder.data

import pl.pelotasplus.eyeofbeholder.data.model.ViewWindow
import pl.pelotasplus.eyeofbeholder.data.model.WallSight
import pl.pelotasplus.eyeofbeholder.data.model.viewWindow
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * What a doorway leaves of the squares behind it.
 *
 * Worth stating in numbers because one half of it reads as a bug: a creature
 * standing *in* a doorway is drawn across the frame around it rather than cut
 * to the opening, and that is right. A door narrows the squares it is looked
 * through and takes nothing at all off the square it stands on — so there is
 * nothing to cut the creature in the opening against.
 *
 * The room it shows in is frozen as `level12-monster-past-a-doorway`.
 */
class TheDoorwayBandTest {

    /** A door on the square ahead, and the square behind it seen through it. */
    @Test
    fun `a doorway ahead narrows the square beyond it`() {
        val beyond = windows(aDoorOn = STRAIGHT_AHEAD)[TWO_ROWS_OFF]

        assertEquals(7, beyond.from)
        assertEquals(15, beyond.to)
    }

    /** And takes nothing off the square it stands on. */
    @Test
    fun `a door on a square does not cut what stands on that same square`() {
        val itsOwn = windows(aDoorOn = STRAIGHT_AHEAD)[STRAIGHT_AHEAD]

        assertEquals(ViewWindow.WHOLE_VIEW, itsOwn)
    }

    private fun windows(aDoorOn: Int) = (0..17).map { block ->
        viewWindow(block) { if (it == aDoorOn) WallSight.OPEN_FRAME else WallSight.CLEAR }
    }

    private companion object {
        /** The square one row ahead of the party, in the middle of the view. */
        const val STRAIGHT_AHEAD = 13

        /** And the one behind that, which is seen through it. */
        const val TWO_ROWS_OFF = 9
    }
}
