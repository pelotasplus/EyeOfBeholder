package pl.pelotasplus.eyeofbeholder.data.model

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
 * 3-7     = DoorTypeOneWithButton (state 0-4: closed/opening/open/closing/stuck)
 * 8-12    = DoorTypeOneWithoutButton (state 0-4)
 * 13-17   = DoorTypeTwoWithButton (state 0-4, uses second door CPS graphic)
 * 18-22   = DoorTypeTwoWithoutButton (state 0-4)
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
        data class DoorTypeOneWithButton(val state: Int) : WallType()
        data class DoorTypeOneWithoutButton(val state: Int) : WallType()
        data class DoorTypeTwoWithButton(val state: Int) : WallType()
        data class DoorTypeTwoWithoutButton(val state: Int) : WallType()
        data object StairUp : WallType()
        data object StairDown : WallType()
        data class Decoration(val decorationWallIndex: Int) : WallType()

        companion object {
            fun fromInt(value: Int): WallType = when (value) {
                0 -> NoWall
                1, 2 -> FixedWall(wallType = value - 1)
                in 3..7 -> DoorTypeOneWithButton(state = value - 3)
                in 8..12 -> DoorTypeOneWithoutButton(state = value - 8)
                in 13..17 -> DoorTypeTwoWithButton(state = value - 13)
                in 18..22 -> DoorTypeTwoWithoutButton(state = value - 18)
                23 -> StairUp
                24 -> StairDown
                else -> Decoration(value)
            }
        }
    }
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
