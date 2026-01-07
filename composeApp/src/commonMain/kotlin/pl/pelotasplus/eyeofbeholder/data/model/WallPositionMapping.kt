package pl.pelotasplus.eyeofbeholder.data.model

/**
 * Defines the mapping from wall rendering position (0-24) to maze coordinates
 * relative to player position and facing direction.
 *
 * Works in conjunction with WallRenderData to provide complete wall rendering information:
 * - WallPositionMapping: WHERE in the maze to look for walls (game logic)
 * - WallRenderData: HOW to render walls on screen (rendering config)
 */
data class WallPositionMapping(
    /**
     * Relative X offset from player position when facing NORTH (baseline direction).
     * Negative = left, Positive = right
     */
    val relativeX: Int,

    /**
     * Relative Y offset from player position when facing NORTH (baseline direction).
     * Negative = forward/ahead, Positive = backward
     */
    val relativeY: Int,

    /**
     * Which wall side to check on the target square.
     * NORTH/EAST/SOUTH/WEST relative to the maze, not player.
     */
    val wallSide: WallSide,

    /**
     * Optional: Wall types to render (null = all types)
     * Allows filtering to only show FixedWall, or include Doors, Stairs, etc.
     */
    val acceptedTypes: Set<WallTypeCategory>? = null
)

/**
 * Cardinal directions in maze coordinates (absolute, not player-relative)
 */
enum class WallSide {
    NORTH, EAST, SOUTH, WEST
}

/**
 * Categories of wall types for filtering what gets rendered
 */
enum class WallTypeCategory {
    FIXED_WALL,
    DOOR,
    STAIRS,
    SPECIAL
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

/**
 * Layer 5 (backdrop)
 *                                   ─────────────────────────────────
 * Layer 4                               A | B | C | D | E | F | G
 *                                          ─── ─── ─── ─── ───
 * Layer 3                                   H | I | J | K | L
 *                                              ─── ─── ───
 * Layer 2                                       M | N | O
 *                                              ─── ─── ───
 * Layer 1 (closest)                             P | ^ | Q
 */
/**
 * The 25 wall position mappings, corresponding to wallRenderData positions 0-24.
 *
 * Coordinates are relative to player facing NORTH (negative Y = forward).
 * Each entry maps to the same index in wallRenderData.
 *
 * Layer organization:
 * Layer 4 - Far back (3 tiles ahead): positions 0-10
 * Layer 3 - Middle back (2 tiles ahead): positions 11-17
 * Layer 2 - Middle front (1 tile ahead): positions 18-22
 * Layer 1 - Closest (same row as player): positions 23-24
 */
val wallPositionMappings: List<WallPositionMapping> = listOf(
    // Layer 4 - Far back (3 tiles ahead)
    // Side walls left back
    WallPositionMapping(-3, -3, WallSide.EAST),     // Position 0: A-east
    WallPositionMapping(-2, -3, WallSide.EAST),     // Position 1: B-east
    WallPositionMapping(-1, -3, WallSide.EAST),     // Position 2: C-east

    // Side walls right back
    WallPositionMapping(1, -3, WallSide.WEST),      // Position 3: E-west
    WallPositionMapping(2, -3, WallSide.WEST),      // Position 4: F-west
    WallPositionMapping(3, -3, WallSide.WEST),      // Position 5: G-west

    // Front walls far back
    WallPositionMapping(-2, -3, WallSide.SOUTH),    // Position 6: B-south
    WallPositionMapping(-1, -3, WallSide.SOUTH),    // Position 7: C-south
    WallPositionMapping(0, -3, WallSide.SOUTH),     // Position 8: D-south
    WallPositionMapping(1, -3, WallSide.SOUTH),     // Position 9: E-south
    WallPositionMapping(2, -3, WallSide.SOUTH),     // Position 10: F-south

    // Layer 3 - Middle back (2 tiles ahead)
    // Side walls middle back left
    WallPositionMapping(-2, -2, WallSide.EAST),     // Position 11: H-east
    WallPositionMapping(-1, -2, WallSide.EAST),     // Position 12: I-east

    // Side walls middle back right
    WallPositionMapping(1, -2, WallSide.WEST),      // Position 13: K-west
    WallPositionMapping(2, -2, WallSide.WEST),      // Position 14: L-west

    // Front walls middle back
    WallPositionMapping(-1, -2, WallSide.SOUTH),    // Position 15: I-south
    WallPositionMapping(0, -2, WallSide.SOUTH),     // Position 16: J-south
    WallPositionMapping(1, -2, WallSide.SOUTH),     // Position 17: K-south

    // Layer 2 - Middle front (1 tile ahead)
    // Side walls middle front
    WallPositionMapping(-1, -1, WallSide.EAST),     // Position 18: M-east
    WallPositionMapping(1, -1, WallSide.WEST),      // Position 19: O-west

    // Front walls middle front
    WallPositionMapping(-1, -1, WallSide.SOUTH),    // Position 20: M-south
    WallPositionMapping(0, -1, WallSide.SOUTH),     // Position 21: N-south
    WallPositionMapping(1, -1, WallSide.SOUTH),     // Position 22: O-south

    // Layer 1 - Closest (same row as player)
    // Side walls front
    WallPositionMapping(-1, 0, WallSide.EAST),      // Position 23: P-east
    WallPositionMapping(1, 0, WallSide.WEST),       // Position 24: Q-west
)
