package pl.pelotasplus.eyeofbeholder.data

import kotlinx.collections.immutable.toImmutableList
import pl.pelotasplus.eyeofbeholder.data.model.Cps
import pl.pelotasplus.eyeofbeholder.data.model.Font
import pl.pelotasplus.eyeofbeholder.data.model.GlyphRow
import pl.pelotasplus.eyeofbeholder.data.model.Palette
import pl.pelotasplus.eyeofbeholder.data.model.PaletteIndex
import pl.pelotasplus.eyeofbeholder.data.model.RGB
import pl.pelotasplus.eyeofbeholder.data.model.sequence.SequenceScreen
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A scene draws inside its window and nowhere else.
 *
 * The names roll up out of the top of the picture, and a line is still being
 * drawn while half of it has already left. Only the window is ever put back
 * between frames, so anything drawn above or below it is never cleared again:
 * one line leaving would smear across the black border and every line after it
 * would pile up on the same few rows.
 */
class TheRollStaysInsideThePictureTest {

    /** A sheet of one colour, so anything drawn over it is obvious. */
    private val sheet = Cps(
        name = "flat",
        width = SequenceScreen.WIDTH,
        height = SequenceScreen.HEIGHT,
        pixels = List(SequenceScreen.WIDTH * SequenceScreen.HEIGHT) { PaletteIndex(1) },
    )

    /** Every letter a solid block, so a glyph cannot miss by being mostly blank. */
    private val font = Font(
        width = 8,
        height = 8,
        glyphs = List(128) { Font.Glyph(List(8) { GlyphRow(0xFF00) }) },
    )

    private val palette = Palette(
        name = "flat",
        colors = List(256) { RGB(it, it, it) }.toImmutableList(),
    )

    private fun screen() = SequenceScreen().apply {
        light(palette)
        load(sheet)
        show()
    }

    /**
     * A line straddling the top edge is drawn to the edge and stops there,
     * and one straddling the bottom likewise.
     */
    @Test
    fun `a line half out of the window is cut off at the edge`() {
        val screen = screen()
        val before = screen.getRows()

        screen.insideThePicture {
            // Four pixels above the window's top edge, so half of an eight
            // pixel letter is outside it, and the same at the bottom.
            screen.writeAt("XXXX", font, INK, left = 40, top = SequenceScreen.PICTURE_TOP - 4)
            screen.writeAt(
                "XXXX", font, INK,
                left = 40,
                top = SequenceScreen.PICTURE_TOP + SequenceScreen.PICTURE_HEIGHT - 4,
            )
        }

        val after = screen.getRows()

        val spoiled = (0 until SequenceScreen.HEIGHT)
            .filter { it !in insideTheWindow }
            .filter { row -> after[row] != before[row] }

        assertEquals(emptyList(), spoiled, "the roll drew on rows outside the picture")

        assertTrue(
            after[SequenceScreen.PICTURE_TOP] != before[SequenceScreen.PICTURE_TOP],
            "nothing was drawn on the window's own top row, so the test proves nothing",
        )
    }

    /** And without the clip it would spill, which is what makes the test worth having. */
    @Test
    fun `unclipped, the same line spills over the edge`() {
        val screen = screen()
        val before = screen.getRows()

        screen.writeAt("XXXX", font, INK, left = 40, top = SequenceScreen.PICTURE_TOP - 4)

        val after = screen.getRows()
        val above = SequenceScreen.PICTURE_TOP - 1

        assertTrue(after[above] != before[above], "the unclipped write stayed inside the window")
    }

    private companion object {
        val INK = PaletteIndex(200)
        val insideTheWindow =
            SequenceScreen.PICTURE_TOP until SequenceScreen.PICTURE_TOP + SequenceScreen.PICTURE_HEIGHT
    }
}
