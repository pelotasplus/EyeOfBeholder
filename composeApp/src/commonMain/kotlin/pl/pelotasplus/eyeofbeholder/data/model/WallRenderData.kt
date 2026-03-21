package pl.pelotasplus.eyeofbeholder.data.model

/**
 * Pixel-level rendering configuration for one of the 25 wall positions in the
 * 3D viewport. Works with [WallPositionMapping] to fully describe wall rendering:
 * - [WallPositionMapping]: WHERE in the maze to look (game logic)
 * - [WallRenderData]: HOW to draw the wall on screen (rendering config)
 *
 * The viewport uses 8×8 pixel tiles from the VCN. These parameters control
 * which tiles from the VMP wall type array to draw, and where on the 22×15
 * tile viewport grid to place them.
 *
 * @property baseOffset Starting index into the VMP wall type tile array (431 tiles per type)
 * @property offsetInViewPort Starting position on the 22×15 viewport tile grid
 *           (row-major: position = y * 22 + x)
 * @property visibleWidthInBlocks Number of tile rows to draw (height in tiles)
 * @property visibleHeightInBlocks Number of tile columns to draw (width in tiles)
 * @property skipValue Tiles to skip in the VMP array between rows (for non-contiguous layouts)
 * @property flipFlag 1 = mirror the wall horizontally (used for right-side walls to
 *           reuse left-side tile data)
 */
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
    // A-east       //   0
    WallRenderData(104, 66, 5, 1, 2, 0),
    // B-east       //   1
    WallRenderData(102, 68, 5, 3, 0, 0),
    // C-east       //   2
    WallRenderData(97, 74, 5, 1, 0, 0),

    // Side-Walls right back
    // E-west       //   3
    WallRenderData(97, 79, 5, 1, 0, 1),
    // F-west       //   4
    WallRenderData(102, 83, 5, 3, 0, 1),
    // G-west       //   5
    WallRenderData(104, 87, 5, 1, 2, 1),

    // Front walls back
    // B-south      //   6
    WallRenderData(133, 66, 5, 2, 4, 0),
    // C-south      //   7
    WallRenderData(129, 68, 5, 6, 0, 0),
    // D-south      //   8
    WallRenderData(129, 74, 5, 6, 0, 0),
    // E-south      //   9
    WallRenderData(129, 80, 5, 6, 0, 0),
    // F-south      //  10
    WallRenderData(129, 86, 5, 2, 4, 0),

    // Side walls middle back left
    // H-east       //  11
    WallRenderData(117, 66, 6, 2, 0, 0),
    // I-east       //  12
    WallRenderData(81, 50, 8, 2, 0, 0),

    // Side walls middle back right
    // K-west       //  13
    WallRenderData(81, 58, 8, 2, 0, 1),
    // L-west       //  14
    WallRenderData(117, 86, 6, 2, 0, 1),

    // Front walls middle back
    // I-south      //  15
    WallRenderData(163, 44, 8, 6, 4, 0),
    // J-south       // 16
    WallRenderData(159, 50, 8, 10, 0, 0),
    // K-south       // 17
    WallRenderData(159, 60, 8, 6, 4, 0),

    // Side walls middle front left
    // M-east        // 18
    WallRenderData(45, 25, 12, 3, 0, 0),

    // Side walls middle front right
    // O-west        // 19
    WallRenderData(45, 38, 12, 3, 0, 1),

    // Front walls middle front
    // M-south       // 20
    WallRenderData(252, 22, 12, 3, 13, 0),
    // N-south       // 21
    WallRenderData(239, 25, 12, 16, 0, 0),
    // O-south       // 22
    WallRenderData(239, 41, 12, 3, 13, 0),

    // Side wall front left
    // P-east        // 23
    WallRenderData(0, 0, 15, 3, 0, 0),

    // Side wall front right
    // Q-west        // 24
    WallRenderData(0, 19, 15, 3, 0, 1),
)
