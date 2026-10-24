package pl.pelotasplus.eyeofbeholder.data.model

/**
 * A champion's spells open over the compass: a mage's book or a cleric's
 * holy symbol, read a level at a time.
 *
 * The book shows the champion's own places on the page as they stand, six
 * at a time and nine in all, so a place still being learned or left empty is
 * a blank line rather than closed up. [at] is the page, how far it is
 * scrolled and which line is picked; [ABORT] is the line below the list that
 * closes the book.
 */
data class SpellBook(
    val whose: PartySlot,
    val discipline: Discipline,
    val pages: SpellPages,
    val at: BookPosition,
) {
    /** The six lines showing, each the spell ready there or nothing. */
    val lines: List<Spell?> get() = List(LINES) { readyAt(at.scrolledBy + it) }

    /** Whether the second half of the page has anything, which is what the scroll buttons are for. */
    val canScroll: Boolean get() = readyAt(LINES) != null

    val onAbort: Boolean get() = at.line == ABORT

    /** The tab clicked: its first ready line picked, or the way out where it has none. */
    fun onTab(page: Int): SpellBook {
        val opened = copy(at = BookPosition(page = page, scrolledBy = 0, line = 0))
        val first = (0 until LINES).firstOrNull { opened.readyAt(it) != null } ?: ABORT
        return opened.copy(at = opened.at.copy(line = first))
    }

    /**
     * The list moved on by a screenful, round to the other half and back. A
     * half with nothing ready on it is passed over, and a page whose first
     * place is not ready is left showing its second half.
     */
    fun scrolled(down: Boolean): SpellBook {
        if (readyAt(0) == null) return copy(at = at.copy(scrolledBy = LINES))

        var by = at.scrolledBy + if (down) LINES else -LINES
        if (by >= SHOWN) by = 0 else if (by < 0) by = SHOWN - SHOWN % LINES
        while ((by until minOf(by + LINES, SpellPages.PER_PAGE)).none { readyAt(it) != null }) {
            by = if (down) 0 else by - LINES
        }
        return copy(at = at.copy(scrolledBy = by, line = 0))
    }

    /** [line] picked, if a spell is ready on it. */
    fun picked(line: Int): SpellBook? {
        if (line !in 0 until LINES || lines[line] == null) return null
        return copy(at = at.copy(line = line))
    }

    val pickedSpell: Spell? get() = if (onAbort) null else lines.getOrNull(at.line)

    /**
     * The picked spell cast: its place used up and the rest of the page
     * closed up behind it, the spell gone to the end of the page to be learned
     * again. If what now sits on the picked line is still being learned, the
     * line above is picked instead, or the way out from the top. Turn undead
     * is a cleric's to keep and is never used up.
     */
    fun cast(): SpellBook {
        val spell = pickedSpell ?: return this
        if (spell == Spell.TURN_UNDEAD) return this

        val base = at.page * SpellPages.PER_PAGE
        val from = at.scrolledBy + at.line
        val slots = pages.slots.toMutableList()
        for (i in from until LAST) slots[base + i] = slots[base + i + 1]
        slots[base + LAST] = SpellSlot.Learning(spell)
        val used = copy(pages = SpellPages(slots))

        if (slots[base + from] !is SpellSlot.Learning) return used
        val line = at.line - 1
        return used.copy(
            at = when {
                line >= 0 -> at.copy(line = line)
                at.scrolledBy > 0 -> at.copy(scrolledBy = 0, line = LINES - 1)
                else -> at.copy(line = ABORT)
            },
        )
    }

    private fun readyAt(onThePage: Int): Spell? {
        if (onThePage !in 0 until SHOWN) return null
        return (pages.slots[at.page * SpellPages.PER_PAGE + onThePage] as? SpellSlot.Ready)?.spell
    }

    companion object {
        const val TABS = 6
        const val LINES = 6

        /** The line under the list, which is the way out. */
        const val ABORT = LINES

        /** How many places of a page the book shows; the last of the ten is never read. */
        private const val SHOWN = 9

        /** Where a spell used up goes to be learned again. */
        private const val LAST = 8

        /**
         * [whose]'s book opened where they left it. A line with nothing ready
         * on it moves down to the first that has, onto the second half if
         * that is where it is, and to the way out where there is none.
         */
        fun opened(champion: Champion, whose: PartySlot, discipline: Discipline): SpellBook {
            val book = SpellBook(whose, discipline, champion.spellsOf(discipline), champion.bookLeftAt)
            val by = book.at.scrolledBy
            if (book.readyAt(by + book.at.line) != null) return book

            for (line in book.at.line until SpellPages.PER_PAGE) {
                if (book.readyAt(by + line) == null) continue
                return book.copy(
                    at = if (line >= LINES) book.at.copy(scrolledBy = LINES, line = line - LINES)
                    else book.at.copy(line = line),
                )
            }
            return book.copy(at = book.at.copy(scrolledBy = 0, line = ABORT))
        }

        /**
         * The level a champion casts at from their own book: their level as a
         * mage, or as a cleric. A paladin prays as a cleric eight levels below
         * their own, and as a first-level one before that.
         */
        fun casterLevel(champion: Champion, discipline: Discipline): Int = when (discipline) {
            Discipline.MAGE -> champion.levelIn(CharacterClass.MAGE) ?: 1
            Discipline.CLERIC -> champion.levelIn(CharacterClass.CLERIC)
                ?: champion.levelIn(CharacterClass.PALADIN)?.takeIf { it > PALADIN_LAG }?.minus(PALADIN_LAG)
                ?: 1
        }

        private const val PALADIN_LAG = 8
    }
}

/** [book]'s pages and where it was left, back on the champion it belongs to. */
fun Champion.withBook(book: SpellBook): Champion = when (book.discipline) {
    Discipline.MAGE -> copy(mageSpells = book.pages, bookLeftAt = book.at)
    Discipline.CLERIC -> copy(clericSpells = book.pages, bookLeftAt = book.at)
}

/**
 * Where the book's parts are on the screen, over the compass. Every number
 * here is transcribed: the tabs along the top, the list under them a line of
 * the small font apiece, and the way out and the two scroll buttons along
 * the bottom.
 */
object SpellBookLayout {

    sealed interface Part {
        data class Tab(val page: Int) : Part
        data class Line(val line: Int) : Part
        data object Abort : Part
        data class Scroll(val down: Boolean) : Part
    }

    const val LEFT = 64
    const val TOP = 121
    const val WIDTH = 112
    const val HEIGHT = 56

    const val TAB_LEFT = 68
    const val TAB_WIDTH = 18
    const val TAB_HEIGHT = 9
    const val TAB_LABEL_LEFT = 7
    const val TAB_LABEL_TOP = 2

    const val LIST_TOP = TOP + TAB_HEIGHT
    const val LIST_HEIGHT = 47
    const val LINE_LEFT = 71
    const val LINE_TOP = 132
    const val LINE_HEIGHT = 6

    const val ABORT_LEFT = 68
    const val ABORT_TOP = 168
    const val ABORT_WIDTH = 78
    const val BUTTON_HEIGHT = 9
    const val ABORT_LABEL_LEFT = 74
    const val ABORT_LABEL_TOP = 170
    const val ABORT_LABEL = "ABORT SPELL"

    /** The two scroll buttons, side by side, and where each one's arrow sits. */
    const val UP_LEFT = 146
    const val UP_WIDTH = 14
    const val DOWN_LEFT = 160
    const val DOWN_WIDTH = 16
    const val UP_ARROW_LEFT = 150
    const val DOWN_ARROW_LEFT = 165

    /** Where the twelve pieces of the resting grid go: four across, three down. */
    val GRID_LEFTS = listOf(68, 98, 128, 144)
    val GRID_TOPS = listOf(130, 146, 152)

    /** How long a book rests after each casting. */
    val RESTS_FOR = Ticks(72)

    fun partAt(x: Int, y: Int): Part? {
        val right = TAB_LEFT + SpellBook.TABS * TAB_WIDTH
        return when {
            x !in TAB_LEFT until right -> null
            y in TOP until TOP + TAB_HEIGHT -> Part.Tab((x - TAB_LEFT) / TAB_WIDTH)
            y in LINE_TOP until LINE_TOP + SpellBook.LINES * LINE_HEIGHT -> Part.Line((y - LINE_TOP) / LINE_HEIGHT)
            y !in ABORT_TOP until ABORT_TOP + BUTTON_HEIGHT -> null
            x < UP_LEFT -> Part.Abort
            else -> Part.Scroll(down = x >= DOWN_LEFT)
        }
    }

    /** Whether [x],[y] is anywhere on the open book, which keeps the click from reaching the compass. */
    fun covers(x: Int, y: Int) = x in LEFT until LEFT + WIDTH && y in TOP until TOP + HEIGHT
}
