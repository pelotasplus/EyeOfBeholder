package pl.pelotasplus.eyeofbeholder.data

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A stored string carries instructions among its letters, and only the
 * letters are drawn.
 *
 * Everything below 31 is an instruction to whatever is drawing: a page break,
 * a colour and the byte saying which, a tab, a line break. The rest of that
 * range means nothing at all and the engine draws nothing for it — but a
 * reader that takes every byte for a letter puts a glyph there instead, which
 * is the mark on the end of "a vision of Khelben appears in your mind." Three
 * of the level messages end with byte 18.
 */
class WhatIsDrawnOfAStringTest {

    @Test
    fun `an instruction with no meaning is drawn as nothing`() {
        assertEquals(
            "a vision of Khelben.",
            "a vision of Khelben.".asTheGameDrawsIt(),
            "the stray byte on the end of Khelben's messages was drawn",
        )
    }

    @Test
    fun `every byte below thirty-one goes`() {
        val instructions = (1..30).filterNot { it == LINE_BREAK }
            .joinToString("") { it.toChar().toString() }

        assertEquals("", instructions.asTheGameDrawsIt(), "something below 31 was drawn")
    }

    @Test
    fun `a line break is kept, being about the shape of the words`() {
        assertEquals("one\ntwo", "one\rtwo".asTheGameDrawsIt())
    }

    /** The byte after a colour says which colour, and is not a letter. */
    @Test
    fun `a colour takes its argument with it`() {
        assertEquals("red", "!red".asTheGameDrawsIt(), "the colour's number was drawn")
        assertEquals("red", "!red".asTheGameDrawsIt(), "the colour's number was drawn")
    }

    /** An argument that would itself be a letter is still an argument. */
    @Test
    fun `and takes it even where it looks like one`() {
        assertEquals("bc", "abc".asTheGameDrawsIt())
    }

    @Test
    fun `ordinary words come through untouched`() {
        val said = "AFTER A MOMENT OF DIZZINESS A VISION OF KHELBEN APPEARS IN YOUR MIND."

        assertEquals(said, said.asTheGameDrawsIt())
    }

    private companion object {
        const val LINE_BREAK = 0x0D
    }
}
