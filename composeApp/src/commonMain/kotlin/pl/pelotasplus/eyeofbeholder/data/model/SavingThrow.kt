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
 * Whether a monster shrugs [off].
 *
 * A creature saves on the fighters' table at its own level, whatever else it
 * is. The game keeps no table for monsters and hands them a fighter's, and
 * nothing about a creature makes it hardier than that — there is no answer to
 * [hardinessAgainst] on this side.
 */
fun MonsterProperty.saves(off: SavingThrow, dice: Dice): Boolean =
    dice.roll(1, 20, 0).coerceIn(1, 20) >= SavesLike.AS_A_FIGHTER.wants(off, level)

/**
 * What a throw made against something is worth to whoever made it.
 *
 * Transcribed, and the three outcomes are not a scale: what a spell allows is
 * written on the spell, so making the throw against one thing is worth half
 * of it and against another the whole.
 */
enum class WhatAMadeThrowIsWorth {
    /** Half the harm, which is what a burning one allows. */
    HALF_OF_IT,

    /** All of it: the thing is turned aside and does nothing whatever. */
    ALL_OF_IT,

    /** Nothing at all — the throw is made and changes none of it. */
    NONE_OF_IT;

    /** What is left of [harm] once a throw has been made against it. */
    fun of(harm: Damage): Damage = when (this) {
        HALF_OF_IT -> Damage(harm.points / 2)
        ALL_OF_IT -> Damage(0)
        NONE_OF_IT -> harm
    }
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
 * @property wanted what to roll against each [SavingThrow], as the steps a
 *   class improves by — see [Step].
 * @property stopsImproving the level past which nothing more is gained.
 */
private enum class SavesLike(
    val stopsImproving: Int,
    val wanted: Map<SavingThrow, List<Step>>,
) {
    /** Two levels a step, and the last step written is past the cap. */
    AS_A_FIGHTER(
        stopsImproving = 17,
        wanted = mapOf(
            SavingThrow.PARALYSIS_POISON_OR_DEATH to listOf(
                1 needs 16, 2 needs 14, 4 needs 13, 6 needs 11, 8 needs 10,
                10 needs 8, 12 needs 7, 14 needs 5, 16 needs 4, 18 needs 3,
            ),
            SavingThrow.A_ROD_STAFF_OR_WAND to listOf(
                1 needs 18, 2 needs 16, 4 needs 15, 6 needs 13, 8 needs 12,
                10 needs 10, 12 needs 9, 14 needs 7, 16 needs 6, 18 needs 5,
            ),
            SavingThrow.PETRIFICATION_OR_POLYMORPH to listOf(
                1 needs 17, 2 needs 15, 4 needs 14, 6 needs 12, 8 needs 11,
                10 needs 9, 12 needs 8, 14 needs 6, 16 needs 5, 18 needs 4,
            ),
            SavingThrow.A_BREATH_WEAPON to listOf(
                1 needs 20, 2 needs 17, 4 needs 16, 6 needs 13, 8 needs 12,
                10 needs 9, 12 needs 8, 14 needs 5, 16 needs 4, 18 needs 4,
            ),
            SavingThrow.A_SPELL to listOf(
                1 needs 19, 2 needs 17, 4 needs 16, 6 needs 14, 8 needs 13,
                10 needs 11, 12 needs 10, 14 needs 8, 16 needs 7, 18 needs 6,
            ),
        ),
    ),

    /** Five levels a step, and the slowest to improve of the four. */
    AS_A_MAGE(
        stopsImproving = 21,
        wanted = mapOf(
            SavingThrow.PARALYSIS_POISON_OR_DEATH to listOf(
                1 needs 14, 5 needs 13, 10 needs 11, 15 needs 10, 20 needs 8,
            ),
            SavingThrow.A_ROD_STAFF_OR_WAND to listOf(
                1 needs 11, 5 needs 9, 10 needs 7, 15 needs 5, 20 needs 3,
            ),
            SavingThrow.PETRIFICATION_OR_POLYMORPH to listOf(
                1 needs 13, 5 needs 11, 10 needs 9, 15 needs 7, 20 needs 5,
            ),
            SavingThrow.A_BREATH_WEAPON to listOf(
                1 needs 15, 5 needs 13, 10 needs 11, 15 needs 9, 20 needs 7,
            ),
            SavingThrow.A_SPELL to listOf(
                1 needs 12, 5 needs 10, 10 needs 8, 15 needs 6, 20 needs 4,
            ),
        ),
    ),

    /** Three levels a step, and the best of the four against being held. */
    AS_A_CLERIC(
        stopsImproving = 19,
        wanted = mapOf(
            SavingThrow.PARALYSIS_POISON_OR_DEATH to listOf(
                1 needs 10, 3 needs 9, 6 needs 7, 9 needs 6, 12 needs 5, 15 needs 4, 18 needs 2,
            ),
            SavingThrow.A_ROD_STAFF_OR_WAND to listOf(
                1 needs 14, 3 needs 13, 6 needs 11, 9 needs 10, 12 needs 9, 15 needs 8, 18 needs 6,
            ),
            SavingThrow.PETRIFICATION_OR_POLYMORPH to listOf(
                1 needs 13, 3 needs 12, 6 needs 10, 9 needs 9, 12 needs 8, 15 needs 7, 18 needs 5,
            ),
            SavingThrow.A_BREATH_WEAPON to listOf(
                1 needs 16, 3 needs 15, 6 needs 13, 9 needs 12, 12 needs 11, 15 needs 10, 18 needs 8,
            ),
            SavingThrow.A_SPELL to listOf(
                1 needs 15, 3 needs 14, 6 needs 12, 9 needs 11, 12 needs 10, 15 needs 9, 18 needs 7,
            ),
        ),
    ),

    /**
     * Four levels a step. One row goes the wrong way at the last step, which
     * is the game's own and not a slip here: a thief of twenty saves worse
     * against a wand than one of sixteen did.
     */
    AS_A_THIEF(
        stopsImproving = 21,
        wanted = mapOf(
            SavingThrow.PARALYSIS_POISON_OR_DEATH to listOf(
                1 needs 13, 4 needs 12, 8 needs 11, 12 needs 10, 16 needs 9, 20 needs 8,
            ),
            SavingThrow.A_ROD_STAFF_OR_WAND to listOf(
                1 needs 14, 4 needs 12, 8 needs 10, 12 needs 8, 16 needs 6, 20 needs 7,
            ),
            SavingThrow.PETRIFICATION_OR_POLYMORPH to listOf(
                1 needs 12, 4 needs 11, 8 needs 10, 12 needs 9, 16 needs 8, 20 needs 4,
            ),
            SavingThrow.A_BREATH_WEAPON to listOf(
                1 needs 16, 4 needs 15, 8 needs 14, 12 needs 13, 16 needs 12, 20 needs 11,
            ),
            SavingThrow.A_SPELL to listOf(
                1 needs 15, 4 needs 13, 8 needs 11, 12 needs 9, 16 needs 7, 20 needs 5,
            ),
        ),
    );

    /**
     * What a champion of this class and [level] needs against [off].
     *
     * The last step to have been reached, which is why the steps are written
     * in order. A champion with no level at all counts as a first-level one,
     * which is the worst step there is.
     *
     * Where the cap falls can leave the last step of a table out of reach — a
     * fighter stops at seventeen and the best numbers written for one begin at
     * eighteen. Both the cap and the step nobody reaches are the game's, and
     * are left as they are rather than tidied into agreement.
     */
    fun wants(off: SavingThrow, level: Int): Int {
        val reached = minOf(level, stopsImproving).coerceAtLeast(FIRST)

        return wanted.getValue(off).last { it.fromLevel <= reached }.needsToRoll
    }

    private companion object {
        const val FIRST = 1
    }
}

/**
 * One step of a class's improvement: what to roll from [fromLevel] until the
 * next step is reached.
 *
 * Written out rather than counted, so that a row says at which level each of
 * its numbers starts applying instead of leaving it to be worked back out of
 * the row's length.
 */
private class Step(val fromLevel: Int, val needsToRoll: Int)

/** `4 needs 12` — from the fourth level, a twelve. */
private infix fun Int.needs(roll: Int) = Step(fromLevel = this, needsToRoll = roll)
