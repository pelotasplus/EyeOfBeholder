package pl.pelotasplus.eyeofbeholder.data.model

data class Vmp(
    val name: String,
    val tileIndexes: List<TileIndex>,
) {
    data class TileIndex(
        val zMask: Boolean,
        val mirrorX: Boolean,
        val tileIndex: Int,
    )

    val backdrop: List<TileIndex>
        get() = tileIndexes.subList(0, BACKDROP_SIZE)

    fun getWallType(index: Int): List<TileIndex> {
        return tileIndexes.subList(
            BACKDROP_SIZE + (index * WALL_TYPE_SIZE),
            BACKDROP_SIZE + (index + 1) * WALL_TYPE_SIZE
        )
    }

    override fun toString(): String {
        return "Vmp(name='$name', tileIndexes=${tileIndexes.size})"
    }

    companion object {
        const val BACKDROP_SIZE = 330
        const val WALL_TYPE_SIZE = 431
    }
}
