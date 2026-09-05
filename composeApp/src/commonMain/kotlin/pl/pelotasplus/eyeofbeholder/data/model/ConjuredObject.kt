package pl.pelotasplus.eyeofbeholder.data.model

/**
 * The fourteen numbered things that can be conjured into the air.
 *
 * One table, read from both ends: a spell a champion casts names a row of it,
 * and so does a script when a trap in the wall looses something. That is why a
 * trap's fireball and a champion's are the same fireball — the same row, the
 * same throw against it, the same half to whoever makes theirs. Only how hard
 * it lands differs, and that is the floor's doing rather than the row's.
 *
 * Not [ConjuredBolt], which is how one of these is drawn. This is which one it
 * is.
 *
 * The numbering is the game's and is transcribed. A script naming a number
 * outside it looses nothing anybody has written down.
 */
enum class ConjuredObject(val asWritten: Int, val casts: Spell) {
    MAGIC_MISSILE(0, Spell.MAGIC_MISSILE),
    MELFS_ACID_ARROW(1, Spell.MELFS_ACID_ARROW),
    FIREBALL(2, Spell.FIREBALL),
    HOLD_PERSON(3, Spell.HOLD_PERSON),
    LIGHTNING_BOLT(4, Spell.LIGHTNING_BOLT),
    ICE_STORM(5, Spell.ICE_STORM),
    HOLD_MONSTER(6, Spell.HOLD_MONSTER),
    FLAME_STRIKE(7, Spell.FLAME_STRIKE),

    // And the six nobody learns, which monsters throw.
    A_MONSTERS_FIREBALL(8, Spell.A_MONSTERS_FIREBALL),
    A_LESSER_FIREBALL(9, Spell.A_LESSER_FIREBALL),
    A_DEATH_SPELL(10, Spell.A_DEATH_SPELL),
    A_DISINTEGRATION(11, Spell.A_DISINTEGRATION),
    WOUNDS_AT_A_DISTANCE(12, Spell.WOUNDS_CAUSED_AT_A_DISTANCE),
    STONE_AT_A_DISTANCE(13, Spell.STONE_AT_A_DISTANCE);

    companion object {
        /** Which of them that number is, or none for a number nothing uses. */
        fun of(asWritten: Int): ConjuredObject? = entries.firstOrNull { it.asWritten == asWritten }
    }
}
