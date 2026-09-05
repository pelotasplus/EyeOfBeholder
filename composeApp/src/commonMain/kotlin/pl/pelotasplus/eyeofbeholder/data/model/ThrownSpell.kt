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
