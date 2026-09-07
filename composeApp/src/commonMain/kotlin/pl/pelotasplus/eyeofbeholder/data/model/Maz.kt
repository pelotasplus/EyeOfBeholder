package pl.pelotasplus.eyeofbeholder.data.model

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

/**
 * Represents a parsed .MAZ file — the dungeon floor layout for a sublevel.
 *
 * The maze is a fixed 32x32 grid of [Square]s. Each square has four walls
 * (north, east, south, west) whose type determines what the player sees
 * and can interact with: solid stone, open passage, doors, stairs, or
 * decorations like levers, alcoves, and paintings.
 *
 * ## Binary format (.MAZ)
 * - Header: width (u16), height (u16), bytesPerSquare (u16) — always 32, 32, 4
 * - Body: 1024 squares in row-major order (y outer, x inner)
 * - Each square: 4 bytes → north, east, south, west wall type
 *
 * ## Coordinate system
 * - (0,0) is the top-left corner of the maze
 * - X increases to the east, Y increases to the south
 * - Squares are stored and accessed in row-major order: index = y * width + x
 *
 * ## Wall type encoding (single byte per wall)
 * ```
 * 0       = NoWall (open passage, party can walk through)
 * 1-2     = FixedWall (solid wall, wallType 0 or 1 → indexes into VMP wall tile sets)
 * 3-7     = Door 1 with button (state 0-4: shut, then four steps open)
 * 8-12    = Door 1 without button (state 0-4)
 * 13-17   = Door 2 with button (state 0-4, uses second door CPS graphic)
 * 18-22   = Door 2 without button (state 0-4)
 * 23      = StairUp
 * 24      = StairDown
 * 25+     = Decoration (the byte value IS the wallIndex, used to look up
 *           decoration definitions from the sublevel's decoration list)
 * ```
 */
data class Maz(
    val name: String,
    val width: Int,
    val height: Int,
    val squares: List<Square>
) {
    operator fun get(x: Int, y: Int): Square {
        val ret = squares[y * width + x]
        check(ret.x == x && ret.y == y) {
            "Expected ($x, $y) but got (${ret.x}, ${ret.y})"
        }
        return ret
    }

    /**
     * The square at [at], counting round the ends.
     *
     * The view cone reaches three squares ahead and three to either side, so a
     * party near an edge ask about squares beyond it, and there is no beyond:
     * a maze is one run of squares that the game walks by index, so a step off
     * the top comes out at the bottom of the same column and a step off a side
     * lands at the far end of the next row. The border rows are solid rock in
     * every level, so what the party actually see there is a wall rather than
     * the far side of the world.
     */
    fun square(at: Location): Square {
        val square = ((at.y * width + at.x) % squares.size + squares.size) % squares.size
        return this[square % width, square / width]
    }

    data class Square(
        val x: Int,
        val y: Int,

        val north: WallType,
        val east: WallType,
        val south: WallType,
        val west: WallType,
    )

    sealed class WallType {
        data object NoWall : WallType()
        data class FixedWall(val wallType: Int) : WallType()

        /**
         * @property doorIndex Which door definition to use (0 or 1 → [SubLevel.doors])
         * @property hasButton Whether the door has a clickable open/close
         *   button. It says more than where to draw one: a door with a button
         *   is a door that can be worked at all, so it is also the only kind a
         *   monster opens. One with none on either face belongs to whatever
         *   plate or script drives it and opens for nothing else
         * @property state How far the door has slid out of its frame: 0 shut,
         *   4 open, the three between them the steps it opens through. A door
         *   jammed in its frame is not one of these — that is a decoration with
         *   a special type of its own.
         */
        data class Door(
            val doorIndex: DoorIndex,
            val hasButton: Boolean,
            val state: Int,
        ) : WallType() {
            /** Only a door all the way out of its frame lets anything past. */
            val isOpen: Boolean get() = state == FULLY_OPEN

            /**
             * All the way down, as against merely not open: a door halfway
             * through its travel is neither, and can still be sent either way.
             */
            val isShut: Boolean get() = state == SHUT

            /**
             * The same door a step further along, which is how a door moves:
             * it slides rather than jumps, and stops at either end of its
             * travel however often it is pushed.
             */
            fun stepped(opening: Boolean) =
                copy(state = (state + if (opening) 1 else -1).coerceIn(SHUT, FULLY_OPEN))

            companion object {
                /** How many steps a door takes to slide from shut to open. */
                const val TRAVEL = 4

                private const val FULLY_OPEN = TRAVEL
                private const val SHUT = 0
            }
        }

        data object StairUp : WallType()
        data object StairDown : WallType()
        data class Decoration(val decorationWallIndex: Int) : WallType()

        companion object {
            fun of(byte: WallByte): WallType = fromInt(byte.value)

            fun fromInt(value: Int): WallType = when (value) {
                0 -> NoWall
                1, 2 -> FixedWall(wallType = value - 1)
                in 3..7 -> Door(doorIndex = DoorIndex(0), hasButton = true, state = value - 3)
                in 8..12 -> Door(doorIndex = DoorIndex(0), hasButton = false, state = value - 8)
                in 13..17 -> Door(doorIndex = DoorIndex(1), hasButton = true, state = value - 13)
                in 18..22 -> Door(doorIndex = DoorIndex(1), hasButton = false, state = value - 18)
                23 -> StairUp
                24 -> StairDown
                else -> Decoration(value)
            }
        }
    }
}

/**
 * A wall as the maze stores it, and as a script writes and compares it.
 *
 * Its own space: it is not a decoration id, a door slot or a wall-set index,
 * though several of those are packed into its range. [Maz.WallType.fromInt] is
 * the only thing that says which.
 */
@JvmInline
@Serializable
value class WallByte(val value: Int)

/**
 * The byte a wall is stored as, which is the inverse of [Maz.WallType.fromInt].
 *
 * Scripts deal in these rather than in types: they compare a wall against a
 * number and set it to one, so a wall read back out of the world has to be the
 * number again.
 */
fun Maz.WallType.asByte(): WallByte = WallByte(toInt())

private fun Maz.WallType.toInt(): Int = when (this) {
    Maz.WallType.NoWall -> 0
    is Maz.WallType.FixedWall -> wallType + 1
    is Maz.WallType.Door -> when {
        doorIndex.value == 0 && hasButton -> 3
        doorIndex.value == 0 -> 8
        hasButton -> 13
        else -> 18
    } + state

    Maz.WallType.StairUp -> 23
    Maz.WallType.StairDown -> 24
    is Maz.WallType.Decoration -> decorationWallIndex
}

/**
 * Get the wall on the specified side of this square
 */
fun Maz.Square.getWall(side: WallSide): Maz.WallType {
    return when (side) {
        WallSide.NORTH -> north
        WallSide.EAST -> east
        WallSide.SOUTH -> south
        WallSide.WEST -> west
    }
}
