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
        data object DoorPoleType1 : WallType()
        data object BottomPit : WallType()
        data object DoorPoleType2 : WallType()
        data object PidgeonHole : WallType()
        data object StuckDoorType1 : WallType()
        data object StuckDoorType2 : WallType()
        data object Teleport : WallType()
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
                25 -> DoorPoleType1
                27 -> BottomPit
                28 -> DoorPoleType2
                30 -> PidgeonHole
                31 -> StuckDoorType1
                32 -> StuckDoorType2
                45 -> Teleport
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

/**
 * Categorize a wall type for filtering purposes
 */
fun Maz.WallType.category(): WallTypeCategory {
    return when (this) {
        is Maz.WallType.FixedWall -> WallTypeCategory.FIXED_WALL
        is Maz.WallType.DoorTypeOneWithButton,
        is Maz.WallType.DoorTypeOneWithoutButton,
        is Maz.WallType.DoorTypeTwoWithButton,
        is Maz.WallType.DoorTypeTwoWithoutButton,
        is Maz.WallType.DoorPoleType1,
        is Maz.WallType.DoorPoleType2,
        is Maz.WallType.StuckDoorType1,
        is Maz.WallType.StuckDoorType2 -> WallTypeCategory.DOOR
        is Maz.WallType.StairUp,
        is Maz.WallType.StairDown -> WallTypeCategory.STAIRS
        else -> WallTypeCategory.SPECIAL
    }
}

///**
// * Extract the visual wall type index for rendering.
// * For FixedWall, returns the wallType.
// * For other types, this needs to be extended based on VMP structure.
// */
//fun Maz.WallType.toRenderWallType(): Int? {
//    return when (this) {
//        is Maz.WallType.FixedWall -> wallType
//        is Maz.WallType.NoWall -> null
//        is Maz.WallType.Decoration ->
//        // TODO: Add mappings for doors, stairs, etc.
//        // These will need to map to appropriate VMP wall type indices
//        else -> null
//    }
//}
