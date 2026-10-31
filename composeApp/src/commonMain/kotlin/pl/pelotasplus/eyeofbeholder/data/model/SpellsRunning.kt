package pl.pelotasplus.eyeofbeholder.data.model

/**
 * How long a spell goes on for once it has been cast.
 *
 * Two shapes of answer, and what separates them is not how long but whether
 * there is a clock at all.
 */
sealed interface HowLongASpellRuns {

    /**
     * No clock: it runs until something ends it.
     *
     * Not a long time — no time. Nothing counts it down, so it survives a
     * rest and a flight of stairs, and what finishes it instead is whoever
     * is under it doing something that gives them away. That is the whole of
     * what makes such a spell worth casting, and the whole of its cost.
     */
    data object UntilSomethingBreaksIt : HowLongASpellRuns
}

/**
 * A counted time, grown by the level of whoever cast it.
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
) : HowLongASpellRuns {
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

    /**
     * What is left of its clock, or null for one that has none — see
     * [HowLongASpellRuns.UntilSomethingBreaksIt].
     *
     * Null rather than a very large number: a clockless spell is not one
     * with a long time left, and a sentinel would quietly become a duration
     * the first time something subtracted from it.
     */
    val ticksLeft: Int?,

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
     * How many hit points it lent, for the one spell that lends any.
     *
     * Kept rather than rolled again at the end, because what it takes back
     * must be what it gave: an aid that rolled three and took back eight
     * would be a slow way of killing whoever was aided.
     */
    val lent: Int = 0,

    /**
     * Whether the one thing it was holding back has been used.
     *
     * Only a mystic defence has anything to spend: its shield goes on the
     * first blast of fire that reaches the party, while the spell itself runs
     * on to its own end regardless — so the two do not finish together, and
     * one flag cannot say both.
     */
    val spent: Boolean = false,

    /** The hand it has charged, for a spell that charges one — see [ChargedHand]. */
    val charged: ChargedHand? = null,
)

/**
 * A hand a spell has charged, and what a blow with it does.
 *
 * The hand itself stays empty: the charge is the spell's and not a thing
 * carried, so nothing can be put into that hand, nothing taken out, and the
 * charge is gone with the spell. A blow with it is a blow like any other,
 * rolled for, and the charge goes into the first one that does any harm. A
 * miss keeps it, and so does a blow the creature is quick enough to take
 * nothing from.
 *
 * Spending it leaves the spell running to its own end, charge and all gone:
 * its running out is still told when the time comes.
 *
 * [strikesFor] has the caster's level already counted into its base.
 */
data class ChargedHand(
    val hand: CarrySlot,
    val strikesFor: DamageDice,
    val looksLike: ItemIconId,
)

/**
 * What a spell puts into a hand, and how long it stays there.
 *
 * The two spells that charge a hand read alike and are nothing alike to use.
 * One is a touch: it is worth more in a better mage's hand, and it is gone
 * into the first blow that does any harm, so its whole duration is time to
 * find something to hit. The other is a weapon: the same in anybody's hand,
 * and it strikes with for as long as the spell runs.
 *
 * [grownByCasterLevel] is the first of those and [goesIntoOneBlow] the
 * second, said separately because they are separate facts about the charge
 * even though two spells happen to divide them between them.
 */
data class HandCharge(
    val strikesFor: DamageDice,

    /** The icon the hand shows while the charge is in it, from the item sheet. */
    val looksLike: ItemIconId,

    val grownByCasterLevel: Boolean = false,
    val goesIntoOneBlow: Boolean = false,
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

    /**
     * On each of them separately: a haste.
     *
     * Six spells and not one over the party, which is the game's own choice
     * and shows: each can be dispelled on its own, and somebody who joins
     * afterwards is not under it.
     */
    EVERY_CHAMPION,

    /**
     * On whichever champion is pointed at, the casting waiting until one is:
     * an aid, a slow poison.
     *
     * The asking is the same asking a cure does, and is refused the same way
     * — nothing is spent until somebody has been named, so a question thought
     * better of costs neither the scroll nor the hand.
     */
    WHOEVER_IS_POINTED_AT,
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
        lent: Int = 0,
        charged: ChargedHand? = null,
    ): SpellsRunning? {
        if (on == null && isRunning(spell)) return null
        if (on != null && isOn(spell, on)) return null

        val ticks = when (val lasts = spell.lasts) {
            null -> return null
            HowLongASpellRuns.UntilSomethingBreaksIt -> null
            is SpellLasts -> lasts.castBySomeoneOfLevel(casterLevel).value
        }

        return SpellsRunning(
            all + RunningSpell(
                spell = spell,
                castBy = by,
                ticksLeft = ticks,
                on = on,
                lent = lent,
                charged = charged,
            )
        )
    }

    /** The charge on [whose]'s [hand], if a spell has charged it. */
    fun chargeOn(whose: PartySlot, hand: CarrySlot): ChargedHand? =
        all.firstNotNullOfOrNull { spell -> spell.charged?.takeIf { spell.on == whose && it.hand == hand } }

    /** Whether either of [whose]'s hands is charged. */
    fun hasAChargedHand(whose: PartySlot): Boolean = all.any { it.on == whose && it.charged != null }

    /**
     * [whose]'s [hand] after a blow that did harm, the spell that charged it
     * still running.
     *
     * A touch is gone into the blow; a weapon is not, and strikes with for as
     * long as the spell runs — see [HandCharge.goesIntoOneBlow].
     */
    fun chargeSpent(whose: PartySlot, hand: CarrySlot) = SpellsRunning(
        all.map {
            if (it.on == whose && it.charged?.hand == hand &&
                it.spell.chargesAHand?.goesIntoOneBlow == true
            ) {
                it.copy(charged = null)
            } else {
                it
            }
        }
    )

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

    /**
     * What is left after [by], and which of them ran out in that step.
     *
     * A spell with no clock is not touched and never appears among the ones
     * that ended: there is nothing to take time off, so no length of time
     * finishes it. That is also what carries one through a rest, which runs
     * the clocks forward by an hour at a time.
     */
    fun runDown(by: Ticks): Pair<SpellsRunning, List<RunningSpell>> {
        val stepped = all.map {
            if (it.ticksLeft == null) it else it.copy(ticksLeft = it.ticksLeft - by.value)
        }
        val (running, ended) = stepped.partition { it.ticksLeft == null || it.ticksLeft > 0 }
        return SpellsRunning(running) to ended
    }

    /** Whether anything running is on a clock, which is what the clock runs for. */
    val anyOnAClock: Boolean get() = all.any { it.ticksLeft != null }

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

    /**
     * Whether [whom] cannot be seen, by whichever of the spells does it.
     *
     * Asked of the champion and not of a spell, because what being unseen is
     * worth does not depend on which one put them there: a monster passes
     * over them the same either way.
     */
    fun unseen(whom: PartySlot): Boolean = over(whom).any { it.spell.hidesThem }

    /**
     * What is left once [whom] has given themselves away, and which spells
     * that cost them.
     *
     * Only the hiding that breaks: an improved invisibility is built to
     * survive being acted under, so a champion can swing out of one and stay
     * out of sight. Nothing else on them is touched — giving yourself away
     * is not the same as being dispelled.
     *
     * One champion only, even for the spell that hid all six. A hiding that
     * reaches the whole party is six hidings rather than one over them, so
     * the one who swings steps out of cover and leaves the others in it.
     */
    fun givenAwayBy(whom: PartySlot): Pair<SpellsRunning, List<RunningSpell>> {
        val (broken, left) = all.partition {
            it.spell.hidingBreaksOnActing && it.on == whom
        }
        return SpellsRunning(left) to broken
    }

    /** Whether [whom] is hastened, which halves what a swing costs them. */
    fun hastened(whom: PartySlot): Boolean = over(whom).any { it.spell.hastens }

    /** How much the spells on [whom] add to their own rolls to hit. */
    fun helpStriking(whom: PartySlot): Int = over(whom).sumOf { it.spell.helpsStriking }

    /** Whether [whom] is carrying lent hit points, which frames their box. */
    fun aided(whom: PartySlot): Boolean = over(whom).any { it.lent > 0 }

    /**
     * Whether anything on [whom] frames their box yellow.
     *
     * Over the party counts, so a bless shows on all six at once — a box is
     * framed for what is on the champion in it, and from there a spell over
     * the party is no different from one laid on them.
     */
    fun framedYellow(whom: PartySlot): Boolean =
        over(whom).any { it.spell.framesTheBoxYellow }

    /** Every one of them ended at once, which is what a rest does. */
    fun allEnded(): SpellsRunning = SpellsRunning()

    fun spent(spell: Spell): SpellsRunning = SpellsRunning(
        all.map { if (it.spell == spell) it.copy(spent = true) else it }
    )
}
