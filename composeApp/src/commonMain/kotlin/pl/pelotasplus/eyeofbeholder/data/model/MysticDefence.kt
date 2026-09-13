package pl.pelotasplus.eyeofbeholder.data.model

/**
 * The party shielded against the fire of the last floor's dragon.
 *
 * Two things that do not end together. The shield goes the first time that
 * fire reaches the party, taking most of that one blast. The spell runs on
 * regardless of it, and its end is said aloud whether the shield was used or
 * not — and while it runs, the shield can be put up again once used.
 */
data class MysticDefence(
    val castBy: PartySlot,
    val ticksLeft: Int = LASTS.value,
    val spent: Boolean = false,
) {
    val shields: Boolean get() = !spent

    companion object {
        val LASTS = Ticks(546)

        /** What a dragon's fire does to a party it is up for, rather than twelve dice. */
        val A_DRAGONS_FIREBALL_TURNED = DamageDice(times = 4, pips = 10, base = 6)
    }
}
