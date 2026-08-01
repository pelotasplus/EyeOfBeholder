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
)
