package pl.pelotasplus.eyeofbeholder.data.model

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
