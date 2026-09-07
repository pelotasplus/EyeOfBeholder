package pl.pelotasplus.eyeofbeholder.data.model

/**
 * What a spell the party cast sends down the corridor, for the ones that send
 * anything.
 *
 * Most spells send nothing: they mend somebody, or wrap them in something, or
 * ask a wall a question. These are the ones a monster at the far end of a
 * passage has reason to fear, and every number here is transcribed from the
 * spell's own entry.
 *
 * How it looks and how far it carries are [flies]'s business, since the party
 * and a monster loose the same bolt and it is drawn once.
 */
data class ThrownSpell(
    /** The bolt itself: its picture, its reach, the noise it makes. */
    val flies: MonsterSpell,

    /** What it rolls where it arrives, before the caster is counted in. */
    val dealing: DamageDice,

    /** And how many times that roll is counted, which is the caster's doing. */
    val counted: CountedBy,

    /** What kinds of harm it is, and so what turns it aside. */
    val hurting: Set<HarmKind>,

    /**
     * Whether it takes the party too, and not only what they aimed it at.
     *
     * A missile knows whose it is and passes through them. A fireball and a
     * bolt of lightning do not, and the corridor they are cast down had better
     * be a long one.
     */
    val takesEitherSide: Boolean = false,

    /**
     * Whether it takes everything on the square it comes down on rather than
     * picking one. A burning one does; a missile finds a single mark.
     */
    val takesTheWholeSquare: Boolean = false,

    /**
     * Whether it carries on past whatever it found rather than being spent on
     * it.
     *
     * One spell of the fourteen is written this way, and by name rather than
     * by any rule: a bolt of lightning goes the length of the corridor and
     * takes what is standing in it, where everything else stops at the first
     * thing it touches.
     */
    val carriesOn: Boolean = false,

    /**
     * Whether it takes the four squares beside the one it came down on as
     * well — see [Projectile.Harm.spreads].
     *
     * A storm alone, and it is the difference between a spell for a corridor
     * and a spell for a room.
     */
    val spreads: Boolean = false,

    /**
     * The hold it lays rather than the harm it does, for the two spells that
     * stop a creature instead of hurting it.
     *
     * Such a one rolls no damage at all, and what it does is decided entirely
     * by [AHold]: which kinds it will take hold of, what they may throw to
     * shrug it off, and how long it lasts.
     */
    val holds: AHold? = null,

    /**
     * What may be thrown against it, or nothing for the ones there is no
     * shrugging off. A missile is the only thing in the game with no throw
     * against it at all.
     */
    val thrownOff: SavingThrow? = null,

    /** And what making that throw is worth, for the ones that allow one. */
    val aMadeThrowIsWorth: WhatAMadeThrowIsWorth = WhatAMadeThrowIsWorth.HALF_OF_IT,
) {
    /**
     * The whole of what it does to one creature, for a caster of [casterLevel].
     *
     * The roll is made once and multiplied rather than rolled again for each
     * count, so a mage throwing five missiles throws five of the same size.
     */
    fun dealtBy(casterLevel: Int) = Projectile.Harm(
        dice = dealing,
        times = counted.forACasterOf(casterLevel),
        hurting = hurting,
        takesEitherSide = takesEitherSide,
        everybody = takesTheWholeSquare,
        carriesOn = carriesOn,
        spreads = spreads,
        holds = holds,
        thrownOff = thrownOff,
        aMadeThrowIsWorth = aMadeThrowIsWorth,
    )

    companion object {
        /**
         * How practised anything read out of a scroll or a wand is, whoever
         * is holding it.
         *
         * The words do the work rather than the reader, so a fighter with a
         * scroll throws exactly what a ninth-level mage would. It is what
         * makes a scroll worth carrying to somebody who could never learn the
         * spell.
         */
        const val AS_READ_FROM_A_SCROLL = 9

        /**
         * A spell that stops a creature rather than hurting it: the same bolt
         * as anything else, and nothing rolled where it lands.
         *
         * How practised the caster is buys nothing here. A hold either takes
         * or it does not, and the creature's own throw is the whole of what
         * decides which — so the count and the dice below are both nothing.
         */
        fun thatHolds(flies: MonsterSpell, hold: AHold) = ThrownSpell(
            flies = flies,
            dealing = DamageDice(times = 0, pips = 0, base = 0),
            counted = CountedBy.EVERY_LEVEL,
            hurting = emptySet(),
            holds = hold,
        )
    }
}

/**
 * How many times a spell's dice are counted, which is what makes a practised
 * caster worth more than a new one.
 */
enum class CountedBy {
    /** One for every level: a fireball, a bolt of lightning. */
    EVERY_LEVEL,

    /** One for every two levels past the first, which is the missiles. */
    EVERY_SECOND_LEVEL;

    /**
     * Never fewer than one. A caster too new to have earned a second count
     * still throws one, which is what stops a first-level mage's spell
     * arriving as nothing at all.
     */
    fun forACasterOf(level: Int): Int = when (this) {
        EVERY_LEVEL -> level
        EVERY_SECOND_LEVEL -> (level - 1) / 2
    }.coerceAtLeast(1)
}
