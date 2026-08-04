package pl.pelotasplus.eyeofbeholder.rendering

import pl.pelotasplus.eyeofbeholder.data.model.BlockOffset
import pl.pelotasplus.eyeofbeholder.data.model.viewBlockRows
import pl.pelotasplus.eyeofbeholder.data.model.viewSlots
import pl.pelotasplus.eyeofbeholder.data.model.visibleBlocks
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The squares in sight are numbered in two places — once by the table of
 * squares things stand on, once by the order [visibleBlocks] lists them in —
 * and a face finds its square's number through the second. Nothing makes the
 * two agree, so this does: getting a number wrong hands a face some other
 * square's window and cuts it in the wrong place, which is a silent fault.
 */
class ViewGeometryTest {

    @Test
    fun `the squares things stand on are numbered as the view lists them`() {
        viewBlockRows.values.flatten().forEach { block ->
            assertEquals(
                block.blockIndex,
                visibleBlocks.indexOf(BlockOffset(block.relativeX, block.relativeY)),
                "square at ${block.relativeX},${block.relativeY}",
            )
        }
    }

    /** Every face belongs to a square in sight; none of them is nowhere. */
    @Test
    fun `every view position names a square in sight`() {
        viewSlots.forEach { slot ->
            assertTrue(slot.block in visibleBlocks.indices, "${slot.label} is not a face of any square")
        }
    }

    /**
     * The rows the party see: seven squares three ahead, five two ahead, three
     * one ahead, and three on their own row. The party's own square is one of
     * them, which is why there are 18 and not 17.
     */
    @Test
    fun `the view is eighteen squares in four rows`() {
        assertEquals(18, visibleBlocks.size)
        assertEquals(
            listOf(7, 5, 3, 3),
            visibleBlocks.groupBy { it.relativeY }.toSortedMap().values.map { it.size },
        )
    }
}
