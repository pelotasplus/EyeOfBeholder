package pl.pelotasplus.eyeofbeholder.data.model

data class Maz(
    val name: String,
    val width: Int,
    val height: Int,
    val squares: List<Square>
) {
    operator fun get(x: Int, y: Int): Square = squares[y * width + x]

    data class Square(
        val north: WallType,
        val east: WallType,
        val south: WallType,
        val west: WallType,
        val x: Int,
        val y: Int,
    ) {
        val blockedAllSides: Boolean
            get() = north is WallType.FixedWall && east is WallType.FixedWall
                    && south is WallType.FixedWall && west is WallType.FixedWall
    }

    sealed class WallType {
        data object NoWall : WallType()
        data object FixedWall : WallType()
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
        data class Unknown(val value: Int) : WallType()

        companion object {
            fun fromInt(value: Int): WallType = when (value) {
                0 -> NoWall
                1, 2 -> FixedWall
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
                else -> Unknown(value)
            }
        }
    }
}
