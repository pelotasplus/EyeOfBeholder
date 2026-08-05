package pl.pelotasplus.eyeofbeholder.data.model

/**
 * The band of the view that one square's contents may be drawn in, in the
 * viewport's 22 tile columns: [from] inclusive, [to] exclusive.
 *
 * Depth alone cannot say what a wall hides. A monster two rows ahead is nearer
 * to the party than the far face of the square in front of it, so by distance
 * it wins — and yet a solid square standing beside that face covers it
 * completely. The original settles this before it draws anything: every wall
 * turned towards the party takes a bite out of every square's band, and a
 * square whose band closes is skipped entirely, contents and all.
 */
data class ViewWindow(val from: Int, val to: Int) {

    val closed: Boolean get() = to <= from

    val leftPixel: Int get() = from * ViewPort.TILE_SIZE
    val rightPixel: Int get() = to * ViewPort.TILE_SIZE

    companion object {
        val WHOLE_VIEW = ViewWindow(0, ViewPort.TILES_PER_ROW)
    }
}

/** How much of what stands behind it a wall lets through. */
enum class WallSight {
    /** Nothing is drawn on this face, so nothing behind it is hidden. */
    CLEAR,

    /** A solid face, which hides whatever it covers. */
    SOLID,

    /** A frame with an opening in it — a door — leaving a gap to see through. */
    OPEN_FRAME,
}

/**
 * How the wall a square turns towards the party takes part in hiding things.
 *
 * The plain wall bytes answer for themselves; a decoration's answer is the
 * level's own, since a level decides both whether its decoration has a wall
 * behind it at all and whether that wall can be seen through.
 */
fun SubLevel.sightThrough(wall: Maz.WallType): WallSight = when (wall) {
    Maz.WallType.NoWall -> WallSight.CLEAR
    is Maz.WallType.Door -> WallSight.OPEN_FRAME
    is Maz.WallType.FixedWall, Maz.WallType.StairUp, Maz.WallType.StairDown -> WallSight.SOLID
    is Maz.WallType.Decoration -> {
        val mapped = decorations.firstOrNull { it.decorationWallIndex == wall.decorationWallIndex }
        when {
            mapped == null -> WallSight.CLEAR
            mapped.flags and SEEN_THROUGH != 0 -> WallSight.OPEN_FRAME
            mapped.wallType == NO_WALL_BEHIND -> WallSight.CLEAR
            else -> WallSight.SOLID
        }
    }
}

/**
 * Whether a square shows what lies on it, given the wall it turns towards the
 * party.
 *
 * A wall keeps its square's contents to itself unless it is marked as one that
 * does not: an open alcove carries the mark and shows what is shelved in it, a
 * shelf that locks does not and shows nothing until it is opened. A doorway
 * carries it too, which is how a party see the floor of the square beyond one.
 *
 * A face with no wall drawn on it hides nothing, having nothing to hide behind.
 */
fun SubLevel.showsWhatIsOnIt(wall: Maz.WallType): Boolean = when (wall) {
    Maz.WallType.NoWall -> true
    is Maz.WallType.Door -> true
    is Maz.WallType.FixedWall, Maz.WallType.StairUp, Maz.WallType.StairDown -> false
    is Maz.WallType.Decoration -> {
        val mapped = decorations.firstOrNull { it.decorationWallIndex == wall.decorationWallIndex }
        when {
            mapped == null -> true
            mapped.wallType == NO_WALL_BEHIND -> true
            else -> mapped.flags and SHOWS_ITS_CONTENTS != 0
        }
    }
}

/**
 * Whether the party can reach onto a square, given the wall it turns towards
 * them — which is what says where they may put a thing down.
 *
 * Seeing is not reaching, so this is not [showsWhatIsOnIt] and the two must
 * not be swapped. A doorway shows the floor beyond it and an open alcove
 * shows what is shelved in it; neither can be reached through. A door has to
 * be all the way open, and a wall with something painted on it is still a
 * wall.
 */
fun SubLevel.canBeReachedOnto(wall: Maz.WallType): Boolean = when (wall) {
    Maz.WallType.NoWall -> true
    is Maz.WallType.Door -> wall.isOpen
    is Maz.WallType.FixedWall, Maz.WallType.StairUp, Maz.WallType.StairDown -> false
    is Maz.WallType.Decoration -> {
        val mapped = decorations.firstOrNull { it.decorationWallIndex == wall.decorationWallIndex }
        mapped == null || mapped.wallType == NO_WALL_BEHIND
    }
}

/**
 * Whether the party may walk onto a square, given the wall it turns towards
 * them.
 *
 * Reaching and walking are asked separately because the original asks them
 * separately: a wall carries one mark for what the party may step through and
 * another for what may be put or thrown through it, and a level is free to
 * set either without the other. They happen to agree on every wall the game
 * ships, which is why this reads like [canBeReachedOnto] — but they are two
 * questions and answering one with the other would be luck rather than
 * correctness.
 */
fun SubLevel.canBeWalkedOnto(wall: Maz.WallType): Boolean = when (wall) {
    Maz.WallType.NoWall -> true
    is Maz.WallType.Door -> wall.isOpen
    is Maz.WallType.FixedWall, Maz.WallType.StairUp, Maz.WallType.StairDown -> false
    is Maz.WallType.Decoration -> {
        val mapped = decorations.firstOrNull { it.decorationWallIndex == wall.decorationWallIndex }
        mapped == null || mapped.wallType == NO_WALL_BEHIND
    }
}

/**
 * The mark a wall carries when what is on its square can be seen. A level
 * writes it inverted in the low bits but not in this one, so it is read
 * straight off what the file says.
 */
private const val SHOWS_ITS_CONTENTS = 0x80

/**
 * A decoration with no wall type behind it is painted straight onto whatever
 * the square already shows — a floor plate, a stain — so it hides nothing.
 */
private const val NO_WALL_BEHIND = 0

private const val SEEN_THROUGH = 8

/**
 * Where one of the squares the party can see sits relative to them, on the
 * same axes as [ViewBlock]: negative x is to the left, negative y is ahead.
 */
data class BlockOffset(val relativeX: Int, val relativeY: Int)

/**
 * The 18 squares the party can see, in the same order as [blockScreenCoords]:
 * rows of seven, five and three ahead of them, then their own row.
 */
val visibleBlocks: List<BlockOffset> =
    (-3..3).map { BlockOffset(it, -3) } +
        (-2..2).map { BlockOffset(it, -2) } +
        (-1..1).map { BlockOffset(it, -1) } +
        (-1..1).map { BlockOffset(it, 0) }

/**
 * What is left of the square at [blockIndex] once every wall in the view has
 * taken its bite, given what each of the 18 squares turns towards the party.
 *
 * Four squares are left with nothing by any wall at all: the outer two of the
 * back row and the outer two of the row in front of it. However open the way
 * to them looks, nothing standing on them is drawn — only the walls that face
 * them are. It reads like a fault and is not: those corners are slivers, and
 * the game does not draw into them. As soon as no wall in sight is solid the
 * rule lapses with every other, and they show like anywhere else.
 */
fun viewWindow(blockIndex: Int, sight: (Int) -> WallSight): ViewWindow {
    var from = 0
    var to = ViewPort.TILES_PER_ROW

    for (other in visibleBlocks.indices) {
        val row = blockIndex * visibleBlocks.size + other

        when (sight(other)) {
            WallSight.CLEAR -> continue

            WallSight.OPEN_FRAME -> {
                val left = seenPastEdges[row * 2]
                val right = seenPastEdges[row * 2 + 1]
                if (left > from) from = left
                if (right < to) to = right
            }

            WallSight.SOLID -> {
                when (val edge = solidEdges[row]) {
                    NO_BITE -> continue
                    HIDES_IT_ENTIRELY -> return ViewWindow(ViewPort.TILES_PER_ROW, 0)
                    else -> {
                        if (edge > 0 && to > edge) to = edge
                        if (edge < 0 && from < -edge) from = -edge
                    }
                }
            }
        }

        if (to < from) break
    }

    return ViewWindow(from, to)
}

/** This wall leaves this square alone. */
private const val NO_BITE = -40

/** This wall covers this square completely. */
private const val HIDES_IT_ENTIRELY = -41

// Both tables below are the original game's, one entry per (square being
// drawn, wall in the view) pair.

/**
 * Which edge a solid wall cuts a square back to: positive is a right edge,
 * negative a left one, with [NO_BITE] and [HIDES_IT_ENTIRELY] as the two
 * special answers.
 */
private val solidEdges: List<Int> = listOf(
    -41, -41, -41, -41, -41, -41, -41, -41, -41, -41, -41, -41, -41, -41, -41, -41, -40, -41,
    -40, -40, 2, -40, -40, -40, -40, -2, -41, -40, -40, -40, -41, 3, -40, -3, -40, -40,
    -40, -2, -40, 8, -40, -40, -40, -2, -41, 6, -40, -40, -6, 3, -40, -3, -40, -40,
    -40, -40, -40, -40, -40, -40, -40, -40, -6, -41, 16, -40, -3, -41, 19, -40, -40, -40,
    -40, -40, -40, -14, -40, 20, -40, -40, -40, -16, -41, 20, -40, -19, 16, -40, -40, 19,
    -40, -40, -40, -40, -20, -40, -40, -40, -40, -40, -41, 20, -40, -19, -41, -40, -40, 19,
    -41, -41, -41, -41, -41, -41, -41, -41, -41, -41, -41, -41, -41, -41, -41, -41, -40, -41,
    -41, -41, -41, -41, -41, -41, -41, -41, -41, -41, -41, -41, -41, -41, -41, -41, -40, -41,
    -40, -40, -40, -40, -40, -40, -40, -40, -40, 6, -40, -40, -6, 3, -40, -3, -40, -40,
    -40, -40, -40, -40, -40, -40, -40, -40, -6, -40, 16, -40, -3, -41, 19, -3, -40, 19,
    -40, -40, -40, -40, -40, -40, -40, -40, -40, -16, -40, -40, -40, -19, 16, -40, -40, 19,
    -41, -41, -41, -41, -41, -41, -41, -41, -41, -41, -41, -41, -41, -41, -41, -41, -40, -41,
    -40, -40, -40, -40, -40, -40, -40, -40, -40, -40, -40, -40, -40, 3, -40, -3, -40, -40,
    -40, -40, -40, -40, -40, -40, -40, -40, -40, -40, -40, -40, -3, -40, 19, -3, -40, 19,
    -40, -40, -40, -40, -40, -40, -40, -40, -40, -40, -40, -40, -40, -19, -40, -40, -40, 19,
    -40, -40, -40, -40, -40, -40, -40, -40, -40, -40, -40, -40, -40, -40, -40, -40, -40, -40,
    -40, -40, -40, -40, -40, -40, -40, -40, -40, -40, -40, -40, -40, -40, -40, -40, -40, -40,
    -40, -40, -40, -40, -40, -40, -40, -40, -40, -40, -40, -40, -40, -40, -40, -40, -40, -40,
)

/**
 * The pair of edges a wall with an opening leaves a square — a left one and a
 * right one, since what shows through a doorway is bounded on both sides.
 */
private val seenPastEdges: List<Int> = listOf(
    22, 0, 22, 0, 22, 0, 22, 0, 22, 0, 22, 0, 22, 0, 22, 0, 22, 0, 22, 0, 22, 0, 22, 0, 22, 0, 22, 0, 22, 0, 22, 0, 0, 22, 22, 0,
    0, 22, 0, 22, 0, 2, 0, 22, 0, 22, 0, 22, 0, 22, 2, 22, 0, 4, 0, 22, 0, 22, 0, 22, 22, 0, 0, 3, 0, 22, 3, 22, 0, 22, 0, 22,
    0, 22, 2, 22, 0, 22, 0, 8, 0, 22, 0, 22, 0, 22, 0, 2, 22, 0, 0, 6, 0, 22, 0, 22, 6, 22, 0, 3, 0, 22, 3, 0, 0, 22, 0, 22,
    0, 22, 0, 22, 0, 22, 0, 22, 0, 22, 0, 22, 0, 22, 0, 22, 6, 22, 8, 14, 0, 16, 0, 22, 3, 22, 6, 16, 0, 19, 0, 22, 0, 22, 0, 22,
    0, 22, 0, 22, 0, 22, 14, 22, 0, 22, 0, 20, 0, 22, 0, 22, 0, 22, 16, 22, 22, 0, 0, 20, 0, 22, 19, 22, 0, 16, 0, 22, 0, 22, 0, 19,
    0, 22, 0, 22, 0, 22, 0, 22, 20, 22, 0, 22, 0, 22, 0, 22, 0, 22, 0, 22, 18, 22, 20, 22, 0, 22, 19, 22, 22, 0, 0, 22, 0, 22, 0, 19,
    22, 0, 22, 0, 22, 0, 22, 0, 22, 0, 22, 0, 22, 0, 22, 0, 22, 0, 22, 0, 22, 0, 22, 0, 22, 0, 22, 0, 22, 0, 22, 0, 0, 22, 22, 0,
    22, 0, 22, 0, 22, 0, 22, 0, 22, 0, 22, 0, 22, 0, 22, 0, 22, 0, 22, 0, 22, 0, 22, 0, 22, 0, 22, 0, 22, 0, 22, 0, 0, 22, 22, 0,
    0, 22, 0, 22, 0, 22, 0, 22, 0, 22, 0, 22, 0, 22, 0, 22, 0, 22, 0, 6, 0, 22, 0, 22, 6, 22, 0, 3, 0, 22, 3, 22, 0, 22, 0, 22,
    0, 22, 0, 22, 0, 22, 0, 22, 0, 22, 0, 22, 0, 22, 0, 22, 6, 22, 0, 22, 0, 16, 0, 22, 3, 22, 7, 15, 0, 19, 3, 22, 0, 22, 0, 19,
    0, 22, 0, 22, 0, 22, 0, 22, 0, 22, 0, 22, 0, 22, 0, 22, 0, 22, 16, 22, 0, 22, 0, 22, 0, 22, 19, 22, 0, 16, 0, 22, 0, 22, 0, 19,
    22, 0, 22, 0, 22, 0, 22, 0, 22, 0, 22, 0, 22, 0, 22, 0, 22, 0, 22, 0, 22, 0, 22, 0, 22, 0, 22, 0, 22, 0, 22, 0, 0, 22, 22, 0,
    0, 22, 0, 22, 0, 22, 0, 22, 0, 22, 0, 22, 0, 22, 0, 22, 0, 22, 0, 22, 0, 22, 0, 22, 0, 22, 0, 3, 0, 22, 3, 22, 0, 22, 0, 22,
    0, 22, 0, 22, 0, 22, 0, 22, 0, 22, 0, 22, 0, 22, 0, 22, 0, 22, 0, 22, 0, 22, 0, 22, 3, 22, 0, 22, 0, 19, 3, 22, 0, 22, 0, 19,
    0, 22, 0, 22, 0, 22, 0, 22, 0, 22, 0, 22, 0, 22, 0, 22, 0, 22, 0, 22, 0, 22, 0, 22, 0, 22, 19, 22, 0, 22, 0, 22, 0, 22, 0, 19,
    0, 22, 0, 22, 0, 22, 0, 22, 0, 22, 0, 22, 0, 22, 0, 22, 0, 22, 0, 22, 0, 22, 0, 22, 0, 22, 0, 22, 0, 22, 0, 22, 0, 22, 0, 22,
    0, 22, 0, 22, 0, 22, 0, 22, 0, 22, 0, 22, 0, 22, 0, 22, 0, 22, 0, 22, 0, 22, 0, 22, 0, 22, 0, 22, 0, 22, 0, 22, 0, 22, 0, 22,
    0, 22, 0, 22, 0, 22, 0, 22, 0, 22, 0, 22, 0, 22, 0, 22, 0, 22, 0, 22, 0, 22, 0, 22, 0, 22, 0, 22, 0, 22, 0, 22, 0, 22, 0, 22,
)
