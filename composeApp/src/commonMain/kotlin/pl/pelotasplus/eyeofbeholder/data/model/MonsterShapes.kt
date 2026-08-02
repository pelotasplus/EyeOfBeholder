package pl.pelotasplus.eyeofbeholder.data.model

/**
 * Where one pose sits on a monster's sprite sheet.
 *
 * A sheet is a 320×200 CPS in the level's palette holding six poses, laid out
 * according to the size class in the INF monster-graphics record
 * ([MonsterGfx.sizeClass]). Sheets also carry pre-drawn mid and small copies
 * of every pose, which we ignore: only the near-size frames are cut, and
 * distance shrinks them at runtime by 2/3 a step, the same way item icons
 * shrink. Which colors an instance is painted in is [MonsterSheet]'s business,
 * and where it lands on screen is [blockScreenCoords]'.
 *
 * The rects come from the original game. Read them off a sheet rather than
 * re-deriving them by eye.
 *
 * Not yet implemented: DCR overlay decorations, attack and walk animation
 * frames, monsters standing on the party's own row.
 */
data class MonsterFrameRect(val x: Int, val y: Int, val w: Int, val h: Int)

/**
 * The six poses every monster sheet holds.
 *
 * [SIDE_A] and [SIDE_B] are the two halves of the walk cycle and are painted
 * facing screen-left; a monster walking the other way is the same frame drawn
 * mirrored. A standing monster seen from the side uses [SIDE_A].
 */
enum class MonsterPose {
    FRONT,
    SIDE_A,
    SIDE_B,
    BACK,
    ATTACK_A,
    ATTACK_B,
}

/** Frame rects per size class (0=wide-side, 1=humanoid, 2=extra-large). */
val monsterFrameRects: List<Map<MonsterPose, MonsterFrameRect>> = listOf(
    // Size class 0 — wide side frames (e.g. mantis)
    mapOf(
        MonsterPose.FRONT to MonsterFrameRect(0, 0, 56, 96),
        MonsterPose.SIDE_A to MonsterFrameRect(56, 40, 96, 56),
        MonsterPose.SIDE_B to MonsterFrameRect(152, 40, 96, 56),
        MonsterPose.BACK to MonsterFrameRect(248, 0, 56, 96),
        MonsterPose.ATTACK_A to MonsterFrameRect(0, 96, 56, 96),
        MonsterPose.ATTACK_B to MonsterFrameRect(56, 96, 56, 96),
    ),
    // Size class 1 — humanoids (guards, skeleton warrior, mage, clerics)
    mapOf(
        MonsterPose.FRONT to MonsterFrameRect(0, 0, 56, 96),
        MonsterPose.SIDE_A to MonsterFrameRect(56, 0, 56, 96),
        MonsterPose.SIDE_B to MonsterFrameRect(112, 0, 56, 96),
        MonsterPose.BACK to MonsterFrameRect(168, 0, 56, 96),
        MonsterPose.ATTACK_A to MonsterFrameRect(224, 0, 56, 96),
        MonsterPose.ATTACK_B to MonsterFrameRect(0, 96, 56, 96),
    ),
    // Size class 2 — extra-large (dragon, beholder)
    mapOf(
        MonsterPose.FRONT to MonsterFrameRect(0, 0, 80, 88),
        MonsterPose.SIDE_A to MonsterFrameRect(80, 0, 80, 88),
        MonsterPose.SIDE_B to MonsterFrameRect(160, 0, 80, 88),
        MonsterPose.BACK to MonsterFrameRect(240, 0, 80, 88),
        MonsterPose.ATTACK_A to MonsterFrameRect(0, 88, 80, 88),
        MonsterPose.ATTACK_B to MonsterFrameRect(80, 88, 80, 88),
    ),
)

/** What the party sees of a monster: which pose, and which way round. */
data class MonsterFacing(val pose: MonsterPose, val mirrored: Boolean = false)

/**
 * What the party sees of a standing monster, given which way each of them
 * faces. This is the standing half of the original game's table; the
 * walk-cycle half comes with animation.
 */
fun monsterFacing(partyFacing: Direction, monsterFacing: Direction): MonsterFacing =
    standingMonsterFacings[partyFacing.ordinal * Direction.entries.size + monsterFacing.ordinal]

/** Indexed by party facing, then by the direction the monster faces. */
private val standingMonsterFacings: List<MonsterFacing> = listOf(
    // party facing north
    MonsterFacing(MonsterPose.BACK),
    MonsterFacing(MonsterPose.SIDE_A, mirrored = true),
    MonsterFacing(MonsterPose.FRONT),
    MonsterFacing(MonsterPose.SIDE_B),
    // party facing east
    MonsterFacing(MonsterPose.SIDE_B),
    MonsterFacing(MonsterPose.BACK),
    MonsterFacing(MonsterPose.SIDE_A, mirrored = true),
    MonsterFacing(MonsterPose.FRONT),
    // party facing south
    MonsterFacing(MonsterPose.FRONT),
    MonsterFacing(MonsterPose.SIDE_B),
    MonsterFacing(MonsterPose.BACK),
    MonsterFacing(MonsterPose.SIDE_A, mirrored = true),
    // party facing west
    MonsterFacing(MonsterPose.SIDE_A, mirrored = true),
    MonsterFacing(MonsterPose.FRONT),
    MonsterFacing(MonsterPose.SIDE_B),
    MonsterFacing(MonsterPose.BACK),
)
