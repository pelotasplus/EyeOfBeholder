package pl.pelotasplus.eyeofbeholder.data.model

/**
 * One monster sprite sheet, cut into its six poses in each of the color
 * schemes the sheet carries, plus the overlay sets from its .DCR file.
 */
class MonsterSheet(
    private val posesByColors: Map<MonsterColors, Map<MonsterPose, Cps.ItemIcon>>,
    private val decorationSets: List<Map<MonsterPose, MonsterDecoration>> = emptyList(),
) {

    fun pose(pose: MonsterPose, colors: MonsterColors): Cps.ItemIcon? =
        posesByColors[colors]?.get(pose)

    /** Null where the set does not decorate this pose, or the sheet has no such set. */
    fun decoration(set: MonsterDecorationSetId, pose: MonsterPose): MonsterDecoration? =
        decorationSets.getOrNull(set.value - 1)?.get(pose)

    companion object {
        val EMPTY = MonsterSheet(emptyMap())
    }
}

/**
 * An overlay drawn on top of a monster's pose, cut from the monster's own
 * sheet.
 *
 * Unlike the pose underneath it, an overlay is never recolored — it arrives
 * in the colors it was painted in, which is why a cleric whose body was
 * repainted brown still wears blond hair.
 *
 * @property offsetX From the left edge of the drawn sprite; a mirrored sprite
 *   measures it from the right edge instead
 * @property offsetY From the top edge of the drawn sprite
 */
data class MonsterDecoration(
    val frame: Cps.ItemIcon,
    val offsetX: Int,
    val offsetY: Int,
)

/**
 * Which of a sheet's three color schemes a monster wears.
 *
 * A sublevel defines at most two sprite sheets, so several monsters of one
 * species share one drawing. The game tells them apart by handing the schemes
 * out per monster slot, cycling — two clerics in adjacent slots stand in the
 * same room in differently colored robes.
 */
enum class MonsterColors {
    AS_DRAWN,
    FIRST_RECOLOR,
    SECOND_RECOLOR;

    companion object {
        fun forSlot(slot: Int): MonsterColors = entries[slot % entries.size]
    }
}

/**
 * Cuts the sheet's six poses out and recolors each of them into every scheme,
 * then cuts the overlays [dcr] places on the same sheet.
 */
fun Cps.monsterSheet(gfx: MonsterGfx, dcr: Dcr? = null): MonsterSheet {
    val poses = monsterFrameRects[gfx.sizeClass].mapValues { (_, rect) -> cutFrame(rect) }
    return MonsterSheet(
        posesByColors = MonsterColors.entries.associateWith { colors ->
            poses.mapValues { (pose, frame) -> frame.recolored(recolorTable(pose, colors)) }
        },
        decorationSets = dcr?.sets.orEmpty().map { set ->
            set.mapValues { (_, placement) ->
                MonsterDecoration(
                    frame = cutFrame(placement.rect),
                    offsetX = placement.offsetX,
                    offsetY = placement.offsetY,
                )
            }
        },
    )
}

/**
 * What to repaint one pose with, empty for [MonsterColors.AS_DRAWN].
 *
 * A sheet carries its recolor tables painted into itself, in a strip of pixels
 * to the right of and below the poses: three columns per pose, 16 rows tall,
 * starting at (302, 184) and stepping three columns along per pose, in the
 * order the poses sit in [MonsterPose]. The first column lists the colors the
 * pose was painted in, the second and third what each of them becomes under
 * the other two schemes. A row whose first column is transparent holds no
 * entry.
 */
private fun Cps.recolorTable(pose: MonsterPose, colors: MonsterColors): Map<PaletteIndex, PaletteIndex> {
    val paintedWith = RECOLOR_TABLE_X + RECOLOR_TABLE_COLUMNS * pose.ordinal
    val repaintWith = paintedWith + when (colors) {
        MonsterColors.AS_DRAWN -> return emptyMap()
        MonsterColors.FIRST_RECOLOR -> 1
        MonsterColors.SECOND_RECOLOR -> 2
    }

    return buildMap {
        for (row in 0 until RECOLOR_TABLE_ROWS) {
            val y = RECOLOR_TABLE_Y + row
            val from = pixels[y * width + paintedWith]
            if (!from.isTransparent) {
                put(from, pixels[y * width + repaintWith])
            }
        }
    }
}

private const val RECOLOR_TABLE_X = 302
private const val RECOLOR_TABLE_Y = 184
private const val RECOLOR_TABLE_COLUMNS = 3
private const val RECOLOR_TABLE_ROWS = 16

private fun Cps.ItemIcon.recolored(table: Map<PaletteIndex, PaletteIndex>): Cps.ItemIcon =
    if (table.isEmpty()) this else copy(pixels = pixels.map { table[it] ?: it })

/** Cuts one monster pose out of a sprite sheet CPS. */
private fun Cps.cutFrame(rect: MonsterFrameRect): Cps.ItemIcon {
    val out = ArrayList<PaletteIndex>(rect.w * rect.h)
    for (y in rect.y until rect.y + rect.h) {
        for (x in rect.x until rect.x + rect.w) {
            out.add(pixels[y * width + x])
        }
    }
    return Cps.ItemIcon(w = rect.w, h = rect.h, pixels = out)
}
