package pl.pelotasplus.eyeofbeholder.data

import pl.pelotasplus.eyeofbeholder.data.model.BookPosition
import pl.pelotasplus.eyeofbeholder.data.model.Champion
import pl.pelotasplus.eyeofbeholder.data.model.CharacterClass
import pl.pelotasplus.eyeofbeholder.data.model.ClassLevel
import pl.pelotasplus.eyeofbeholder.data.model.Discipline
import pl.pelotasplus.eyeofbeholder.data.model.PartySlot
import pl.pelotasplus.eyeofbeholder.data.model.Spell
import pl.pelotasplus.eyeofbeholder.data.model.SpellBook
import pl.pelotasplus.eyeofbeholder.data.model.SpellBookLayout
import pl.pelotasplus.eyeofbeholder.data.model.SpellPages
import pl.pelotasplus.eyeofbeholder.data.model.SpellSlot
import pl.pelotasplus.eyeofbeholder.data.model.XpPoints
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The book opened over the compass, on its own: which line it opens on, what
 * a tab and the scroll buttons do, and what casting does to the page.
 *
 * The page is the champion's own ten places, read as they stand: a place
 * being learned is a blank line, nine are ever shown, and a spell cast goes
 * to the ninth place to be learned again while the rest close up behind it.
 */
class WhatTheSpellBookDoesTest {

    private val mm = SpellSlot.Ready(Spell.MAGIC_MISSILE)
    private val shield = SpellSlot.Ready(Spell.SHIELD)
    private val armour = SpellSlot.Ready(Spell.ARMOUR)

    private fun pages(vararg places: Pair<Int, SpellSlot>) =
        places.fold(SpellPages.NONE) { pages, (at, place) -> pages.with(at, place) }

    private fun book(pages: SpellPages, at: BookPosition = BookPosition.START) =
        SpellBook(PartySlot(3), Discipline.MAGE, pages, at)

    private fun mage(pages: SpellPages, leftAt: BookPosition = BookPosition.START) =
        Champion.NOBODY.copy(mageSpells = pages, bookLeftAt = leftAt)

    // --- opening --------------------------------------------------------------

    @Test
    fun `it opens on the line it was left on`() {
        val opened = SpellBook.opened(mage(pages(0 to mm, 1 to shield), BookPosition(line = 1)), PartySlot(3), Discipline.MAGE)

        assertEquals(BookPosition(page = 0, scrolledBy = 0, line = 1), opened.at)
    }

    /** A line with nothing ready on it gives way to the next that has. */
    @Test
    fun `and moves down off a line with nothing ready`() {
        val opened = SpellBook.opened(
            mage(pages(0 to mm, 1 to SpellSlot.Learning(Spell.SHIELD), 2 to armour)),
            PartySlot(3),
            Discipline.MAGE,
        )

        assertEquals(BookPosition(page = 0, scrolledBy = 0, line = 0), opened.at)

        val fromALearningLine = SpellBook.opened(
            mage(pages(0 to mm, 1 to SpellSlot.Learning(Spell.SHIELD), 2 to armour), BookPosition(line = 1)),
            PartySlot(3),
            Discipline.MAGE,
        )
        assertEquals(2, fromALearningLine.at.line)
    }

    /** Onto the second half where that is where the first ready one is. */
    @Test
    fun `onto the second half of the page if it has to`() {
        val opened = SpellBook.opened(mage(pages(7 to mm)), PartySlot(3), Discipline.MAGE)

        assertEquals(BookPosition(page = 0, scrolledBy = 6, line = 1), opened.at)
    }

    @Test
    fun `and onto the way out where nothing is ready`() {
        val opened = SpellBook.opened(mage(pages(0 to SpellSlot.Learning(Spell.SHIELD))), PartySlot(3), Discipline.MAGE)

        assertTrue(opened.onAbort)
        assertEquals(null, opened.pickedSpell)
    }

    // --- what shows ---------------------------------------------------------

    @Test
    fun `a place being learned is a blank line, not closed up`() {
        val shown = book(pages(0 to mm, 1 to SpellSlot.Learning(Spell.SHIELD), 2 to armour)).lines

        assertEquals(listOf(Spell.MAGIC_MISSILE, null, Spell.ARMOUR, null, null, null), shown)
    }

    /** The tenth place is never shown, so a full page scrolls to three lines. */
    @Test
    fun `nine places are ever shown`() {
        val full = pages(*Array(10) { it to mm })

        val second = book(full).scrolled(down = true)

        assertEquals(6, second.at.scrolledBy)
        assertEquals(listOf(Spell.MAGIC_MISSILE, Spell.MAGIC_MISSILE, Spell.MAGIC_MISSILE, null, null, null), second.lines)
    }

    // --- tabs and scrolling -------------------------------------------------

    @Test
    fun `a tab opens at its first ready line`() {
        val book = book(pages(10 to SpellSlot.Learning(Spell.BLUR), 11 to SpellSlot.Ready(Spell.MELFS_ACID_ARROW)))

        assertEquals(BookPosition(page = 1, scrolledBy = 0, line = 1), book.onTab(1).at)
        assertTrue(book.onTab(2).onAbort, "an empty tab picked a line")
    }

    @Test
    fun `scrolling passes over a half with nothing on it`() {
        assertEquals(0, book(pages(0 to mm, 1 to shield)).scrolled(down = true).at.scrolledBy)
        assertTrue(!book(pages(0 to mm, 1 to shield)).canScroll)
        assertTrue(book(pages(0 to mm, 6 to shield)).canScroll)
    }

    // --- casting ------------------------------------------------------------

    /**
     * The cast place closes up, everything after it moves up one, and the
     * spell is put ninth to be learned again.
     */
    @Test
    fun `casting uses the place up and closes the page behind it`() {
        val cast = book(pages(0 to mm, 1 to shield, 2 to armour)).cast()

        assertEquals(shield, cast.pages.slots[0])
        assertEquals(armour, cast.pages.slots[1])
        assertEquals(SpellSlot.Empty, cast.pages.slots[2])
        assertEquals(SpellSlot.Learning(Spell.MAGIC_MISSILE), cast.pages.slots[8])
        assertEquals(0, cast.at.line, "the line moved off a spell still ready")
    }

    /** What closes up onto the line is still to learn, so the line above is picked. */
    @Test
    fun `the line moves up off what is still to learn`() {
        val cast = book(pages(0 to mm, 1 to shield, 2 to SpellSlot.Learning(Spell.ARMOUR)), BookPosition(line = 1)).cast()

        assertEquals(0, cast.at.line)
    }

    @Test
    fun `and onto the way out from the top`() {
        val cast = book(pages(0 to mm, 1 to SpellSlot.Learning(Spell.ARMOUR))).cast()

        assertTrue(cast.onAbort)
    }

    @Test
    fun `turn undead is never used up`() {
        val cleric = SpellBook(
            PartySlot(2),
            Discipline.CLERIC,
            pages(0 to SpellSlot.Ready(Spell.TURN_UNDEAD)),
            BookPosition.START,
        )

        assertEquals(cleric, cleric.cast())
    }

    // --- the caster's level -------------------------------------------------

    @Test
    fun `a paladin casts as a cleric eight levels below`() {
        fun paladin(level: Int) = Champion.NOBODY.copy(
            characterClass = CharacterClass.PALADIN,
            levels = listOf(ClassLevel(level, XpPoints(0))),
        )

        assertEquals(1, SpellBook.casterLevel(paladin(6), Discipline.CLERIC))
        assertEquals(1, SpellBook.casterLevel(paladin(9), Discipline.CLERIC))
        assertEquals(3, SpellBook.casterLevel(paladin(11), Discipline.CLERIC))
    }

    // --- where a click lands ------------------------------------------------

    @Test
    fun `the parts of the book are where they are drawn`() {
        assertEquals(SpellBookLayout.Part.Tab(0), SpellBookLayout.partAt(70, 125))
        assertEquals(SpellBookLayout.Part.Tab(5), SpellBookLayout.partAt(175, 125))
        assertEquals(SpellBookLayout.Part.Line(0), SpellBookLayout.partAt(80, 132))
        assertEquals(SpellBookLayout.Part.Line(5), SpellBookLayout.partAt(80, 167))
        assertEquals(SpellBookLayout.Part.Abort, SpellBookLayout.partAt(100, 170))
        assertEquals(SpellBookLayout.Part.Scroll(down = false), SpellBookLayout.partAt(150, 170))
        assertEquals(SpellBookLayout.Part.Scroll(down = true), SpellBookLayout.partAt(165, 170))
    }
}
