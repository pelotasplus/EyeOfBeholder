package pl.pelotasplus.eyeofbeholder.data.model

data class DoorRenderData(
    val offsetInViewPortX: Int,
    val offsetInViewPortY: Int,
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

val doorRenderData: List<DoorRenderData> = listOf(
    // Side-Walls left back
    // A-east       //   0
    DoorRenderData(-1, 66, 5, 1, 2, 0),
    // B-east       //   1
    DoorRenderData(-1, 68, 5, 3, 0, 0),
    // C-east       //   2
    DoorRenderData(-1, 74, 5, 1, 0, 0),

    // Side-Walls right back
    // E-west       //   3
    DoorRenderData(-1, 79, 5, 1, 0, 1),
    // F-west       //   4
    DoorRenderData(-1, 83, 5, 3, 0, 1),
    // G-west       //   5
    DoorRenderData(-1, 87, 5, 1, 2, 1),

    // Front walls back
    // B-south      //   6
    DoorRenderData(-24, 30, 5, 2, 4, 0),
    // C-south      //   7
    DoorRenderData(24, 30, 5, 6, 0, 0),
    // D-south      //   8
    DoorRenderData(72, 30, 5, 6, 0, 0),
    // E-south      //   9
    DoorRenderData(120, 30, 5, 6, 0, 0),
    // F-south      //  10
    DoorRenderData(168, 30,0, 5, 2,  0),

    // Side walls middle back left
    // H-east       //  11
    DoorRenderData(-1, 66, 6, 2, 0, 0),
    // I-east       //  12
    DoorRenderData(-1, 50, 8, 2, 0, 0),

    // Side walls middle back right
    // K-west       //  13
    DoorRenderData(-1, 58, 8, 2, 0, 1),
    // L-west       //  14
    DoorRenderData(-1, 86, 6, 2, 0, 1),

    // Front walls middle back
    // I-south      //  15
    DoorRenderData(0, 24, 8, 6, 4, 0),
    // J-south       // 16
    DoorRenderData(60, 24, 8, 10, 0, 0),
    // K-south       // 17
    DoorRenderData(140, 24, 8, 6, 4, 0),

    // Side walls middle front left
    // M-east        // 18
    DoorRenderData(-1, 25, 12, 3, 0, 0),

    // Side walls middle front right
    // O-west        // 19
    DoorRenderData(-1, 38, 12, 3, 0, 1),

    // Front walls middle front
    // M-south       // 20
    DoorRenderData(-1, -1, 12, 3, 13, 0),
    // N-south       // 21
    DoorRenderData(52, 16, 12, 16, 0, 0),
    // O-south       // 22
    DoorRenderData(-1, -1, 12, 3, 13, 0),

    // Side wall front left
    // P-east        // 23
    DoorRenderData(-1, 0, 15, 3, 0, 0),

    // Side wall front right
    // Q-west        // 24
    DoorRenderData(-1, 19, 15, 3, 0, 1),
)
