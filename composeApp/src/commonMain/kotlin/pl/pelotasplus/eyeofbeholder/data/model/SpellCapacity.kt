package pl.pelotasplus.eyeofbeholder.data.model

/**
 * How many spells of each level a champion may have chosen at once, and which
 * spells they may choose from.
 *
 * Every table here is data, read off the game rather than worked out: a row
 * per level of the class, a column per page. Reasoning about the numbers will
 * not check them, and a digit nudged by eye has nothing to catch it but the
 * tests that name rows.
 */
object SpellCapacity {

    /**
     * Places on each of the eight pages of [discipline] for [champion].
     *
     * A mage's come from their mage level, doubled on the fourth and fifth
     * pages by a ring of wizardry. A cleric's come from their cleric level,
     * with a wisdom bonus on any page that already has a place. A paladin
     * prays from a table of their own, and only for a few spells — see
     * [mayChoose]. Anybody else has none.
     */
    fun of(champion: Champion, discipline: Discipline, wearingWizardry: Boolean = false): List<Int> =
        when (discipline) {
            Discipline.MAGE -> {
                val level = champion.levelIn(CharacterClass.MAGE)
                val row = rowOf(MAGE, level)
                if (!wearingWizardry) row
                else row.mapIndexed { page, places -> if (page in WIZARDRY_DOUBLES) places * 2 else places }
            }

            Discipline.CLERIC -> {
                val cleric = champion.levelIn(CharacterClass.CLERIC)
                val paladin = champion.levelIn(CharacterClass.PALADIN)
                when {
                    cleric != null -> {
                        val bonus = wisdomBonus(champion.abilities.wisdom.current)
                        rowOf(CLERIC, cleric).mapIndexed { page, places ->
                            if (places == 0) 0 else places + bonus[page]
                        }
                    }

                    paladin != null -> rowOf(PALADIN, paladin)
                    else -> NONE
                }
            }
        }

    /**
     * Whether [champion] may choose [spell] at all, whatever room they have.
     *
     * A mage chooses only from what is written in their book. A cleric may
     * pray for any cleric spell but lay on hands, which is a paladin's; a
     * paladin for a short list of their own.
     */
    fun mayChoose(champion: Champion, discipline: Discipline, spell: Spell): Boolean {
        if (pageOf(spell, discipline) == null) return false

        val bit = 1L shl (discipline.numberOf(spell) - 1)
        return when (discipline) {
            Discipline.MAGE -> spell in champion.knownMageSpells
            Discipline.CLERIC -> when {
                champion.levelIn(CharacterClass.CLERIC) != null -> CLERIC_MAY_PRAY_FOR and bit != 0L
                champion.levelIn(CharacterClass.PALADIN) != null -> PALADIN_MAY_PRAY_FOR and bit != 0L
                else -> false
            }
        }
    }

    /**
     * Which page [spell] goes on — its level, counted from nought — or none
     * for a spell that is never chosen: turn undead, which is always there.
     */
    fun pageOf(spell: Spell, discipline: Discipline): Int? {
        val levels = when (discipline) {
            Discipline.MAGE -> MAGE_SPELL_LEVELS
            Discipline.CLERIC -> CLERIC_SPELL_LEVELS
        }
        val level = levels.getOrNull(discipline.numberOf(spell) - 1) ?: return null
        return (level - 1).takeIf { it >= 0 }
    }

    private fun rowOf(table: List<Int>, level: Int?): List<Int> {
        if (level == null || level < 1) return NONE
        return List(SpellPages.PAGES) { page -> table.getOrElse((level - 1) * SpellPages.PAGES + page) { 0 } }
    }

    /**
     * The bonus row for a wisdom: none below thirteen, and the last row the
     * table has for anything above it.
     */
    private fun wisdomBonus(wisdom: Int): List<Int> {
        val row = (wisdom - WISDOM_WITH_NO_BONUS).coerceIn(0, WISDOM_BONUS.size / SpellPages.PAGES - 1)
        return WISDOM_BONUS.subList(row * SpellPages.PAGES, (row + 1) * SpellPages.PAGES)
    }

    private val NONE = List(SpellPages.PAGES) { 0 }

    /** The pages a ring of wizardry doubles: the fourth and fifth levels. */
    private val WIZARDRY_DOUBLES = setOf(3, 4)

    private const val WISDOM_WITH_NO_BONUS = 12

    /** One bit a cleric spell, the lowest the first: every one but lay on hands. */
    private const val CLERIC_MAY_PRAY_FOR = 0xF7FFFFFFL

    private const val PALADIN_MAY_PRAY_FOR = 0xA9BBD1DL

    /** The level of each mage spell, by its number on the list. */
    private val MAGE_SPELL_LEVELS = listOf(
        1, 1, 1, 1, 1, 1, 2, 2,
        2, 2, 2, 3, 3, 3, 3, 3,
        3, 3, 4, 4, 4, 4, 5, 5,
        5, 6, 6, 6, 6, 7, 7, 8,
        0, 1, 1,
    )

    /** And each cleric spell's. Turn undead, last, has none: it is never chosen. */
    private val CLERIC_SPELL_LEVELS = listOf(
        1, 1, 1, 1, 1, 2, 2, 2,
        2, 3, 3, 3, 3, 3, 4, 4,
        4, 4, 5, 5, 5, 5, 5, 5,
        6, 6, 8, 8, 0,
    )

    private val MAGE = listOf(
        1, 0, 0, 0, 0, 0, 0, 0,
        2, 0, 0, 0, 0, 0, 0, 0,
        2, 1, 0, 0, 0, 0, 0, 0,
        3, 2, 0, 0, 0, 0, 0, 0,
        4, 2, 1, 0, 0, 0, 0, 0,
        4, 2, 2, 0, 0, 0, 0, 0,
        4, 3, 2, 1, 0, 0, 0, 0,
        4, 3, 3, 2, 0, 0, 0, 0,
        4, 3, 3, 2, 1, 0, 0, 0,
        4, 4, 3, 2, 2, 0, 0, 0,
        4, 4, 4, 3, 3, 0, 0, 0,
        4, 4, 4, 4, 4, 1, 0, 0,
        5, 5, 5, 4, 4, 2, 0, 0,
        5, 5, 5, 4, 4, 2, 1, 0,
        5, 5, 5, 5, 5, 2, 1, 0,
        5, 5, 5, 5, 5, 3, 2, 1,
        5, 5, 5, 5, 5, 3, 3, 2,
    )

    private val CLERIC = listOf(
        1, 0, 0, 0, 0, 0, 0, 0,
        2, 0, 0, 0, 0, 0, 0, 0,
        2, 1, 0, 0, 0, 0, 0, 0,
        3, 2, 0, 0, 0, 0, 0, 0,
        3, 3, 1, 0, 0, 0, 0, 0,
        3, 3, 2, 0, 0, 0, 0, 0,
        3, 3, 2, 1, 0, 0, 0, 0,
        3, 3, 3, 2, 0, 0, 0, 0,
        4, 4, 3, 2, 1, 0, 0, 0,
        4, 4, 3, 2, 2, 0, 0, 0,
        5, 4, 4, 3, 2, 1, 0, 0,
        6, 5, 5, 3, 2, 2, 0, 0,
        6, 6, 6, 4, 2, 2, 0, 0,
        6, 6, 6, 5, 3, 2, 1, 0,
        6, 6, 6, 6, 4, 2, 1, 0,
        7, 7, 7, 6, 4, 3, 1, 0,
        7, 7, 7, 7, 5, 3, 2, 0,
    )

    /** Rows for wisdom twelve and below, then one a point up to nineteen. */
    private val WISDOM_BONUS = listOf(
        0, 0, 0, 0, 0, 0, 0, 0,
        1, 0, 0, 0, 0, 0, 0, 0,
        2, 0, 0, 0, 0, 0, 0, 0,
        2, 1, 0, 0, 0, 0, 0, 0,
        2, 2, 0, 0, 0, 0, 0, 0,
        2, 2, 1, 0, 0, 0, 0, 0,
        2, 2, 1, 1, 0, 0, 0, 0,
        3, 2, 1, 2, 0, 0, 0, 0,
    )

    private val PALADIN = listOf(
        0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0,
        1, 0, 0, 0, 0, 0, 0, 0,
        2, 0, 0, 0, 0, 0, 0, 0,
        2, 1, 0, 0, 0, 0, 0, 0,
        2, 2, 0, 0, 0, 0, 0, 0,
        2, 2, 1, 0, 0, 0, 0, 0,
        3, 2, 1, 0, 0, 0, 0, 0,
        3, 2, 1, 1, 0, 0, 0, 0,
        3, 3, 2, 1, 0, 0, 0, 0,
        3, 3, 3, 1, 0, 0, 0, 0,
    )
}
