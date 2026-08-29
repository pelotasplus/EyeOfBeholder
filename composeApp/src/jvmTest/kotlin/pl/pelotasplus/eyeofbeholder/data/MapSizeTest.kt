package pl.pelotasplus.eyeofbeholder.data

import pl.pelotasplus.eyeofbeholder.data.model.Debugging
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The sizes the little map can be drawn at, which are a debug control and no
 * part of the game.
 */
class MapSizeTest {

    /**
     * The size the map has always been drawn at, which the control has to keep
     * offering: it is the one the rest of the screen was laid out around.
     */
    @Test
    fun `normal is the size the map was before there was a choice`() {
        assertEquals(50, Debugging.MapSize.NORMAL.side)
        assertEquals(Debugging.MapSize.NORMAL, Debugging().mapSize.value, "and is where it starts")
    }

    @Test
    fun `the sizes are listed smallest first`() {
        val sides = Debugging.MapSize.entries.map { it.side }

        assertEquals(sides.sorted(), sides, "the list is out of order: $sides")
        assertEquals(sides.distinct(), sides, "two of them are the same size")
    }

    /** One control walks the whole list and comes back round. */
    @Test
    fun `stepping through the sizes reaches every one and returns`() {
        var size = Debugging.MapSize.NORMAL
        val walked = Debugging.MapSize.entries.map { size = size.next; size }

        assertEquals(Debugging.MapSize.entries.toSet(), walked.toSet())
        assertEquals(Debugging.MapSize.NORMAL, size, "the last step should come back round")
    }

    /** Asking for a size is what the menu does, and it holds. */
    @Test
    fun `a size asked for is the size kept`() {
        val debugging = Debugging()

        debugging.sizeMap(Debugging.MapSize.HUGE)

        assertEquals(Debugging.MapSize.HUGE, debugging.mapSize.value)
        assertTrue(
            debugging.mapSize.value.side > Debugging.MapSize.NORMAL.side,
            "huge should be bigger than normal",
        )
    }
}
