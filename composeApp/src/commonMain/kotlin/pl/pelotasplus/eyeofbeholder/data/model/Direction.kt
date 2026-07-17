package pl.pelotasplus.eyeofbeholder.data.model

/**
 * Cardinal directions in maze coordinates (absolute, not player-relative)
 */
enum class WallSide {
    NORTH, EAST, SOUTH, WEST
}

/**
 * Player facing direction in maze coordinates
 */
enum class Direction {
    NORTH,  // Negative Y
    EAST,   // Positive X
    SOUTH,  // Positive Y
    WEST;   // Negative X

    /**
     * Rotate coordinates based on player direction.
     * Baseline: NORTH (negative Y = forward in original implementation)
     */
    fun transformCoordinates(relX: Int, relY: Int): Pair<Int, Int> {
        return when (this) {
            NORTH -> Pair(relX, relY)           // Baseline: no transformation
            SOUTH -> Pair(-relX, -relY)         // 180° rotation
            EAST -> Pair(-relY, relX)           // 90° clockwise
            WEST -> Pair(relY, -relX)           // 90° counter-clockwise
        }
    }

    /**
     * Rotate wall side based on player direction.
     * Baseline: NORTH (no rotation)
     */
    fun transformWallSide(side: WallSide): WallSide {
        val rotationSteps = when (this) {
            NORTH -> 0  // Baseline
            EAST -> 1   // 90° clockwise
            SOUTH -> 2  // 180°
            WEST -> 3   // 270° clockwise
        }

        val sides = WallSide.entries
        val currentIndex = sides.indexOf(side)
        val newIndex = (currentIndex + rotationSteps) % 4
        return sides[newIndex]
    }
}
