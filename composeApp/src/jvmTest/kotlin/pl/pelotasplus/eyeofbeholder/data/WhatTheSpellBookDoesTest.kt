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
 * The page is the champion's own ten places, closed up with what is ready
 * first: nine are ever shown, and a spell cast goes to be learned again
 * behind what is still ready.
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

    /** The page may have been chosen afresh since: a book left on its second half comes back to what is left. */
    @Test
    fun `a book left past its last spell opens on that spell`() {
        val two = pages(0 to mm, 1 to shield)

        val leftScrolled = SpellBook.opened(mage(two, BookPosition(scrolledBy = 6, line = 2)), PartySlot(3), Discipline.MAGE)
        assertEquals(BookPosition(page = 0, scrolledBy = 0, line = 1), leftScrolled.at)

        val leftLow = SpellBook.opened(mage(two, BookPosition(line = 4)), PartySlot(3), Discipline.MAGE)
        assertEquals(Spell.SHIELD, leftLow.pickedSpell)
    }

    @Test
    fun `and onto the way out where nothing is ready`() {
        val opened = SpellBook.opened(mage(pages(0 to SpellSlot.Learning(Spell.SHIELD))), PartySlot(3), Discipline.MAGE)

        assertTrue(opened.onAbort)
        assertEquals(null, opened.pickedSpell)
    }

    // --- what shows ---------------------------------------------------------

    @Test
    fun `a page closes up with what is ready first, leaving no gap`() {
        val learning = SpellSlot.Learning(Spell.SHIELD)
        val closed = pages(0 to mm, 1 to learning, 2 to learning, 4 to armour, 12 to learning, 13 to shield).readyFirst()

        assertEquals(listOf(mm, armour, learning, learning) + List(6) { SpellSlot.Empty }, closed.page(0))
        assertEquals(listOf(shield, learning), closed.page(1).take(2), "a page was closed up into the next")
        assertEquals(listOf(Spell.MAGIC_MISSILE, Spell.ARMOUR, null, null, null, null), book(closed).lines)
    }

    /** The tenth place is never shown, so a full page scrolls to three lines. */
    @Test
    fun `nine places are ever shown`() {
        val full = pages(*Array(10) { it to mm })

        val second = book(full).scrolled()

        assertEquals(6, second.at.scrolledBy)
        assertEquals(listOf(Spell.MAGIC_MISSILE, Spell.MAGIC_MISSILE, Spell.MAGIC_MISSILE, null, null, null), second.lines)
    }

    // --- tabs and scrolling -------------------------------------------------

    @Test
    fun `a tab opens at its first ready line`() {
        val book = book(pages(10 to SpellSlot.Learning(Spell.BLUR), 11 to SpellSlot.Ready(Spell.MELFS_ACID_ARROW)))

        assertEquals(BookPosition(page = 1, scrolledBy = 0, line = 0), book.onTab(1).at)
        assertEquals(Spell.MELFS_ACID_ARROW, book.onTab(1).pickedSpell)
        assertTrue(book.onTab(2).onAbort, "an empty tab picked a line")
    }

    @Test
    fun `scrolling passes over a half with nothing on it`() {
        assertEquals(0, book(pages(0 to mm, 1 to shield)).scrolled().at.scrolledBy)
        assertTrue(!book(pages(0 to mm, 1 to shield)).canScroll)
        assertTrue(book(pages(*Array(7) { it to mm })).canScroll)
    }

    /** A book showing a second half with nothing left on it goes back to the first. */
    @Test
    fun `scrolling from an emptied second half goes back to the first`() {
        val stale = book(pages(0 to mm, 1 to shield), BookPosition(scrolledBy = 6))

        assertEquals(0, stale.scrolled().at.scrolledBy)
    }

    /** Writing a place closes its page up, so a page never has a gap however it was written. */
    @Test
    fun `writing a place closes its page up`() {
        val written = pages(0 to mm, 1 to shield).with(0, SpellSlot.Learning(Spell.MAGIC_MISSILE))

        assertEquals(listOf(shield, SpellSlot.Learning(Spell.MAGIC_MISSILE)), written.page(0).take(2))
    }

    /** A page that reaches the book with a gap still opens on a ready spell. */
    @Test
    fun `a gapped page still opens on a ready spell`() {
        val gapped = SpellPages(listOf(mm, SpellSlot.Learning(Spell.SHIELD), armour) + List(77) { SpellSlot.Empty })

        val opened = SpellBook.opened(mage(gapped, BookPosition(line = 1)), PartySlot(3), Discipline.MAGE)

        assertEquals(Spell.MAGIC_MISSILE, opened.pickedSpell)
    }

    @Test
    fun `spells cast and learned again come back to the top of the page`() {
        val castTwice = book(pages(0 to mm, 1 to mm)).cast().cast()
        val rested = SpellPages(castTwice.pages.slots.map { (it as? SpellSlot.Learning)?.let { l -> SpellSlot.Ready(l.spell) } ?: it })
        val opened = SpellBook.opened(mage(rested), PartySlot(3), Discipline.MAGE)

        assertEquals(listOf(Spell.MAGIC_MISSILE, Spell.MAGIC_MISSILE, null, null, null, null), opened.lines)
        assertTrue(!opened.canScroll)
    }

    // --- casting ------------------------------------------------------------

    /** The cast place closes up, and the spell goes behind what is still ready to be learned again. */
    @Test
    fun `casting uses the place up and closes the page behind it`() {
        val cast = book(pages(0 to mm, 1 to shield, 2 to armour)).cast()

        assertEquals(shield, cast.pages.slots[0])
        assertEquals(armour, cast.pages.slots[1])
        assertEquals(SpellSlot.Learning(Spell.MAGIC_MISSILE), cast.pages.slots[2])
        assertEquals(SpellSlot.Empty, cast.pages.slots[8])
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
        assertEquals(SpellBookLayout.Part.Scroll, SpellBookLayout.partAt(150, 170))
        assertEquals(SpellBookLayout.Part.Scroll, SpellBookLayout.partAt(165, 170))
    }
}
