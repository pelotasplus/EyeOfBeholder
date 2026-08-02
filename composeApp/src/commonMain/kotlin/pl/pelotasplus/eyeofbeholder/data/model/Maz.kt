package pl.pelotasplus.eyeofbeholder.data.model

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
 * 3-7     = Door 1 with button (state 0-4: closed/opening/open/closing/stuck)
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
     * The square at [at], or null where there is none.
     *
     * The view cone reaches three squares ahead and three to the side, so
     * standing near an edge of the maze asks about squares that are off it.
     */
    fun squareOrNull(at: Location): Square? =
        if (at.x in 0 until width && at.y in 0 until height) this[at.x, at.y] else null

    data class Square(
        val x: Int,
        val y: Int,

        val north: WallType,
        val east: WallType,
        val south: WallType,
        val west: WallType,
    ) {
        val blockedAllSides: Boolean
            get() = north is WallType.FixedWall && east is WallType.FixedWall
                    && south is WallType.FixedWall && west is WallType.FixedWall
    }

    sealed class WallType {
        data object NoWall : WallType()
        data class FixedWall(val wallType: Int) : WallType()

        /**
         * @property doorIndex Which door definition to use (0 or 1 → [SubLevel.doors])
         * @property hasButton Whether the door has a clickable open/close button
         * @property state 0-4: closed/opening/open/closing/stuck
         */
        data class Door(
            val doorIndex: DoorIndex,
            val hasButton: Boolean,
            val state: Int,
        ) : WallType()

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
