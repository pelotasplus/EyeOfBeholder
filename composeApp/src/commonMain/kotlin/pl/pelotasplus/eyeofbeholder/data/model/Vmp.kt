package pl.pelotasplus.eyeofbeholder.data.model

data class Vmp(
    val name: String,
    val tileIndexes: List<TileIndex>,
) {
    val backdrop: List<TileIndex>
        get() = tileIndexes.subList(0, WALL_TYPE_SIZE)

    val wallTypesCount: Int
        get() = (tileIndexes.size - BACKDROP_SIZE) / WALL_TYPE_SIZE

    fun getWallType(index: Int): List<TileIndex> {
        return tileIndexes.subList(BACKDROP_SIZE + index, BACKDROP_SIZE + (index + 1) * WALL_TYPE_SIZE)
    }

    companion object {
        const val BACKDROP_SIZE = 330
        const val WALL_TYPE_SIZE = 431
    }
}
