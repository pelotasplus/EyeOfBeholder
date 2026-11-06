package pl.pelotasplus.eyeofbeholder.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.abs

/** Which of the two kinds of spells: a mage's, out of a book, or a cleric's, prayed for. */
enum class Discipline {
    MAGE,
    CLERIC;

    /**
     * The spell a number on this discipline's list names, or none.
     *
     * Each discipline numbers its own spells from one, and the cleric's list
     * follows the mage's in the one table every spell is kept in — so a
     * cleric's first spell is the table's [CLERICS_START]th.
     */
    fun spellNumbered(onTheList: Int): Spell? = when (this) {
        MAGE -> Spell.of(onTheList)
        CLERIC -> Spell.of(CLERICS_START + onTheList)
    }

    /** And the other way: where [spell] stands on this discipline's list. */
    fun numberOf(spell: Spell): Int = when (this) {
        MAGE -> spell.asWritten
        CLERIC -> spell.asWritten - CLERICS_START
    }

    private companion object {
        const val CLERICS_START = 33
    }
}

/** One place on a page of a champion's spells. */
@Serializable
sealed interface SpellSlot {

    val spell: Spell?

    @Serializable
    @SerialName("empty")
    data object Empty : SpellSlot {
        override val spell: Spell? get() = null
    }

    /** Chosen, and not learned yet: a rest is what turns it [Ready]. */
    @Serializable
    @SerialName("learning")
    data class Learning(override val spell: Spell) : SpellSlot

    /** Learned, and there to be cast. Casting it puts it back to [Learning]. */
    @Serializable
    @SerialName("ready")
    data class Ready(override val spell: Spell) : SpellSlot
}

/**
 * A champion's spells of one discipline: a page for each level of spell, ten
 * places to a page.
 *
 * Eight pages are kept and six are ever shown, so the last two are carried
 * as a save has them and nothing here puts anything on them. A page is kept
 * closed up — [with] and [withEach] do it to what they change, [readyFirst]
 * to pages built whole — so a ready spell never sits behind one still being
 * learned or an empty place.
 */
@Serializable
data class SpellPages(val slots: List<SpellSlot> = List(PAGES * PER_PAGE) { SpellSlot.Empty }) {

    fun page(index: Int): List<SpellSlot> = slots.subList(index * PER_PAGE, (index + 1) * PER_PAGE)

    /** [place] put at [at], and its page closed up: the place it lands in may not be [at]. */
    fun with(at: Int, place: SpellSlot) = withEach(mapOf(at to place))

    /** Several places changed at once, numbered as they stand now, and their pages closed up. */
    fun withEach(changes: Map<Int, SpellSlot>): SpellPages {
        if (changes.isEmpty()) return this
        val changed = slots.toMutableList()
        changes.forEach { (at, place) -> changed[at] = place }
        changes.keys.map { it / PER_PAGE }.distinct().forEach { page ->
            val from = page * PER_PAGE
            changed.subList(from, minOf(from + PER_PAGE, changed.size)).closeUp()
        }
        return SpellPages(changed)
    }

    /** Each page closed up: what is ready first, then what is being learned, then the empty places. */
    fun readyFirst() = SpellPages(
        slots.toMutableList().apply {
            for (from in indices step PER_PAGE) subList(from, minOf(from + PER_PAGE, size)).closeUp()
        },
    )

    private fun MutableList<SpellSlot>.closeUp() {
        val closed = sortedBy {
            when (it) {
                is SpellSlot.Ready -> 0
                is SpellSlot.Learning -> 1
                SpellSlot.Empty -> 2
            }
        }
        closed.forEachIndexed { i, place -> this[i] = place }
    }

    companion object {
        const val PAGES = 8
        const val PER_PAGE = 10

        val NONE = SpellPages()

        /**
         * A page set as a saved game writes it: one signed byte a place, the
         * spell's number on [discipline]'s list — positive learned, negative
         * still to learn, nought empty.
         *
         * A number that names no spell is read as an empty place rather than
         * failing the whole party: it can only come of a damaged save.
         */
        fun read(bytes: List<Int>, discipline: Discipline) = SpellPages(
            bytes.map { written ->
                val spell = discipline.spellNumbered(abs(written))
                when {
                    spell == null -> SpellSlot.Empty
                    written > 0 -> SpellSlot.Ready(spell)
                    else -> SpellSlot.Learning(spell)
                }
            },
        ).readyFirst()
    }
}

/**
 * Where a champion last left their book open, so that it opens there again:
 * which page, how far the list was scrolled, and which line was picked.
 */
@Serializable
data class BookPosition(val page: Int = 0, val scrolledBy: Int = 0, val line: Int = 0) {
    companion object {
        val START = BookPosition()
    }
}

/**
 * The mage spells a champion's book holds, as a saved game writes them: one
 * bit a spell, the lowest bit the first on the list.
 */
fun mageSpellsWrittenIn(bits: Long): Set<Spell> =
    (0 until Long.SIZE_BITS)
        .filter { (bits shr it) and 1L == 1L }
        .mapNotNull { Discipline.MAGE.spellNumbered(it + 1) }
        .toSet()
