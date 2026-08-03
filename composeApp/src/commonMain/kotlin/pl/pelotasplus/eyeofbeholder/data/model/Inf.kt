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
     * a wall is. [sight] must be in [viewSlots] order, since which row a wall
     * stands in is what decides between two sublevels.
     */
    fun subLevelShowing(showing: Int, sight: List<Maz.WallType>): Int {
        val stuck = subLevels.map { it.cannotDraw(sight) }
        val least = stuck.minWith(NEAREST_ROW_FIRST)

        return if (NEAREST_ROW_FIRST.compare(stuck[showing], least) == 0) {
            showing
        } else {
            stuck.indexOfFirst { NEAREST_ROW_FIRST.compare(it, least) == 0 }
        }
    }
}

/**
 * The nearest row that tells two sublevels apart decides between them; rows
 * further back are only consulted where it says nothing.
 *
 * The view is three squares deep and a level's sublevels sit next to each
 * other, so it habitually shows walls of more than one of them — a party
 * looking towards the boundary can have most of what they see belong to rooms
 * they are not in. What settles where they stand is what stands beside them,
 * however much of the distance disagrees.
 */
private val NEAREST_ROW_FIRST = Comparator<List<Int>> { a, b ->
    a.indices.firstNotNullOfOrNull { row -> (a[row] - b[row]).takeIf { it != 0 } } ?: 0
}

/** What this sublevel cannot draw of [sight], counted by row, nearest first. */
private fun SubLevel.cannotDraw(sight: List<Maz.WallType>): List<Int> {
    val rows = MutableList(ROWS_DEEP + 1) { 0 }

    sight.forEachIndexed { slot, wall ->
        if (hasNoAppearanceFor(wall)) rows[-viewSlots[slot].relativeY]++
    }
    return rows
}

private fun SubLevel.hasNoAppearanceFor(wall: Maz.WallType): Boolean = when (wall) {
    is Maz.WallType.Decoration ->
        decorations.none { it.decorationWallIndex == wall.decorationWallIndex }

    is Maz.WallType.Door -> wall.doorIndex.value !in doors.indices

    else -> false
}

private const val ROWS_DEEP = 3
