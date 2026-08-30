package pl.pelotasplus.eyeofbeholder.data.model

/**
 * What a monster's blow can leave on a champion besides the wound.
 *
 * Three of them, and they are alike enough to be one thing: each is a bit of
 * its own, each is thrown against, and none of them is tried unless the blow
 * drew blood — a miss leaves nothing, and so does a blow that came to nothing.
 *
 * None of them is refreshed either. A champion already held is not held
 * again, and the clock they are already on is the one that frees them.
 */
enum class WhatABlowLeaves(
    val thrownAgainst: SavingThrow,
    /** How long it holds, or none where nothing but a cure lifts it. */
    val holdsFor: Ticks?,
    /** What the line about it calls it. Transcribed, capital letters and all. */
    val calledIt: String,
) {
    /**
     * Venom, which does not hold the champion at all — it takes a little off
     * them again and again until somebody sees to them.
     */
    POISON(
        thrownAgainst = SavingThrow.PARALYSIS_POISON_OR_DEATH,
        holdsFor = null,
        calledIt = "poisoned",
    ),

    /** Held where they stand, and let go of after a while. */
    PARALYSIS(
        thrownAgainst = SavingThrow.PETRIFICATION_OR_POLYMORPH,
        holdsFor = Ticks(5 * A_WHILE),
        calledIt = "paralyzed",
    ),

    /**
     * Turned to stone, which no clock undoes. It takes everything else with
     * it: whatever else was wrong with the champion is gone, and so is
     * whatever was helping them.
     */
    PETRIFICATION(
        thrownAgainst = SavingThrow.PETRIFICATION_OR_POLYMORPH,
        holdsFor = null,
        calledIt = "PETRIFIED",
    );

    /** Whether this is already on [champion], who cannot take it twice. */
    fun alreadyOn(champion: Champion): Boolean = when (this) {
        POISON -> champion.poisoned
        PARALYSIS -> champion.paralysed
        PETRIFICATION -> champion.petrified
    }

    /** [champion] with this left on them. */
    fun leftOn(champion: Champion): Champion = when (this) {
        POISON -> champion.poisoned(true)
        PARALYSIS -> champion.paralysed(true)
        PETRIFICATION -> champion.turnedToStone()
    }
}

/**
 * The clock everything slow runs on, which is about half a minute of playing.
 * The venom's bite comes round on it, and so does being let go of.
 */
private const val A_WHILE = 546
