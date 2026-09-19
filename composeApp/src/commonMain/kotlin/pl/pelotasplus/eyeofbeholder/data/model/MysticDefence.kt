package pl.pelotasplus.eyeofbeholder.data.model

/**
 * The party shielded against the fire of the last floor's dragon.
 *
 * Two things that do not end together. The shield goes the first time that
 * fire reaches the party, taking most of that one blast; the spell runs on
 * regardless of it, and its end is said aloud whether the shield was used or
 * not — so while it runs, the shield can be put up again once used. What is
 * left of the spell is kept with every other running one, in [SpellsRunning].
 */
object MysticDefence {

    /** What a dragon's fire does to a party it is up for, rather than twelve dice. */
    val A_DRAGONS_FIREBALL_TURNED = DamageDice(times = 4, pips = 10, base = 6)
}
