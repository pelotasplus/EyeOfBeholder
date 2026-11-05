package pl.pelotasplus.eyeofbeholder.agentserver

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/**
 * The map the party draw as they go, the way a player keeps one on squared
 * paper: every square seen, how each of its sides looked, and what was lying
 * on it when last seen. It is made from what `observe` told and from nothing
 * else, so keeping it is fair play.
 *
 * Kept on disk, so a run picked up in another session still has it.
 */
class TheMap(private val file: File) {

    /**
     * One floor. A side is keyed by the square it is the north or west side
     * of, so the wall between two squares is one entry whichever of them it
     * was seen from; an empty string is a side seen to be open.
     */
    @Serializable
    data class Floor(
        val sides: MutableMap<String, String> = mutableMapOf(),
        val seen: MutableSet<String> = mutableSetOf(),
        val items: MutableMap<String, List<String>> = mutableMapOf(),
    )

    @Serializable
    private data class Kept(val floors: MutableMap<Int, Floor> = mutableMapOf())

    private val kept: Kept = file.takeIf { it.exists() }
        ?.let { runCatching { Games.json.decodeFromString(Kept.serializer(), it.readText()) }.getOrNull() }
        ?: Kept()

    private var lastAt: Square? = null
    private var lastFacing: Facing? = null
    private var lastLevel: Int? = null

    val currentLevel: Int? get() = lastLevel

    fun floor(level: Int): Floor? = kept.floors[level]

    /** What one observation adds. */
    fun saw(observation: JsonObject) {
        val level = observation["level"]?.jsonPrimitive?.intOrNull ?: return
        val at = observation["at"]?.jsonObject ?: return
        val party = Square(at["x"]!!.jsonPrimitive.int, at["y"]!!.jsonPrimitive.int)
        val facing = observation["facing"]?.jsonPrimitive?.content?.let { Facing.valueOf(it) } ?: return
        val inSight = observation["inSight"] as? JsonArray ?: return

        lastAt = party
        lastFacing = facing
        lastLevel = level

        val floor = kept.floors.getOrPut(level) { Floor() }
        val straightOn = inSight.map { it.jsonObject }.filter { it["across"]?.jsonPrimitive?.int == 0 }
        val lastAhead = straightOn.maxOfOrNull { it["ahead"]!!.jsonPrimitive.int } ?: 0

        for (entry in inSight.map { it.jsonObject }) {
            val ahead = entry["ahead"]!!.jsonPrimitive.int
            val across = entry["across"]!!.jsonPrimitive.int
            val square = party.seenAt(facing, ahead, across)
            val items = (entry["items"] as? JsonArray)?.map { it.jsonObject["name"]!!.jsonPrimitive.content }.orEmpty()

            if (across != 0) {
                if (items.isNotEmpty()) floor.items[square.key] = items
                continue
            }

            fun face(name: String) = entry[name]?.jsonPrimitive?.content ?: ""

            // The square a line of sight stops at is not seen at all: only the
            // wall in front of it is, and that is all the entry says — which
            // can happen three ahead as well as nearer.
            val stoppedAt = ahead == lastAhead && ahead > 0 && entry["near"] != null &&
                entry.keys.none { it in SEEN_ONLY_FROM_INSIDE }
            if (ahead > 0) floor.side(square, facing.back, face("near"))
            if (stoppedAt) continue

            floor.seen += square.key
            floor.side(square, facing.left, face("left"))
            floor.side(square, facing.right, face("right"))
            if (ahead == SIGHT) floor.side(square, facing, face("far"))
            if (items.isEmpty()) floor.items.remove(square.key) else floor.items[square.key] = items
        }

        file.parentFile?.mkdirs()
        file.writeText(Games.json.encodeToString(Kept.serializer(), kept))
    }

    /**
     * The floor drawn in text, north up: `@` with an arrow is the party, `i`
     * a square with something on it, `.` a square seen; `-` and `|` are
     * walls, `D` a door, `*` a wall with something on it — a button, a niche,
     * a picture — which is worth a closer look.
     */
    fun drawn(level: Int): String {
        val floor = kept.floors[level] ?: return "Nothing of level $level has been seen yet."
        val squares = floor.seen.map(Square::of) + floor.sides.keys.map { Square.of(it.substringBeforeLast(',')) }
        if (squares.isEmpty()) return "Nothing of level $level has been seen yet."

        val left = squares.minOf { it.x }
        val right = squares.maxOf { it.x }
        val top = squares.minOf { it.y }
        val bottom = squares.maxOf { it.y }

        val here = lastAt.takeIf { lastLevel == level }
        val lines = mutableListOf<String>()
        lines += "level $level, x $left..$right across, y $top..$bottom down, north up"

        for (y in top..bottom + 1) {
            val northSides = StringBuilder("    +")
            for (x in left..right) {
                northSides.append(mark(floor.sides["$x,$y,N"], across = true)).append('+')
            }
            lines += northSides.toString()
            if (y > bottom) break

            val row = StringBuilder(y.toString().padStart(3)).append(' ')
            for (x in left..right + 1) {
                row.append(mark(floor.sides["$x,$y,W"], across = false))
                if (x > right) break
                val key = "$x,$y"
                row.append(
                    when {
                        here == Square(x, y) -> lastFacing!!.arrow
                        floor.items.containsKey(key) -> 'i'
                        key in floor.seen -> '.'
                        else -> ' '
                    },
                )
            }
            lines += row.toString()
        }

        if (floor.items.isNotEmpty()) {
            lines += "lying about: " + floor.items.entries.sortedBy { it.key }.joinToString("; ") { (at, what) -> "($at) ${what.joinToString(", ")}" }
        }
        val worthALook = floor.sides.filterValues { it.isNotEmpty() && it != WALL }
        if (worthALook.isNotEmpty()) {
            lines += "on the walls: " + worthALook.entries.sortedBy { it.key }.joinToString("; ") { (side, what) -> "($side) $what" }
        }
        return lines.joinToString("\n")
    }

    private fun Floor.side(of: Square, side: Facing, looked: String) {
        val (square, key) = when (side) {
            Facing.NORTH -> of to "N"
            Facing.WEST -> of to "W"
            Facing.SOUTH -> Square(of.x, of.y + 1) to "N"
            Facing.EAST -> Square(of.x + 1, of.y) to "W"
        }
        sides["${square.key},$key"] = looked
    }

    private fun mark(looked: String?, across: Boolean): Char = when {
        looked == null || looked.isEmpty() -> ' '
        looked.contains("door") -> 'D'
        looked == WALL -> if (across) '-' else '|'
        else -> '*'
    }

    data class Square(val x: Int, val y: Int) {
        val key: String get() = "$x,$y"

        /** The square [ahead] in front of one standing here facing [facing], [across] to its right. */
        fun seenAt(facing: Facing, ahead: Int, across: Int) = Square(
            x + ahead * facing.dx + across * facing.right.dx,
            y + ahead * facing.dy + across * facing.right.dy,
        )

        companion object {
            fun of(key: String) = key.split(',').let { Square(it[0].toInt(), it[1].toInt()) }
        }
    }

    /** North is up and y grows southwards, as the maze has it. */
    enum class Facing(val dx: Int, val dy: Int, val arrow: Char) {
        NORTH(0, -1, '^'), EAST(1, 0, '>'), SOUTH(0, 1, 'v'), WEST(-1, 0, '<');

        val right: Facing get() = entries[(ordinal + 1) % 4]
        val left: Facing get() = entries[(ordinal + 3) % 4]
        val back: Facing get() = entries[(ordinal + 2) % 4]
    }

    private companion object {
        /** As far ahead as `observe` tells, which is as far as the view draws. */
        const val SIGHT = 3
        const val WALL = "wall"

        /** What an entry says only about a square the party can see into. */
        val SEEN_ONLY_FROM_INSIDE = setOf("left", "right", "far", "floor", "monsters", "items")
    }
}
