package pl.pelotasplus.eyeofbeholder.data.model

/**
 * How long a spell goes on for once it has been cast.
 *
 * The three numbers are data and cannot be worked out: reasoning about them
 * will not check them, and a digit changed by eye has nothing to catch it. A
 * duration wrong by a factor of the caster's level still looks perfectly
 * ordinary on screen, so what would notice is a test naming the number.
 *
 * [length] is the unit the game counts in — always half a minute — and the
 * other two say how many of them: [base] flat, plus [perLevel] for every
 * level the caster has. So a spell can last a fixed time, a time that grows
 * with the caster, or both.
 */
data class SpellLasts(
    val base: Int,
    val perLevel: Int,
    val length: Int = HALF_A_MINUTE,
) {
    fun castBySomeoneOfLevel(level: Int) = Ticks(length * base + length * level * perLevel)

    companion object {
        /** Every duration in the game is built out of half-minutes. */
        const val HALF_A_MINUTE = 546
    }
}

/**
 * A spell that goes on after the words are said, and what is left of it.
 *
 * [castBy] is kept because the end is announced in the caster's name, however
 * long ago they read it and whoever is holding what now.
 */
data class RunningSpell(
    val spell: Spell,
    val castBy: PartySlot,
    val ticksLeft: Int,

    /**
     * Whether the one thing it was holding back has been used.
     *
     * Only a mystic defence has anything to spend: its shield goes on the
     * first blast of fire that reaches the party, while the spell itself runs
     * on to its own end regardless — so the two do not finish together, and
     * one flag cannot say both.
     */
    val spent: Boolean = false,
)

/** Every spell still running over the party, and nothing that has ended. */
data class SpellsRunning(val all: List<RunningSpell> = emptyList()) {

    operator fun get(spell: Spell): RunningSpell? = all.firstOrNull { it.spell == spell }

    fun isRunning(spell: Spell): Boolean = get(spell) != null

    /**
     * The same spell cast again, or null where one is already running.
     *
     * Refusing rather than restarting is what lets the caster be told, and
     * keeps a scroll from being spent on something already in force.
     */
    fun begun(spell: Spell, by: PartySlot, casterLevel: Int): SpellsRunning? {
        if (isRunning(spell)) return null
        val lasts = spell.lasts ?: return null
        return SpellsRunning(
            all + RunningSpell(
                spell = spell,
                castBy = by,
                ticksLeft = lasts.castBySomeoneOfLevel(casterLevel).value,
            )
        )
    }

    /**
     * The same again, whatever was there before, and running its whole
     * duration from now.
     *
     * For the spell that can be cast again while an earlier one still runs —
     * a mystic defence whose shield has already gone. [begun] is the usual
     * answer, and refuses.
     */
    fun begunAgain(spell: Spell, by: PartySlot, casterLevel: Int): SpellsRunning =
        SpellsRunning(all.filterNot { it.spell == spell }).begun(spell, by, casterLevel) ?: this

    /** What is left after [by], and which of them ran out in that step. */
    fun runDown(by: Ticks): Pair<SpellsRunning, List<RunningSpell>> {
        val stepped = all.map { it.copy(ticksLeft = it.ticksLeft - by.value) }
        val (running, ended) = stepped.partition { it.ticksLeft > 0 }
        return SpellsRunning(running) to ended
    }

    /** Every one of them ended at once, which is what a rest does. */
    fun allEnded(): SpellsRunning = SpellsRunning()

    fun spent(spell: Spell): SpellsRunning = SpellsRunning(
        all.map { if (it.spell == spell) it.copy(spent = true) else it }
    )
}
