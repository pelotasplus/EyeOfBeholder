package pl.pelotasplus.eyeofbeholder.data.model

data class Maz(
    val name: String,
    val width: Int,
    val height: Int,
    val squares: List<Square>
) {
    operator fun get(x: Int, y: Int): Square = squares[y * width + x]

    data class Square(
        val north: Int,
        val east: Int,
        val south: Int,
        val west: Int,
        val x: Int,
        val y: Int,
    ) {
        val blockedAllSides: Boolean
            get() = (north in listOf(1, 2)) && (east in listOf(1, 2)) && (south in listOf(1, 2)) && (west in listOf(1, 2))
    }
}

fun Int.isBlocked(): Boolean = this in listOf(1, 2)

