package pl.pelotasplus.eyeofbeholder.data.model

import pl.pelotasplus.eyeofbeholder.data.model.script.Script

/**
 * Top-level container for a complete game level, parsed from a .INF file.
 *
 * Each level in Eye of the Beholder corresponds to one .INF file (e.g. LEVEL1.INF).
 * The INF file is LCW-compressed and contains three sequential blocks:
 *
 * ## Block A — SubLevels
 * One or more [SubLevel]s, each with its own maze, tileset, palette, doors,
 * monsters, decorations, and script timers. The first sublevel is the main floor;
 * additional sublevels represent side areas (e.g. a separate room with different
 * wall graphics). Each sublevel references external files (MAZ, VMP, VCN, PAL,
 * DEC, CPS) that are loaded during parsing.
 *
 * ## Block B — Scripts & Messages
 * - Monster instance data (up to 30 active monsters on the level)
 * - The level [script] — a bytecode program of 29 opcodes that drives all
 *   interactive behavior: opening doors, teleporting, spawning monsters, etc.
 * - String [messages] referenced by script Message tokens (displayed in the
 *   game's message area)
 *
 * ## Block C — Trigger Map
 * Maps maze locations + flags to script entry points. When the party steps on
 * a square (or interacts with a wall), the engine looks up the trigger and
 * jumps to the corresponding script offset.
 *
 * @property name Original filename (e.g. "LEVEL1.INF")
 * @property subLevels All sublevels (floors/areas) within this level
 * @property script Complete list of parsed script instructions for this level
 * @property messages Text strings referenced by script Message tokens
 * @property items All game items that belong to this level (filtered from global ITEM.DAT)
 * @property monsterInstances Live monsters placed on this level (up to 30)
 */
data class Inf(
    val name: String,
    val subLevels: List<SubLevel>,
    val script: List<Script>,
    val messages: List<String>,
    val items: List<Item>,
    val monsterInstances: List<MonsterInstance> = emptyList(),
    val triggers: List<Trigger> = emptyList(),
) {
    /** null where the script named no message, or named one this level lacks. */
    fun message(id: MessageId): String? =
        messages.getOrNull(id.index)?.takeIf { it.isNotBlank() }

    /**
     * Which sublevel the walls in [sight] belong to, [showing] being the one
     * being drawn now.
     *
     * A level's sublevels share one maze and are told apart only by which wall
     * indices each of them maps, so the same byte is a bookcase in one and a
     * tree in the next, and a byte a sublevel has never heard of has no
     * appearance in it at all. Nothing in the data marks where one sublevel's
     * rooms end and another's begin: the game is told which it is in, by the
     * script that sends the party across, and never works it out.
     *
     * This is for arriving somewhere no script sent us, which walking through
     * a wall is. [showing] is kept unless something in sight cannot be drawn
     * in it, and kept anyway when no sublevel can draw it — that is a wall
     * nobody maps rather than a party in the wrong place.
     */
    fun subLevelShowing(showing: Int, sight: List<Maz.WallType>): Int {
        val wanted = sight
            .filterIsInstance<Maz.WallType.Decoration>()
            .mapTo(mutableSetOf()) { it.decorationWallIndex }

        if (wanted.isEmpty() || subLevels[showing].maps(wanted)) return showing

        return subLevels.indices.firstOrNull { subLevels[it].maps(wanted) } ?: showing
    }
}

private fun SubLevel.maps(wallIndices: Set<Int>): Boolean =
    decorations.mapTo(mutableSetOf()) { it.decorationWallIndex }.containsAll(wallIndices)
