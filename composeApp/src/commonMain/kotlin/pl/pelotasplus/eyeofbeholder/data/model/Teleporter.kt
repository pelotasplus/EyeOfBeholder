package pl.pelotasplus.eyeofbeholder.data.model

/**
 * The shimmer that hangs over a teleporter square.
 *
 * A teleporter is not something a level draws. Its wall byte maps to a wall
 * with no tiles and no shape, so the square reads as an open doorway and can
 * be walked into; the sparkle over it belongs to the engine, which recognises
 * the byte itself. That is why [TELEPORTER_WALL] is a number here and not
 * something read out of a file.
 *
 * Twenty-six sparks hang in the air over such a square, in two clouds of
 * thirteen, each cloud drawn with its own blob. On every pulse the two clouds
 * trade blobs, and that trade is the whole of the animation.
 *
 * There is one pair of clouds per depth row, bigger and further apart the
 * nearer the square; the party's own square has none, being under their feet.
 *
 * The blobs, the cloud positions and the placement are all transcribed.
 */
val WallByte.isTeleporter: Boolean get() = value == TELEPORTER_WALL

private const val TELEPORTER_WALL = 44

/** How long a teleporter holds one half of its flicker. */
val TELEPORTER_PULSE = Ticks(10)

/** Which way round the two clouds over a teleporter are wearing their blobs. */
enum class TeleporterPulse {
    AS_LAID_OUT,
    TRADED;

    val next: TeleporterPulse get() = if (this == AS_LAID_OUT) TRADED else AS_LAID_OUT
}

/** Where one of the blobs a spark is drawn as sits in DECORATE.CPS. */
data class SparkBlob(val x: Int, val y: Int, val w: Int, val h: Int)

/** A pixel offset within the [ViewPort], for a spark or for a whole cloud. */
data class SparkOffset(val dx: Int, val dy: Int)

/**
 * One cloud of sparks: where its thirteen sparks sit relative to the
 * teleporter, and how far the cloud as a whole is shifted off them.
 *
 * The shift belongs to the cloud rather than to the blob, so a cloud that
 * carries one keeps it after the trade — which makes those sparks jump as
 * well as change size.
 */
data class SparkCloud(
    val sparks: List<SparkOffset>,
    val shiftedBy: SparkOffset = SparkOffset(0, 0),
)

/**
 * A teleporter as seen from one depth row.
 *
 * @property leftOfNiche how far left of the square's [nicheItemX] the clouds
 *   start
 * @property top the top of the clouds on screen
 * @property clouds the two clouds, in the order they are drawn
 * @property blobs one blob per cloud, swapped between them by the pulse
 */
data class TeleporterHaze(
    val leftOfNiche: Int,
    val top: ScreenY,
    val clouds: List<SparkCloud>,
    val blobs: List<SparkBlob>,
) {
    fun blobFor(cloud: Int, pulse: TeleporterPulse): SparkBlob =
        blobs[if (pulse == TeleporterPulse.TRADED) blobs.lastIndex - cloud else cloud]
}

/**
 * The haze a teleporter wears at each depth, nearest row first. A row the
 * party stands on has none, which is [depthRow] returning null.
 */
val teleporterHazes: List<TeleporterHaze> = listOf(
    TeleporterHaze(
        leftOfNiche = 40,
        top = ScreenY(13),
        clouds = listOf(
            SparkCloud(
                shiftedBy = SparkOffset(-4, -4),
                sparks = sparksAt(
                    12, 7, 26, 1, 62, 3, 12, 26, 42, 19, 64, 24, 2, 45,
                    22, 37, 40, 50, 54, 39, 10, 62, 22, 73, 62, 68,
                ),
            ),
            SparkCloud(
                sparks = sparksAt(
                    6, 6, 42, 4, 55, 10, 4, 27, 26, 22, 55, 29, 14, 42,
                    27, 53, 46, 40, 66, 48, 6, 71, 6, 71, 45, 76,
                ),
            ),
        ),
        blobs = listOf(SparkBlob(96, 88, 16, 14), SparkBlob(96, 103, 8, 7)),
    ),
    TeleporterHaze(
        leftOfNiche = 28,
        top = ScreenY(21),
        clouds = listOf(
            SparkCloud(
                sparks = sparksAt(
                    10, 4, 20, 0, 46, 1, 12, 16, 31, 16, 47, 16, 18, 24,
                    40, 29, 1, 33, 8, 42, 17, 50, 47, 46, 31, 37,
                ),
            ),
            SparkCloud(
                sparks = sparksAt(
                    2, 2, 1, 17, 1, 47, 8, 30, 17, 14, 17, 38, 28, 1,
                    30, 25, 31, 51, 36, 17, 38, 5, 40, 43, 47, 34,
                ),
            ),
        ),
        blobs = listOf(SparkBlob(96, 111, 8, 7), SparkBlob(96, 119, 8, 5)),
    ),
    TeleporterHaze(
        leftOfNiche = 18,
        top = ScreenY(26),
        clouds = listOf(
            SparkCloud(
                sparks = sparksAt(
                    0, 19, 5, 1, 6, 8, 9, 12, 4, 26, 8, 31, 18, 5,
                    18, 21, 22, 16, 26, 8, 26, 29, 10, 0, 10, 0,
                ),
            ),
            SparkCloud(
                sparks = sparksAt(
                    0, 9, 0, 30, 4, 17, 8, 22, 8, 6, 16, 0, 17, 13,
                    18, 32, 21, 2, 20, 9, 22, 27, 26, 20, 26, 20,
                ),
            ),
        ),
        blobs = listOf(SparkBlob(96, 125, 8, 5), SparkBlob(96, 131, 8, 3)),
    ),
)

/**
 * Which haze a square at depth row [dim] wears — 0 is three rows ahead, 3 the
 * party's own square, which has no entry and so no haze.
 */
fun teleporterHazeAt(dim: Int): TeleporterHaze? =
    teleporterHazes.getOrNull(NEAREST_ROW - dim)

/** The depth row one square ahead, which wears the largest haze. */
private const val NEAREST_ROW = 2

private fun sparksAt(vararg coordinates: Int): List<SparkOffset> =
    coordinates.toList().chunked(2) { (dx, dy) -> SparkOffset(dx, dy) }

/**
 * The squares in view standing on a teleporter, as the blocks they are drawn
 * at.
 *
 * A square is asked about by the face it turns towards the party, the same one
 * a click is aimed at.
 */
fun teleportersInView(
    party: Location,
    facing: Direction,
    wallAt: (Location, WallSide) -> Maz.WallType,
): List<ViewBlock> {
    val facingUs = facing.transformWallSide(WallSide.SOUTH)

    return viewBlockRows.values.flatten().filter { block ->
        val (dx, dy) = facing.transformCoordinates(block.relativeX, block.relativeY)
        wallAt(Location(party.x + dx, party.y + dy), facingUs).asByte().isTeleporter
    }
}
