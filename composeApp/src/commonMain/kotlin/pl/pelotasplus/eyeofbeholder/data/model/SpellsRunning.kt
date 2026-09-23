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
     * Whom it is on, or null for one that is on the party as a whole.
     *
     * The two are different things rather than one thing counted twice: a
     * detect magic is over the party and belongs to nobody, while a blur is
     * on one champion and moves nothing about the other five. A spell that
     * reaches all six is six of these and not a party-wide one, because each
     * of them can be dispelled, and because a champion who joins afterwards
     * is not under it.
     */
    val on: PartySlot? = null,

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

/**
 * Whom a lasting spell settles on once it is cast.
 *
 * The game keeps this as bits on the spell: one for the caster, one for the
 * party as a whole, one for every champion at once, and one that means ask
 * first. They are named here rather than tested as bits, because which of
 * them a spell carries is the whole of how it is cast.
 */
enum class SettlesOn {
    /** Over all of them and belonging to none: a detect magic, a prayer. */
    THE_PARTY,

    /** On whoever read it, without asking: a blur, a shield. */
    WHOEVER_CAST_IT,
}

/** Every spell still running over the party, and nothing that has ended. */
data class SpellsRunning(val all: List<RunningSpell> = emptyList()) {

    /** The party-wide one, which is the only kind asked for without a slot. */
    operator fun get(spell: Spell): RunningSpell? =
        all.firstOrNull { it.spell == spell && it.on == null }

    fun isRunning(spell: Spell): Boolean = get(spell) != null

    /** Whether [spell] is on [whom] in particular, party-wide ones aside. */
    fun isOn(spell: Spell, whom: PartySlot): Boolean =
        all.any { it.spell == spell && it.on == whom }

    /**
     * Everything on [whom] — what was cast on them and what is over the party
     * alike, because from a champion's own side the two are the same thing.
     */
    fun over(whom: PartySlot): List<RunningSpell> =
        all.filter { it.on == null || it.on == whom }

    /**
     * The same spell cast again, or null where one is already running — on
     * the same champion, or over the party where [on] is null.
     *
     * Refusing rather than restarting is what lets the caster be told, and
     * keeps a scroll from being spent on something already in force. Two
     * champions may each carry their own, which is why the slot is part of
     * the question and not only the spell.
     */
    fun begun(
        spell: Spell,
        by: PartySlot,
        casterLevel: Int,
        on: PartySlot? = null,
    ): SpellsRunning? {
        if (on == null && isRunning(spell)) return null
        if (on != null && isOn(spell, on)) return null

        val lasts = spell.lasts ?: return null
        return SpellsRunning(
            all + RunningSpell(
                spell = spell,
                castBy = by,
                ticksLeft = lasts.castBySomeoneOfLevel(casterLevel).value,
                on = on,
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

    /**
     * How much harder the spells on [whom] make them to hit, taken off a
     * monster's roll rather than added to their armour.
     *
     * Two spells cannot each be worth their two: the game takes the
     * hindrances one after another off the same roll, so they add.
     */
    fun hindranceStriking(whom: PartySlot): Int =
        over(whom).sumOf { it.spell.hindersStriking }

    /** Whether a blur is on [whom], which is drawn round their portrait. */
    fun blurred(whom: PartySlot): Boolean = isOn(Spell.BLUR, whom)

    /** Every one of them ended at once, which is what a rest does. */
    fun allEnded(): SpellsRunning = SpellsRunning()

    fun spent(spell: Spell): SpellsRunning = SpellsRunning(
        all.map { if (it.spell == spell) it.copy(spent = true) else it }
    )
}
