package pl.pelotasplus.eyeofbeholder.data.model

import kotlinx.serialization.Serializable
/**
 * Cardinal directions in maze coordinates (absolute, not player-relative)
 */
@Serializable
enum class WallSide {
    NORTH, EAST, SOUTH, WEST;

    /**
     * The face across the square from this one. A doorway is one square with
     * the same door on both of its faces, and working it moves both: a door
     * opened from one side stands open from the other.
     */
    val opposite: WallSide get() = entries[(ordinal + 2) % entries.size]
}

/**
 * Player facing direction in maze coordinates
 */
@Serializable
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

    /** The square one step this way from [from]. */
    fun oneStepFrom(from: Location): Location {
        val (dx, dy) = transformCoordinates(0, -1)
        return Location(from.x + dx, from.y + dy)
    }

    /**
     * The side a square turns back towards whoever walks onto it this way.
     *
     * It is that wall which decides whether the step is allowed, and not the
     * one on the square being left: a doorway carries its door on the face it
     * is entered by.
     */
    val wallSideFacingBack: WallSide get() = WallSide.entries[ordinal].opposite
}
