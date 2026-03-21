package pl.pelotasplus.eyeofbeholder.data.model

/**
 * Reference to a monster sprite sheet (CPS file).
 *
 * Each sublevel can define up to 2 monster graphic sets. The [name] is the
 * base filename (e.g. "SKELWAR") which maps to a CPS file (SKELWAR.CPS)
 * containing the monster's animation frames.
 */
data class MonsterGfx(
    val name: String,
)
