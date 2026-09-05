package pl.pelotasplus.eyeofbeholder.data.model

/**
 * A kind of harm a spell does, and so what can shrug it off.
 *
 * A spell says which kinds it is, and a creature says which kinds do nothing
 * to it — but the two say it in differently numbered words, so cold is one bit
 * where a spell names it and another where a creature turns it aside. Both
 * numbers are transcribed and neither follows from the other.
 *
 * These are the kinds this game's spells actually carry. The creature word has
 * two more bits with no spell behind them, and they are left out rather than
 * guessed at.
 */
enum class HarmKind(
    /** The bit a spell sets to say it is this kind. */
    val asASpellSaysIt: Int,

    /**
     * And the bit a creature sets to say this kind does nothing to it, where
     * anything may be immune. Nothing is immune to acid.
     */
    val asACreatureTurnsItAside: Int? = null,
) {
    /** Which every spell is, on top of whatever else it is. */
    MAGIC(asASpellSaysIt = 0x01, asACreatureTurnsItAside = 0x10),

    ELECTRICITY(asASpellSaysIt = 0x02, asACreatureTurnsItAside = 0x20),

    /** Melf's arrow alone, and nothing in the dungeon shrugs it off. */
    ACID(asASpellSaysIt = 0x10),

    FIRE(asASpellSaysIt = 0x20, asACreatureTurnsItAside = 0x800),

    COLD(asASpellSaysIt = 0x40, asACreatureTurnsItAside = 0x80),
}
