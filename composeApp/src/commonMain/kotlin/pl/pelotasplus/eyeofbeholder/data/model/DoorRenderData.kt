package pl.pelotasplus.eyeofbeholder.data.model

data class DoorRenderData(
    val offsetInViewPortX: Int?,
    val offsetInViewPortY: Int,
    val buttonIndex: Int?,
    val rectangleIndex: Int,
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
    DoorRenderData(null, 66, null, 2),
    // B-east       //   1
    DoorRenderData(null, 68, null, 2),
    // C-east       //   2
    DoorRenderData(null, 74, null, 2),

    // Side-Walls right back
    // E-west       //   3
    DoorRenderData(null, 79, null, 2),
    // F-west       //   4
    DoorRenderData(null, 83, null, 2),
    // G-west       //   5
    DoorRenderData(null, 87, null, 2),

    // Front walls back
    // B-south      //   6
    DoorRenderData(-24, 30, null, 2),
    // C-south      //   7
    DoorRenderData(24, 30, null, 2),
    // D-south      //   8
    DoorRenderData(72, 30, null, 2),
    // E-south      //   9
    DoorRenderData(120, 30, null, 2),
    // F-south      //  10
    DoorRenderData(168, 30, null, 2),

    // Side walls middle back left
    // H-east       //  11
    DoorRenderData(-1, 66, null, 2),
    // I-east       //  12
    DoorRenderData(-1, 50, null, 2),

    // Side walls middle back right
    // K-west       //  13
    DoorRenderData(-1, 58, null, 2),
    // L-west       //  14
    DoorRenderData(-1, 86, null, 2),

    // Front walls middle back
    // I-south      //  15
    DoorRenderData(0, 24, null, 1),
    // J-south       // 16
    DoorRenderData(60, 24, 1, 1),
    // K-south       // 17
    DoorRenderData(140, 24, null, 1),

    // Side walls middle front left
    // M-east        // 18
    DoorRenderData(null, 25, null, 1),

    // Side walls middle front right
    // O-west        // 19
    DoorRenderData(null, 38, null, 1),

    // Front walls middle front
    // M-south       // 20
    DoorRenderData(null, -1, null, 1),
    // N-south       // 21
    DoorRenderData(52, 16, 0, 0),
    // O-south       // 22
    DoorRenderData(null, -1, 0, 0),

    // Side wall front left
    // P-east        // 23
    DoorRenderData(null, 0, 0, 0),

    // Side wall front right
    // Q-west        // 24
    DoorRenderData(null, 19, 0, 9),
)
