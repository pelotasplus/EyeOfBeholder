package pl.pelotasplus.eyeofbeholder.data.model

/**
 * Monster sprite-sheet geometry and rendering tables for EoB2 (Darkmoon).
 *
 * Derived from ScummVM's EoB implementation (engines/kyra), which is the
 * authoritative re-implementation of the original DOS engine:
 * - Frame rects: kEoB2EncodeMonsterDefsDOS + EoBCoreEngine::loadMonsterShapes
 * - Screen coords: kEoB2DscShapeCoordsDOS + EoBCoreEngine::drawMonsters
 * - Frame selection: kEoB2DscMonsterFrmOffsTbl2DOS
 * - Sub-position rotation: kEoB2DscItemPosIndexDOS
 *
 * ## Sprite sheets
 * Each monster CPS sheet (320×200, level palette) holds 6 poses:
 * front, side walk A, side walk B, back, attack A, attack B. Side frames are
 * stored facing screen-left and mirrored for the other side. The sheet layout
 * depends on the size class from the INF monster-graphics record
 * ([MonsterGfx.sizeClass]). The sheets also contain pre-drawn mid/small
 * variants; like ScummVM we use only the near-size frames and shrink at
 * runtime (2/3 per distance step, same algorithm as item icons).
 *
 * Not yet implemented (future work): DCR overlay decorations, per-instance
 * palette variants, attack/walk animation frames, monsters on the party's
 * own row.
 */
data class MonsterFrameRect(val x: Int, val y: Int, val w: Int, val h: Int)

/** Frame rects per size class (0=wide-side, 1=humanoid, 2=extra-large), 6 poses each. */
val monsterFrameRects: List<List<MonsterFrameRect>> = listOf(
    // Size class 0 — wide side frames (e.g. mantis)
    listOf(
        MonsterFrameRect(0, 0, 56, 96),     // front
        MonsterFrameRect(56, 40, 96, 56),   // side walk A
        MonsterFrameRect(152, 40, 96, 56),  // side walk B
        MonsterFrameRect(248, 0, 56, 96),   // back
        MonsterFrameRect(0, 96, 56, 96),    // attack A
        MonsterFrameRect(56, 96, 56, 96),   // attack B
    ),
    // Size class 1 — humanoids (guards, skeleton warrior, mage, clerics)
    listOf(
        MonsterFrameRect(0, 0, 56, 96),
        MonsterFrameRect(56, 0, 56, 96),
        MonsterFrameRect(112, 0, 56, 96),
        MonsterFrameRect(168, 0, 56, 96),
        MonsterFrameRect(224, 0, 56, 96),
        MonsterFrameRect(0, 96, 56, 96),
    ),
    // Size class 2 — extra-large (dragon, beholder)
    listOf(
        MonsterFrameRect(0, 0, 80, 88),
        MonsterFrameRect(80, 0, 80, 88),
        MonsterFrameRect(160, 0, 80, 88),
        MonsterFrameRect(240, 0, 80, 88),
        MonsterFrameRect(0, 88, 80, 88),
        MonsterFrameRect(80, 88, 80, 88),
    ),
)

/** Cuts one monster pose out of a sprite sheet CPS. */
fun Cps.cutFrame(rect: MonsterFrameRect): Cps.ItemIcon {
    val out = ArrayList<PaletteIndex>(rect.w * rect.h)
    for (y in rect.y until rect.y + rect.h) {
        for (x in rect.x until rect.x + rect.w) {
            out.add(pixels[y * width + x])
        }
    }
    return Cps.ItemIcon(w = rect.w, h = rect.h, pixels = out)
}

/**
 * One block of the view cone that can contain monsters, ordered back-to-front.
 *
 * @property relativeX X offset from the player when facing NORTH (negative = left)
 * @property relativeY Y offset from the player when facing NORTH (negative = ahead)
 * @property blockIndex The original engine's visible-block index (0-17) used to
 *           address [blockScreenCoords]
 * @property scaleSteps 2/3-shrink steps applied at this distance
 */
data class MonsterBlock(
    val relativeX: Int,
    val relativeY: Int,
    val blockIndex: Int,
    val scaleSteps: ScaleSteps,
)

/** Visible blocks by depth row (party's own row not yet rendered). */
val monsterBlockRows: Map<Int, List<MonsterBlock>> = mapOf(
    -3 to (-3..3).mapIndexed { i, vx -> MonsterBlock(vx, -3, i, scaleSteps = ScaleSteps(2)) },
    -2 to (-2..2).mapIndexed { i, vx -> MonsterBlock(vx, -2, 7 + i, scaleSteps = ScaleSteps(1)) },
    -1 to (-1..1).mapIndexed { i, vx -> MonsterBlock(vx, -1, 12 + i, scaleSteps = ScaleSteps(0)) },
)

/**
 * Screen coordinates for objects in the view cone: 18 visible blocks ×
 * 5 sub-positions × (x, y). x is relative to the viewport horizontal center
 * (88); y to the baseline — 127 for monsters, 124 for items. The sprite is
 * drawn centered on x with its feet on y. Shared by monsters everywhere and
 * by items lying on the party's own square (block 16).
 * (kEoB2DscShapeCoordsDOS, converted from unsigned hex to signed.)
 */
val blockScreenCoords: List<Int> = listOf(
    // blocks 0-6: three rows ahead
    -111, -63, -95, -63, -139, -59, -117, -59, -120, -61,
    -76, -63, -60, -63, -95, -59, -74, -59, -80, -61,
    -43, -63, -27, -63, -53, -59, -31, -59, -40, -61,
    -8, -63, 8, -63, -10, -59, 10, -59, 0, -61,
    27, -63, 43, -63, 31, -59, 53, -59, 40, -61,
    60, -63, 76, -63, 74, -59, 95, -59, 80, -61,
    95, -63, 111, -63, 117, -59, 139, -59, 120, -61,
    // blocks 7-11: two rows ahead
    -118, -53, -92, -53, -152, -45, -120, -45, -118, -50,
    -66, -53, -40, -53, -84, -45, -51, -45, -59, -50,
    -13, -53, 13, -53, -16, -45, 16, -45, 0, -50,
    40, -53, 66, -53, 51, -45, 84, -45, 59, -50,
    92, -53, 118, -53, 120, -45, 152, -45, 118, -50,
    // blocks 12-14: one row ahead
    -110, -35, -67, -35, -140, -22, -83, -22, -98, -30,
    -22, -35, 22, -35, -27, -22, 27, -22, 0, -30,
    67, -35, 110, -35, 83, -22, 140, -22, 98, -30,
    // blocks 15-17: party's own row (unused for now)
    -128, -4, 128, -4, -128, -66, 128, -66, 128, 0,
    -38, -4, 38, -4, -38, -66, 38, -66, 0, 0,
    -128, -4, 128, -4, -128, -66, 128, -66, 128, 0,
)

/**
 * Item scale steps by depth row and view-relative quadrant:
 * index = dim * 4 + quadrant, where dim is 0 (three rows ahead) to 3 (the
 * party's own square). -1 = the item is not drawn there (too far, or behind
 * the camera on the own square). Niche items use quadrant 0.
 * (kEoB2DscItemScaleIndexDOS)
 */
val itemScaleSteps: List<ScaleSteps> = listOf(
    -1, -1, 3, 3,
    2, 2, 2, 2,
    1, 1, 1, 1,
    0, 0, -1, -1,
).map { ScaleSteps(it) }

/**
 * Niche-item screen X (absolute, before centering on icon width) per visible
 * block. (kEoB2DscItemShpXDOS, signed)
 */
val nicheItemX: List<Int> = listOf(
    -56, -8, 40, 88, 136, 184, 232,
    -72, 8, 88, 168, 248,
    -40, 88, 216,
    -88, 88, 264,
)

/** Niche-item baseline Y per depth row (dim 0-3); the icon's bottom edge. */
val nicheItemY: List<Int> = listOf(37, 49, 56, 0)

/**
 * Rotates an object's absolute sub-position (0=NW, 1=NE, 2=SW, 3=SE in maze
 * coordinates) into a view-relative one, per player facing direction.
 * Shared by items and monsters. Index: playerDirection * 4 + pos.
 * (kEoB2DscItemPosIndexDOS)
 */
val monsterPosIndex: List<Int> = listOf(
    0, 1, 2, 3,
    2, 0, 3, 1,
    3, 2, 1, 0,
    1, 3, 0, 2,
)

/**
 * Frame selection by facing: index = playerDirection * 4 + monsterDirection.
 * abs(value) is the pose subframe (1=front, 2/3=sides, 4=back); negative
 * means draw horizontally mirrored. This is the standing (animStep 0) half of
 * kEoB2DscMonsterFrmOffsTbl2DOS; the walk-cycle half comes with animation.
 */
val monsterFrameSelect: List<Int> = listOf(
    4, -2, 1, 3,
    3, 4, -2, 1,
    1, 3, 4, -2,
    -2, 1, 3, 4,
)
