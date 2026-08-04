package pl.pelotasplus.eyeofbeholder.data.model

/**
 * Pixel-level rendering configuration for a wall at one view position.
 *
 * The viewport uses 8×8 pixel tiles from the VCN. These parameters control
 * which tiles from the VMP wall type array to draw, and where on the 22×15
 * tile viewport grid to place them.
 *
 * @property baseOffset Starting index into the VMP wall type tile array (431 tiles per type)
 * @property offsetInViewPort Starting position on the 22×15 viewport tile grid
 *           (row-major: position = y * 22 + x)
 * @property heightInTiles Number of tile rows to draw (vertical size)
 * @property widthInTiles Number of tile columns to draw (horizontal size)
 * @property skipValue Tiles to skip in the VMP array between rows (for non-contiguous layouts)
 * @property flipFlag 1 = mirror the wall horizontally (used for right-side walls to
 *           reuse left-side tile data)
 */
data class WallRenderData(
    val baseOffset: Int,
    val offsetInViewPort: Int,
    val heightInTiles: Int,
    val widthInTiles: Int,
    val skipValue: Int,
    val flipFlag: Int,
)

/**
 * Door-specific rendering parameters for a wall at one view position.
 *
 * When a wall position contains a door, the renderer first draws the door frame
 * (VCN wall type 2), then overlays the door panel from the door's CPS graphic.
 * Where that panel lands is not a position's own business either way round:
 * [doorPanelLeft] says how far across and [doorPanelTop] how far down, both
 * from the doorway rather than from the frame drawn for it. A position chooses
 * only which size of panel and which button.
 *
 * @property buttonIndex Which button definition from [Door.buttons] to use;
 *           null = no button rendered at this position
 * @property rectangleIndex Which rectangle from [Door.rectangles] to use
 *           (0=close, 1=medium, 2=far distance). A row is all the same distance
 *           away, so the positions of one share a rectangle; giving the left of
 *           a row a smaller one draws its door shrunk and pulled inwards.
 */
data class DoorRenderData(
    val buttonIndex: Int?,
    val rectangleIndex: Int,
)

/**
 * Maps a view position into the DEC file's own coordinate space — NOT screen
 * pixels. Decorations are authored against 10 "decoration wall slots" (0-9)
 * relative to the party; the actual screen x/y comes from the DEC data itself
 * (`decoration.xCoords[wall]` / `yCoords[wall]`) at draw time.
 *
 * Slot grid relative to the party:
 * ```
 * 9 7 3 7 9
 * 8 6 2 6 8
 * 8 5 1 5 8
 *   4 0 4
 *     ^=party pos.
 * ```
 *
 * @param xFlip 0=left side wall, 1=right side wall (for mirroring)
 * @param wall decoration slot 0-9, -1=none available for this view position
 * @param xDelta horizontal shift (multiply by 8 for pixels)
 */
data class DecorationPosition(
    val xFlip: Int,
    val wall: Int,
    val xDelta: Int,
)

/**
 * Everything the renderer knows about one of the 25 wall positions in the
 * 3D viewport, in a single row:
 * - WHERE in the maze to look: [relativeX]/[relativeY]/[wallSide] (game logic)
 * - HOW to draw a solid wall there: [wall] (viewport tile geometry)
 * - HOW to draw a door there: [door] (screen pixel offsets)
 * - WHERE a decoration maps to: [decoration] (DEC slot space, see [DecorationPosition])
 *
 * @property label Human name from the layer diagram below (e.g. "C-south")
 * @property relativeX X offset from the player when facing NORTH; negative = left
 * @property relativeY Y offset from the player when facing NORTH; negative = forward
 * @property wallSide Which wall of the target square to check, in absolute maze
 *           coordinates (rotated by player facing at render time)
 * @property floorDecorationX Screen X shift for floor decorations (pits,
 *           pressure plates) at this view position; 0 = centered/none
 */
data class ViewSlot(
    val label: String,
    val relativeX: Int,
    val relativeY: Int,
    val wallSide: WallSide,
    val wall: WallRenderData,
    val door: DoorRenderData,
    val decoration: DecorationPosition,
    val floorDecorationX: Int = 0,
) {
    /** Front walls face the player; the decoration mirror flag (bit 0) only applies there. */
    val isFrontWall: Boolean get() = wallSide == WallSide.SOUTH
}

/**
 * The single source of truth for all 25 view positions.
 *
 * ```
 * Layer 5 (backdrop)
 *                                   ─────────────────────────────────
 * Layer 4 (3 tiles ahead)               A | B | C | D | E | F | G
 *                                          ─── ─── ─── ─── ───
 * Layer 3 (2 tiles ahead)                   H | I | J | K | L
 *                                              ─── ─── ───
 * Layer 2 (1 tile ahead)                        M | N | O
 *                                              ─── ─── ───
 * Layer 1 (player row)                          P | ^ | Q
 * ```
 *
 * Coordinates are relative to the player facing NORTH (negative Y = forward);
 * they are rotated by the actual facing direction at render time. Positions are
 * rendered in list order (back-to-front) so closer walls occlude farther ones.
 */
val viewSlots: List<ViewSlot> = listOf(
    // ── Layer 4: side walls left ─────────────────────────────────────────
    ViewSlot(
        label = "A-east", relativeX = -3, relativeY = -3, wallSide = WallSide.EAST,
        wall = WallRenderData(104, 66, 5, 1, 2, 0),
        door = DoorRenderData(null, 2),
        decoration = DecorationPosition(xFlip = 0, wall = -1, xDelta = 0),
    ),
    ViewSlot(
        label = "B-east", relativeX = -2, relativeY = -3, wallSide = WallSide.EAST,
        wall = WallRenderData(102, 68, 5, 3, 0, 0),
        door = DoorRenderData(null, 2),
        decoration = DecorationPosition(xFlip = 0, wall = 9, xDelta = 0),
    ),
    ViewSlot(
        label = "C-east", relativeX = -1, relativeY = -3, wallSide = WallSide.EAST,
        wall = WallRenderData(97, 74, 5, 1, 0, 0),
        door = DoorRenderData(null, 2),
        decoration = DecorationPosition(xFlip = 0, wall = 7, xDelta = 0),
    ),

    // ── Layer 4: side walls right ────────────────────────────────────────
    ViewSlot(
        label = "E-west", relativeX = 1, relativeY = -3, wallSide = WallSide.WEST,
        wall = WallRenderData(97, 79, 5, 1, 0, 1),
        door = DoorRenderData(null, 2),
        decoration = DecorationPosition(xFlip = 1, wall = 7, xDelta = 0),
    ),
    ViewSlot(
        label = "F-west", relativeX = 2, relativeY = -3, wallSide = WallSide.WEST,
        wall = WallRenderData(102, 83, 5, 3, 0, 1),
        door = DoorRenderData(null, 2),
        decoration = DecorationPosition(xFlip = 1, wall = 9, xDelta = 0),
    ),
    ViewSlot(
        label = "G-west", relativeX = 3, relativeY = -3, wallSide = WallSide.WEST,
        wall = WallRenderData(104, 87, 5, 1, 2, 1),
        door = DoorRenderData(null, 2),
        decoration = DecorationPosition(xFlip = 0, wall = -1, xDelta = 0),
    ),

    // ── Layer 4: front walls ─────────────────────────────────────────────
    ViewSlot(
        label = "B-south", relativeX = -2, relativeY = -3, wallSide = WallSide.SOUTH,
        wall = WallRenderData(133, 66, 5, 2, 4, 0),
        door = DoorRenderData(null, 2),
        decoration = DecorationPosition(xFlip = 0, wall = 3, xDelta = -12),
        floorDecorationX = -88,
    ),
    ViewSlot(
        label = "C-south", relativeX = -1, relativeY = -3, wallSide = WallSide.SOUTH,
        wall = WallRenderData(129, 68, 5, 6, 0, 0),
        door = DoorRenderData(null, 2),
        decoration = DecorationPosition(xFlip = 0, wall = 3, xDelta = -6),
        floorDecorationX = -40,
    ),
    ViewSlot(
        label = "D-south", relativeX = 0, relativeY = -3, wallSide = WallSide.SOUTH,
        wall = WallRenderData(129, 74, 5, 6, 0, 0),
        door = DoorRenderData(null, 2),
        decoration = DecorationPosition(xFlip = 0, wall = 3, xDelta = 0),
    ),
    ViewSlot(
        label = "E-south", relativeX = 1, relativeY = -3, wallSide = WallSide.SOUTH,
        wall = WallRenderData(129, 80, 5, 6, 0, 0),
        door = DoorRenderData(null, 2),
        decoration = DecorationPosition(xFlip = 0, wall = 3, xDelta = 6),
        floorDecorationX = 40,
    ),
    ViewSlot(
        label = "F-south", relativeX = 2, relativeY = -3, wallSide = WallSide.SOUTH,
        wall = WallRenderData(129, 86, 5, 2, 4, 0),
        door = DoorRenderData(null, 2),
        decoration = DecorationPosition(xFlip = 0, wall = 3, xDelta = 12),
        floorDecorationX = 88,
    ),

    // ── Layer 3: side walls ──────────────────────────────────────────────
    ViewSlot(
        label = "H-east", relativeX = -2, relativeY = -2, wallSide = WallSide.EAST,
        wall = WallRenderData(117, 66, 6, 2, 0, 0),
        door = DoorRenderData(null, 2),
        decoration = DecorationPosition(xFlip = 0, wall = 8, xDelta = 0),
    ),
    ViewSlot(
        label = "I-east", relativeX = -1, relativeY = -2, wallSide = WallSide.EAST,
        wall = WallRenderData(81, 50, 8, 2, 0, 0),
        door = DoorRenderData(null, 2),
        decoration = DecorationPosition(xFlip = 0, wall = 6, xDelta = 0),
    ),
    ViewSlot(
        label = "K-west", relativeX = 1, relativeY = -2, wallSide = WallSide.WEST,
        wall = WallRenderData(81, 58, 8, 2, 0, 1),
        door = DoorRenderData(null, 2),
        decoration = DecorationPosition(xFlip = 1, wall = 6, xDelta = 0),
    ),
    ViewSlot(
        label = "L-west", relativeX = 2, relativeY = -2, wallSide = WallSide.WEST,
        wall = WallRenderData(117, 86, 6, 2, 0, 1),
        door = DoorRenderData(null, 2),
        decoration = DecorationPosition(xFlip = 1, wall = 8, xDelta = 0),
    ),

    // ── Layer 3: front walls ─────────────────────────────────────────────
    ViewSlot(
        label = "I-south", relativeX = -1, relativeY = -2, wallSide = WallSide.SOUTH,
        wall = WallRenderData(163, 44, 8, 6, 4, 0),
        door = DoorRenderData(null, 1),
        decoration = DecorationPosition(xFlip = 0, wall = 2, xDelta = -10),
        floorDecorationX = -59,
    ),
    ViewSlot(
        label = "J-south", relativeX = 0, relativeY = -2, wallSide = WallSide.SOUTH,
        wall = WallRenderData(159, 50, 8, 10, 0, 0),
        door = DoorRenderData(1, 1),
        decoration = DecorationPosition(xFlip = 0, wall = 2, xDelta = 0),
    ),
    ViewSlot(
        label = "K-south", relativeX = 1, relativeY = -2, wallSide = WallSide.SOUTH,
        wall = WallRenderData(159, 60, 8, 6, 4, 0),
        door = DoorRenderData(null, 1),
        decoration = DecorationPosition(xFlip = 0, wall = 2, xDelta = 10),
        floorDecorationX = 59,
    ),

    // ── Layer 2: side walls ──────────────────────────────────────────────
    ViewSlot(
        label = "M-east", relativeX = -1, relativeY = -1, wallSide = WallSide.EAST,
        wall = WallRenderData(45, 25, 12, 3, 0, 0),
        door = DoorRenderData(null, 1),
        decoration = DecorationPosition(xFlip = 0, wall = 5, xDelta = 0),
    ),
    ViewSlot(
        label = "O-west", relativeX = 1, relativeY = -1, wallSide = WallSide.WEST,
        wall = WallRenderData(45, 38, 12, 3, 0, 1),
        door = DoorRenderData(null, 1),
        decoration = DecorationPosition(xFlip = 1, wall = 5, xDelta = 0),
    ),

    // ── Layer 2: front walls ─────────────────────────────────────────────
    ViewSlot(
        label = "M-south", relativeX = -1, relativeY = -1, wallSide = WallSide.SOUTH,
        wall = WallRenderData(252, 22, 12, 3, 13, 0),
        door = DoorRenderData(null, 0),
        decoration = DecorationPosition(xFlip = 0, wall = 1, xDelta = -16),
        floorDecorationX = -98,
    ),
    ViewSlot(
        label = "N-south", relativeX = 0, relativeY = -1, wallSide = WallSide.SOUTH,
        wall = WallRenderData(239, 25, 12, 16, 0, 0),
        door = DoorRenderData(0, 0),
        decoration = DecorationPosition(xFlip = 0, wall = 1, xDelta = 0),
    ),
    ViewSlot(
        label = "O-south", relativeX = 1, relativeY = -1, wallSide = WallSide.SOUTH,
        wall = WallRenderData(239, 41, 12, 3, 13, 0),
        door = DoorRenderData(0, 0),
        decoration = DecorationPosition(xFlip = 0, wall = 1, xDelta = 16),
        floorDecorationX = 98,
    ),

    // ── Layer 1: side walls at the player's row ──────────────────────────
    ViewSlot(
        label = "P-east", relativeX = -1, relativeY = 0, wallSide = WallSide.EAST,
        wall = WallRenderData(0, 0, 15, 3, 0, 0),
        door = DoorRenderData(0, 0),
        decoration = DecorationPosition(xFlip = 0, wall = 4, xDelta = 0),
    ),
    ViewSlot(
        label = "Q-west", relativeX = 1, relativeY = 0, wallSide = WallSide.WEST,
        wall = WallRenderData(0, 19, 15, 3, 0, 1),
        // rectangleIndex 9 is out of the documented 0-2 range but unused:
        // offsetInViewPortX is null, so no door panel is ever drawn here.
        door = DoorRenderData(0, 9),
        decoration = DecorationPosition(xFlip = 1, wall = 4, xDelta = 0),
    ),
)

/** Every wall the party can see from [from], facing [facing]. */
fun wallsInSight(
    from: Location,
    facing: Direction,
    wallAt: (Location, WallSide) -> Maz.WallType,
): List<Maz.WallType> = viewSlots.map { slot ->
    val (dx, dy) = facing.transformCoordinates(slot.relativeX, slot.relativeY)
    wallAt(Location(from.x + dx, from.y + dy), facing.transformWallSide(slot.wallSide))
}
