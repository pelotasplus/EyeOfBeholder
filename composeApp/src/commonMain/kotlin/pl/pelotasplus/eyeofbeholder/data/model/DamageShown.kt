package pl.pelotasplus.eyeofbeholder.data.model

import kotlinx.serialization.Serializable

/**
 * A blow a champion has just taken, still showing on their portrait.
 *
 * It is the only thing that says a champion was hit at all: the hit points
 * move, but a bar creeping down is not something a player notices in a fight.
 * A splat with a number on it is.
 *
 * @property ticksLeft How much longer it stays up. The original hangs it on
 *   the same countdown a weapon hand reports on, and for the same length.
 */
@Serializable
data class DamageShown(
    val whose: PartySlot,
    val amount: Int,
    val ticksLeft: Int,
) {
    companion object {
        /** How long a splat stays on a portrait. */
        val WHILE_IT_SHOWS = Ticks(18)

        /** How often the countdown is looked at. */
        val STEP = HandRecovering.STEP
    }
}
