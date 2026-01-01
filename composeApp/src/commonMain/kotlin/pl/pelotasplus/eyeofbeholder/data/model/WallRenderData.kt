package pl.pelotasplus.eyeofbeholder.data.model

data class WallRenderData(
    val baseOffset: Int,
    val offsetInViewPort: Int,
    val visibleWidthInBlocks: Int,
    val visibleHeightInBlocks: Int,
    val skipValue: Int,
    val flipFlag: Int,
)

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

val wallRenderData: List<WallRenderData> = listOf(
    // Side-Walls left back
    // A-east
    WallRenderData(104, 66, 5, 1, 2, 0),
    // B-east
    WallRenderData(102, 68, 5, 3, 0, 0),
    // C-east
    WallRenderData(97, 74, 5, 1, 0, 0),

    // Side-Walls right back
    // E-west
    WallRenderData(97, 79, 5, 1, 0, 1),
    // F-west
    WallRenderData(102, 83, 5, 3, 0, 1),
    // G-west       // 5
    WallRenderData(104, 87, 5, 1, 2, 1),

    // Frontwalls back
    // B-south
    WallRenderData(133, 66, 5, 2, 4, 0),
    // C-south
    WallRenderData(129, 68, 5, 6, 0, 0),
    // D-south
    WallRenderData(129, 74, 5, 6, 0, 0),
    // E-south
    WallRenderData(129, 80, 5, 6, 0, 0),
    // F-south      // 10
    WallRenderData(129, 86, 5, 2, 4, 0),

    // Side walls middle back left
    // H-east
    WallRenderData(117, 66, 6, 2, 0, 0),
    // I-east
    WallRenderData(81, 50, 8, 2, 0, 0),

    // Side walls middle back right
    // K-west
    WallRenderData(81, 58, 8, 2, 0, 1),
    // L-west
    WallRenderData(117, 86, 6, 2, 0, 1),

    // Frontwalls middle back
    // I-south      // 15
    WallRenderData(163, 44, 8, 6, 4, 0),
    // J-south
    WallRenderData(159, 50, 8, 10, 0, 0),
    // K-south
    WallRenderData(159, 60, 8, 6, 4, 0),

    // Side walls middle front left
    // M-east
    WallRenderData(45, 25, 12, 3, 0, 0),

    // Side walls middle front right
    // O-west
    WallRenderData(45, 38, 12, 3, 0, 1),

    // Frontwalls middle front
    // M-south       // 20
    WallRenderData(252, 22, 12, 3, 13, 0),
    // O-south
    WallRenderData(239, 41, 12, 3, 13, 0),
    // N-south
    WallRenderData(239, 25, 12, 16, 0, 0),

    // Side wall front left
    // P-east
    WallRenderData(0, 0, 15, 3, 0, 0),

    // Side wall front right
    // Q-west
    WallRenderData(0, 19, 15, 3, 0, 1),
)
