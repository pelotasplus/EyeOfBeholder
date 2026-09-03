package pl.pelotasplus.eyeofbeholder.data.model

/**
 * A hand that has swung and has not come back to rest, and how much longer it
 * has to go.
 *
 * A weapon is not swung as fast as the mouse can be clicked. The hand goes
 * out of use for a while afterwards, drawn over with the same grid
 * that it draws over something a champion may not use at all — a spellbook in
 * a fighter's hand — so a hand recovering and a hand holding the wrong thing
 * look alike and are refused alike.
 *
 * The wait comes in two parts. For the first the slot says what the blow came
 * to in place of the weapon's icon, and for the rest it shows the weapon again
 * and is still refused.
 */
data class HandRecovering(
    val whose: PartySlot,
    val hand: CarrySlot,
    val ticksLeft: Int,
    /** What the swing came to, which is worth saying only while it is news. */
    val came: WhatTheBlowCameTo?,
) {
    /**
     * Whether the slot is still reporting rather than showing the weapon.
     *
     * The report is the first [REPORTING] of the wait, whichever wait was
     * charged — so it is the whole of a short one and the first third of a
     * full one.
     */
    val stillReporting: Boolean
        get() = came != null && ticksLeft > came.wait.value - REPORTING.value

    companion object {
        /** How long the slot says what the blow came to. */
        val REPORTING = Ticks(18)

        /** And how long it is refused after it has stopped saying. */
        val AFTER_THE_REPORT = Ticks(36)

        /** How long a hand is out of use altogether. */
        val AFTER_A_SWING = Ticks(REPORTING.value + AFTER_THE_REPORT.value)

        /**
         * And how long after reading something aloud out of it, which is the
         * shorter of the two: long enough that a wand cannot be spent as fast
         * as it can be clicked, and short enough not to feel like a swing.
         */
        val AFTER_CASTING = Ticks(18)

        /** How often the wait is counted down, which is what the clock does. */
        val STEP = Ticks(3)
    }
}
