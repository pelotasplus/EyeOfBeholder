package pl.pelotasplus.eyeofbeholder.data.model

/**
 * Reference to a monster sprite sheet (CPS file), from the INF sublevel
 * header (17-byte record: 0xEC marker, sizeClass, slot, 13-char name, dcr flag).
 *
 * Each sublevel can define up to 2 monster graphic sets. The [name] is the
 * base filename (e.g. "SKELWAR") which maps to a CPS file (SKELWAR.CPS)
 * containing the monster's animation frames.
 *
 * @property name Base filename without extension (e.g. "guard1")
 * @property sizeClass Sheet layout selector (0-2), see [monsterFrameRects]
 * @property slot Which of the level's two sheet slots this occupies (0 or 1)
 * @property hasDecorations Whether a matching .DCR overlay file exists
 */
data class MonsterGfx(
    val name: String,
    val sizeClass: Int = 0,
    val slot: Int = 0,
    val hasDecorations: Boolean = false,
)
