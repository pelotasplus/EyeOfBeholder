package pl.pelotasplus.eyeofbeholder.data.model

/**
 * What a champion is given a chance to shrug off.
 *
 * Five of them, each a row of its own in the tables. A blow that allows no
 * throw at all is not a sixth kind: it names a number these do not cover, and
 * [of] answers nothing for it.
 */
enum class SavingThrow {
    PARALYSIS_POISON_OR_DEATH,
    A_ROD_STAFF_OR_WAND,
    PETRIFICATION_OR_POLYMORPH,
    A_BREATH_WEAPON,
    A_SPELL;

    companion object {
        /** Which throw a blow or an attack names, or none where it allows one. */
        fun of(named: Int): SavingThrow? = entries.getOrNull(named)
    }
}

/**
 * Whether [off] is shrugged off.
 *
 * A twenty-sided die at or above what this champion's class wants of them,
 * which gets easier as they gain levels and is easier again for the three
 * races that are hardy.
 */
fun Champion.saves(off: SavingThrow, dice: Dice): Boolean {
    val wanted = savesLike.wants(off, levels.firstOrNull()?.level ?: 1) - hardinessAgainst(off)

    return dice.roll(1, 20, 0).coerceIn(1, 20) >= wanted
}

/**
 * What a stout constitution takes off the number wanted.
 *
 * Three races are hardy and the rest are not, however strong: dwarves and
 * halflings shrug off poison and magic alike, gnomes only magic. It is the
 * one place a champion's constitution is asked about outside their hit
 * points.
 */
private fun Champion.hardinessAgainst(off: SavingThrow): Int {
    val hardy = when (race) {
        Race.DWARF, Race.HALFLING -> off in AGAINST_POISON_AND_MAGIC
        Race.GNOME -> off in AGAINST_MAGIC
        else -> false
    }

    if (!hardy) return 0

    return STOUTNESS.getOrElse(abilities.constitution.current) { 0 }
}

private val AGAINST_MAGIC = setOf(
    SavingThrow.A_SPELL,
    SavingThrow.A_ROD_STAFF_OR_WAND,
)

private val AGAINST_POISON_AND_MAGIC =
    AGAINST_MAGIC + SavingThrow.PARALYSIS_POISON_OR_DEATH

/** What each score of constitution is worth to a hardy race. Transcribed. */
private val STOUTNESS = listOf(
    0, 0, 0, 0, 1, 1, 1, 2, 2, 2, 2, 3, 3, 3, 4, 4, 4, 4, 5, 5,
)

/**
 * Which of the four progressions a class saves by.
 *
 * The same grouping as [toHitProgression]: a class that hits like a fighter
 * saves like one, and the game keeps the two in separate tables that happen
 * to agree. They are written out separately here for the same reason, and a
 * test holds them to it.
 */
private val Champion.savesLike: SavesLike
    get() = when (characterClass) {
        CharacterClass.MAGE -> SavesLike.AS_A_MAGE

        CharacterClass.CLERIC,
        CharacterClass.CLERIC_THIEF,
        CharacterClass.CLERIC_MAGE -> SavesLike.AS_A_CLERIC

        CharacterClass.THIEF,
        CharacterClass.THIEF_MAGE -> SavesLike.AS_A_THIEF

        else -> SavesLike.AS_A_FIGHTER
    }

/**
 * What each class needs to roll, by what it is saving against and how far it
 * has got. The four tables are transcribed from the game.
 *
 * A class improves in bands rather than every level — a fighter every two
 * levels, a mage every five — and stops improving at a level of its own,
 * which is why the four are different lengths.
 *
 * @property wanted one row per [SavingThrow], in that order, and one column
 *   per band of levels.
 * @property stopsImproving the level past which nothing more is gained.
 * @property everyLevels how many levels one band covers.
 */
private enum class SavesLike(
    val stopsImproving: Int,
    val everyLevels: Int,
    val wanted: List<List<Int>>,
) {
    AS_A_FIGHTER(
        stopsImproving = 17,
        everyLevels = 2,
        wanted = listOf(
            listOf(16, 14, 13, 11, 10, 8, 7, 5, 4, 3),
            listOf(18, 16, 15, 13, 12, 10, 9, 7, 6, 5),
            listOf(17, 15, 14, 12, 11, 9, 8, 6, 5, 4),
            listOf(20, 17, 16, 13, 12, 9, 8, 5, 4, 4),
            listOf(19, 17, 16, 14, 13, 11, 10, 8, 7, 6),
        ),
    ),

    AS_A_MAGE(
        stopsImproving = 21,
        everyLevels = 5,
        wanted = listOf(
            listOf(14, 13, 11, 10, 8),
            listOf(11, 9, 7, 5, 3),
            listOf(13, 11, 9, 7, 5),
            listOf(15, 13, 11, 9, 7),
            listOf(12, 10, 8, 6, 4),
        ),
    ),

    AS_A_CLERIC(
        stopsImproving = 19,
        everyLevels = 3,
        wanted = listOf(
            listOf(10, 9, 7, 6, 5, 4, 2),
            listOf(14, 13, 11, 10, 9, 8, 6),
            listOf(13, 12, 10, 9, 8, 7, 5),
            listOf(16, 15, 13, 12, 11, 10, 8),
            listOf(15, 14, 12, 11, 10, 9, 7),
        ),
    ),

    AS_A_THIEF(
        stopsImproving = 21,
        everyLevels = 4,
        wanted = listOf(
            listOf(13, 12, 11, 10, 9, 8),
            listOf(14, 12, 10, 8, 6, 7),
            listOf(12, 11, 10, 9, 8, 4),
            listOf(16, 15, 14, 13, 12, 11),
            listOf(15, 13, 11, 9, 7, 5),
        ),
    );

    /**
     * What a champion of this class and [level] needs against [off].
     *
     * The level is not counted from zero: a first-level fighter falls in the
     * same band as a levelless one, which is the worst band there is.
     *
     * Where the cap falls can leave the last band of a table out of reach —
     * a fighter stops at seventeen, which lands one band short of the best
     * numbers written for one. Both the cap and the unreachable band are the
     * game's, and are left as they are rather than tidied into agreement.
     */
    fun wants(off: SavingThrow, level: Int): Int =
        wanted[off.ordinal][minOf(level, stopsImproving) / everyLevels]
}
