package pl.pelotasplus.eyeofbeholder.data.model

/**
 * A champion choosing their spells in camp: Memorize Spells for a mage, Pray
 * for Spells for a cleric or paladin.
 *
 * A page a level, a line a spell they may choose, and a count against each
 * line. Choosing is all that happens here — nothing is learned until the party
 * rest — and what leaving does is [chosenPages]: the champion's pages written
 * out again from the counts.
 */
data class SpellChoosing(
    val whose: PartySlot,
    val discipline: Discipline,
    /** Places on each page, from [SpellCapacity]. */
    val places: List<Int>,
    /** The spells offered on each page, in the order of their list. */
    val offered: List<List<Spell>>,
    /** How many of each are chosen now. */
    val chosen: Map<Spell, Int>,
    /** How many of each were learned when the menu was opened. */
    val learned: Map<Spell, Int>,
    val page: Int = 0,
    val line: Int = 0,
) {
    val onThisPage: List<Spell> get() = offered[page]

    fun countOf(spell: Spell): Int = chosen[spell] ?: 0

    /** How many places on [page] are still free. */
    fun remaining(page: Int = this.page): Int =
        places[page] - offered[page].sumOf { countOf(it) }

    fun onPage(page: Int) = copy(page = page, line = 0)

    fun onLine(line: Int) =
        if (onThisPage.isEmpty()) this else copy(line = line.coerceIn(0, onThisPage.lastIndex))

    /** One more of the spell on the picked line, where the page has room for it. */
    fun added(): SpellChoosing {
        val spell = onThisPage.getOrNull(line) ?: return this
        if (remaining() <= 0) return this
        return copy(chosen = chosen + (spell to countOf(spell) + 1))
    }

    /** One fewer of it, down to none. */
    fun takenOff(): SpellChoosing {
        val spell = onThisPage.getOrNull(line) ?: return this
        if (countOf(spell) == 0) return this
        return copy(chosen = chosen + (spell to countOf(spell) - 1))
    }

    /** Every spell on this page put back to none. */
    fun cleared() = copy(chosen = chosen - onThisPage.toSet())

    /**
     * The champion's pages written out again from what is chosen.
     *
     * Spell by spell, in the order of their list: as many as were learned
     * already go back in learned, and any more go in still to learn — each in
     * the first empty place on its page. So choosing fewer of a spell keeps
     * the learned ones, and choosing more costs only the new ones a rest.
     *
     * Every page is written afresh, and from everything held as well as
     * everything offered: a spell the champion can no longer be offered — a
     * page whose places they have lost — is kept as it was, not dropped. A
     * cleric's first place is turn undead, which is never chosen and always
     * there.
     */
    fun chosenPages(): SpellPages {
        val slots = MutableList<SpellSlot>(SpellPages.PAGES * SpellPages.PER_PAGE) { SpellSlot.Empty }
        if (discipline == Discipline.CLERIC) slots[0] = SpellSlot.Ready(Spell.TURN_UNDEAD)

        fun putOn(page: Int, slot: SpellSlot) {
            val first = (page * SpellPages.PER_PAGE until (page + 1) * SpellPages.PER_PAGE)
                .firstOrNull { slots[it] == SpellSlot.Empty } ?: return
            slots[first] = slot
        }

        (offered.flatten() + chosen.keys).distinct().sortedBy { discipline.numberOf(it) }.forEach { spell ->
            val page = SpellCapacity.pageOf(spell, discipline) ?: return@forEach
            val count = countOf(spell)
            val stillLearned = minOf(count, learned[spell] ?: 0)

            repeat(stillLearned) { putOn(page, SpellSlot.Ready(spell)) }
            repeat(count - stillLearned) { putOn(page, SpellSlot.Learning(spell)) }
        }

        return SpellPages(slots)
    }

    /** Whether anything chosen is still to be learned, which a rest is for. */
    val needsARest: Boolean
        get() = chosen.any { (spell, count) -> count > (learned[spell] ?: 0) }

    companion object {
        /** The pages a menu shows, of the eight kept: one a level, up to the sixth. */
        const val PAGES_SHOWN = 6

        /** And the lines a page has room for. */
        const val LINES = 10

        /**
         * The menu as it opens for [champion]: their places, what they may
         * choose, and what they hold already.
         *
         * A mage is offered everything in their book on every page, room or
         * none; a cleric or paladin only on a page with places.
         */
        fun opening(
            champion: Champion,
            whose: PartySlot,
            discipline: Discipline,
            wearingWizardry: Boolean = false,
        ): SpellChoosing {
            val places = SpellCapacity.of(champion, discipline, wearingWizardry)
            val listed = Spell.entries
                .filter { SpellCapacity.mayChoose(champion, discipline, it) }
                .filter { spell ->
                    discipline == Discipline.MAGE ||
                        SpellCapacity.pageOf(spell, discipline)?.let { places[it] > 0 } == true
                }
                .sortedBy { discipline.numberOf(it) }

            val offered = List(SpellPages.PAGES) { page ->
                listed.filter { SpellCapacity.pageOf(it, discipline) == page }.take(LINES)
            }

            val held = champion.spellsOf(discipline).slots
            val chosen = held.mapNotNull { it.choosable(discipline) }.groupingBy { it }.eachCount()
            val learned = held.mapNotNull { (it as? SpellSlot.Ready)?.spell?.takeIf { s -> s.choosable(discipline) } }
                .groupingBy { it }.eachCount()

            return SpellChoosing(whose, discipline, places, offered, chosen, learned)
        }

        private fun SpellSlot.choosable(discipline: Discipline): Spell? = when (this) {
            is SpellSlot.Ready -> spell.takeIf { it.choosable(discipline) }
            is SpellSlot.Learning -> spell.takeIf { it.choosable(discipline) }
            SpellSlot.Empty -> null
        }

        /**
         * Turn undead is a cleric's from the start and never counted among what
         * they chose. Only on the cleric's list: the same number on a mage's
         * is true seeing, and skipping it there would throw away every true
         * seeing a mage had chosen the moment the menu was left.
         */
        private fun Spell.choosable(discipline: Discipline) =
            !(discipline == Discipline.CLERIC && this == Spell.TURN_UNDEAD)
    }
}
