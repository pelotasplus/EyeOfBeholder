package pl.pelotasplus.eyeofbeholder.data

import pl.pelotasplus.eyeofbeholder.data.model.DialogueScene
import pl.pelotasplus.eyeofbeholder.data.model.Font
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The strip along the bottom is a fixed number of lines deep, and what is
 * written into it is not.
 *
 * A speech, a reply and a speech again all stand in the same box — the box is
 * only emptied by being drawn again — so between them they outgrow it. What
 * overruns is not drawn anywhere: it goes past the bottom of the panel, over
 * the view and under the button. So it has to be cut into pages instead, read
 * off with the button that turns any other page.
 *
 * Khelben on the ninth floor is the one that gives it away — he says three
 * long things into one box without a break between them.
 */
class WhatTheBoxHoldsTest {

    /** Eight pixels a line and six across, which is the shape of the real one. */
    private val font = Font(width = 6, height = 8, glyphs = emptyList())

    private val strip = DialogueScene.ReadOff.TheStripBelow

    private fun pages(text: String) = DialogueScene.pagesThatFit(text, font, strip)

    /** As many lines as fit between the top of the words and the button. */
    private val fits = strip.linesThatFit(font)

    private fun aLine(n: Int) = "line$n"

    private fun linesOf(howMany: Int) = (1..howMany).joinToString("\n") { aLine(it) }

    @Test
    fun `what fits is left alone`() {
        val text = linesOf(fits)

        assertEquals(listOf(text), pages(text), "a speech that fitted was cut up anyway")
    }

    @Test
    fun `and one line more is a second page`() {
        val cut = pages(linesOf(fits + 1))

        assertEquals(2, cut.size, "a speech a line too long was left to run off the bottom")
        assertEquals(aLine(fits + 1), cut[1], "the wrong line was carried over")
    }

    @Test
    fun `no page is deeper than the box`() {
        val cut = pages(linesOf(fits * 3 + 2))

        assertTrue(
            cut.all { font.wrap(it, strip.textWidth).size <= fits },
            "a page was cut that still does not fit",
        )
    }

    @Test
    fun `nothing is lost in the cutting`() {
        val whole = linesOf(fits * 2 + 3)

        assertEquals(
            font.wrap(whole, strip.textWidth),
            pages(whole).flatMap { font.wrap(it, strip.textWidth) },
            "cutting the speech into pages dropped or reflowed a line",
        )
    }

    /** Nothing to say is still a page, so nothing has to ask whether there is one. */
    @Test
    fun `an empty speech is one empty page`() {
        assertEquals(listOf(""), pages(""))
    }

    // --- and what of it stands there now -------------------------------------

    private fun inTheBox(text: String, canBeReadOff: Boolean) =
        DialogueScene.standingInTheBox(text, font, strip, canBeReadOff)

    /** With a button, the reader gets the first page and turns to the rest. */
    @Test
    fun `a speech that can be read off starts at the beginning`() {
        val (shown, unread) = inTheBox(linesOf(fits + 2), canBeReadOff = true)

        assertEquals(linesOf(fits), shown, "it did not start at the top of the speech")
        assertEquals(2, font.wrap(unread.single(), strip.textWidth).size, "the rest was lost")
    }

    /**
     * With no button there is nothing to turn the page with, so holding
     * anything back would leave the words waiting on a click that never
     * comes. The box has scrolled instead, and a scrolled box is full: it
     * holds the last lines said, not the remainder after the last page.
     *
     * Showing the remainder leaves the top of the box blank and everything
     * that should have scrolled up into it gone, which is what a speech two
     * lines too long looked like.
     */
    @Test
    fun `and one that cannot is full of the end of it`() {
        val (shown, unread) = inTheBox(linesOf(fits + 2), canBeReadOff = false)

        assertEquals(emptyList(), unread, "words were held back with no way to reach them")
        assertEquals(
            (3..fits + 2).map { aLine(it) },
            font.wrap(shown, strip.textWidth),
            "the box did not scroll to hold the last of what was said",
        )
    }

    /** However far it overran, the box ends up full rather than part full. */
    @Test
    fun `a scrolled box is a full one`() {
        listOf(fits + 1, fits + 2, fits * 2, fits * 3 + 1).forEach { howMany ->
            val (shown, _) = inTheBox(linesOf(howMany), canBeReadOff = false)

            assertEquals(
                fits,
                font.wrap(shown, strip.textWidth).size,
                "a box holding $howMany lines' worth came out part full",
            )
        }
    }

    @Test
    fun `a speech that fits is the same either way`() {
        val text = linesOf(fits)

        assertEquals(text to emptyList(), inTheBox(text, canBeReadOff = true))
        assertEquals(text to emptyList(), inTheBox(text, canBeReadOff = false))
    }

    // --- and that it only ever moves forward ---------------------------------

    private fun boxfuls(text: String, alreadyRead: Int) =
        DialogueScene.boxfulsLeft(text, font, strip, alreadyRead)

    /**
     * Khelben's scene on the ninth floor, in miniature: the box is drawn
     * once and three things are said into it without another draw between
     * them, so what is said last must be written on after what was said
     * first — not over the top of it.
     */
    @Test
    fun `what is said next is written on from where the reading stopped`() {
        val already = fits

        val next = boxfuls(linesOf(fits + 2), alreadyRead = already)

        assertEquals(1, next.size, "two more lines were made into more than one boxful")
        assertEquals(
            (3..fits + 2).map { aLine(it) },
            font.wrap(next.single(), strip.textWidth),
            "the box went back to the beginning of what had already been read",
        )
    }

    /** Nothing already read is ever shown again as something new to read. */
    @Test
    fun `a boxful already read is never handed back`() {
        val whole = linesOf(fits * 3)

        val first = boxfuls(whole, alreadyRead = 0).first()
        val after = boxfuls(whole, alreadyRead = fits).first()

        assertTrue(
            first != after,
            "the reader was shown the same boxful twice with nothing said between",
        )
        assertEquals(
            (fits + 1..fits * 2).map { aLine(it) },
            font.wrap(after, strip.textWidth),
            "the second boxful was not the one after the first",
        )
    }

    @Test
    fun `it stops once for each boxful still to be read`() {
        assertEquals(3, boxfuls(linesOf(fits * 3), alreadyRead = 0).size)
        assertEquals(2, boxfuls(linesOf(fits * 3), alreadyRead = fits).size)
        assertEquals(1, boxfuls(linesOf(fits * 3), alreadyRead = fits * 2).size)
    }

    /** Read to the end and there is still the boxful standing there. */
    @Test
    fun `everything read leaves what is on the screen`() {
        val whole = linesOf(fits * 2)

        assertEquals(
            listOf(font.wrap(whole, strip.textWidth).takeLast(fits).joinToString("\n")),
            boxfuls(whole, alreadyRead = fits * 2),
            "reading to the end emptied the box instead of leaving it standing",
        )
    }

    @Test
    fun `every boxful is a boxful`() {
        assertTrue(
            boxfuls(linesOf(fits * 3 + 2), alreadyRead = 0)
                .all { font.wrap(it, strip.textWidth).size == fits },
            "a boxful came out part full",
        )
    }

    @Test
    fun `the box holds more than one line`() {
        assertTrue(fits > 1, "the box was measured as holding a single line, which cannot be right")
    }
}
