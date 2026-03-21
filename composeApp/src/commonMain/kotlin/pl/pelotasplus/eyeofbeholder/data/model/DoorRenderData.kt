package pl.pelotasplus.eyeofbeholder.data.model

/**
 * Door-specific rendering parameters for one of the 25 wall positions.
 *
 * When a wall position contains a door, the renderer first draws the door frame
 * (VCN wall type 2), then overlays the door panel from the door's CPS graphic
 * using these offsets.
 *
 * @property offsetInViewPortX Screen X position to draw the door panel;
 *           null means the door panel is not visible at this wall position
 *           (only the frame is drawn, e.g. for side-view doors)
 * @property offsetInViewPortY Screen Y position for the door panel
 * @property buttonIndex Which button definition from [Door.buttons] to use;
 *           null = no button rendered at this position
 * @property rectangleIndex Which rectangle from [Door.rectangles] to use
 *           (0=close, 1=medium, 2=far distance)
 */
data class DoorRenderData(
    val offsetInViewPortX: Int?,
    val offsetInViewPortY: Int,
    val buttonIndex: Int?,
    val rectangleIndex: Int,
)

/**
 * Layer 5 (backdrop)
 *                                   ─────────────────────────────────
 * Layer 4                       A 0 | B 1  |  C 2  | D  | 3 E   | 4 F | 5 G
 *                                   | 6    |  7    | 8  |   9   |  10 |
 *                                    ────── ─────── ──── ─────── ─────
 * Layer 3                             H 11 | I  12 | J  | 13 K  | 14 L
 *                                          | 15    | 16 |    17 |
 *                                           ─────── ──── ───────
 * Layer 2                                     M 18 | N  | 19 O
 *                                            20    | 21 |    22
 *                                           ─────── ──── ───────
 * Layer 1 (closest)                           P 23 | ^  | 24 Q
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
    DoorRenderData(null, 66, null, 2),
    // I-east       //  12
    DoorRenderData(null, 50, null, 2),

    // Side walls middle back right
    // K-west       //  13
    DoorRenderData(null, 58, null, 2),
    // L-west       //  14
    DoorRenderData(null, 86, null, 2),

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
