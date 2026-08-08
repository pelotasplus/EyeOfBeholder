package pl.pelotasplus.eyeofbeholder.data.model

import kotlinx.serialization.Serializable

/**
 * A hand that has swung and has not come back to rest, and how much longer it
 * has to go.
 *
 * A weapon is not swung as fast as the mouse can be clicked. The original puts
 * the hand out of use for a while afterwards and draws the same grid over it
 * that it draws over something a champion may not use at all — a spellbook in
 * a fighter's hand — so a hand recovering and a hand holding the wrong thing
 * look alike and are refused alike.
 */
@Serializable
data class HandRecovering(
    val whose: PartySlot,
    val hand: CarrySlot,
    val ticksLeft: Int,
) {
    companion object {
        /**
         * How long a hand is out of use after a swing.
         *
         * The original counts it in two parts: for the first 18 the slot shows
         * what the blow came to instead of the weapon in it, and for the 36
         * after that it shows the weapon again and is still refused. Nothing
         * writes the number yet, so the two are one wait here.
         */
        val AFTER_A_SWING = Ticks(18 + 36)

        /** How often the wait is counted down, which is what the clock does. */
        val STEP = Ticks(3)
    }
}
