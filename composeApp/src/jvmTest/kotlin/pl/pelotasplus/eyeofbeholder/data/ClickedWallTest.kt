package pl.pelotasplus.eyeofbeholder.data

import kotlinx.collections.immutable.persistentListOf
import pl.pelotasplus.eyeofbeholder.data.model.ClickedWall
import pl.pelotasplus.eyeofbeholder.data.model.Dec
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A wall being clickable is not the same as the click hitting what hangs on
 * it: the button has to be hit, and it is drawn in one place on a wall seen
 * from the left and another on the same wall mirrored.
 */
class ClickedWallTest {

    // one rectangle two columns wide and ten tall, hung at (80, 40)
    private val rectangles = listOf(Dec.DecorationRectangle(x = 0, y = 0, w = 2, h = 10))

    private fun decoration(mirrored: Boolean) = Dec.Decoration(
        index = 0,
        rectangleIndices = persistentListOf(9, 0, 9, 9, 9, 9, 9, 9, 9, 9),
        linkToNextDecoration = 0,
        flags = if (mirrored) 1 else 0,
        xCoords = persistentListOf(0, 80, 0, 0, 0, 0, 0, 0, 0, 0),
        yCoords = persistentListOf(0, 40, 0, 0, 0, 0, 0, 0, 0, 0),
    )

    @Test
    fun `a click on the button hits it`() {
        assertTrue(ClickedWall.hits(decoration(mirrored = false), rectangles, x = 88, y = 45))
    }

    @Test
    fun `a click elsewhere on the wall does not`() {
        assertFalse(ClickedWall.hits(decoration(mirrored = false), rectangles, x = 20, y = 45))
        assertFalse(ClickedWall.hits(decoration(mirrored = false), rectangles, x = 88, y = 100))
    }

    @Test
    fun `just outside still counts, because the original is generous`() {
        // four pixels above and left, eight below and right
        assertTrue(ClickedWall.hits(decoration(mirrored = false), rectangles, x = 77, y = 37))
        assertTrue(ClickedWall.hits(decoration(mirrored = false), rectangles, x = 100, y = 55))
        assertFalse(ClickedWall.hits(decoration(mirrored = false), rectangles, x = 75, y = 45))
    }

    @Test
    fun `a mirrored decoration is measured from the other edge`() {
        // 176 - 80 - 16 leaves it at 80 again, which would hide the bug, so
        // check a point that only the mirrored reading can contain
        val mirrored = decoration(mirrored = true)

        assertTrue(ClickedWall.hits(mirrored, rectangles, x = 176 - 80 - 16 + 4, y = 45))
        assertFalse(ClickedWall.hits(mirrored, rectangles, x = 160, y = 45))
    }
}
