package pl.pelotasplus.eyeofbeholder.data.model

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

/**
 * The small integer id spaces the game data is built from.
 *
 * Every one of these was a plain Int, which meant an item's icon id, a door
 * definition slot and a monster type were mutually assignable. They index
 * different tables and are never interchangeable.
 */

/**
 * An item's icon id — an index into [Cps.shapeMap], which maps it to a
 * position and size inside the ITEMS1.CPS / ITEMS2.CPS sprite sheet. Not a
 * position in the sheet itself, and not an [ItemTypeId].
 */
@JvmInline
@Serializable
value class ItemIconId(val value: Int)

/** An index into the item type table parsed from ITEM.DAT. */
@JvmInline
@Serializable
value class ItemTypeId(val value: Int)

/**
 * An index into ITEM.DAT's table of item names. An item has two — what the
 * party call it before they know what it is, and what it really is.
 */
@JvmInline
@Serializable
value class ItemNameId(val value: Int)

/**
 * Which of a sublevel's two door definitions a wall uses (0 or 1 → SubLevel.doors).
 */
@JvmInline
value class DoorIndex(val value: Int)

/** An index into the DEC file's decoration table. */
@JvmInline
value class DecorationId(val value: Int)

/** A monster type — an index into the sublevel's monster property table. */
@JvmInline
@Serializable
value class MonsterTypeId(val value: Int)

/**
 * Which set of overlays a monster type wears, counting from 1 — a .DCR file
 * holds several. Zero means the type wears none, which the parser reads as no
 * id at all rather than as a set.
 */
@JvmInline
value class MonsterDecorationSetId(val value: Int)

/**
 * How many 2/3 shrink steps a sprite is drawn at, from [itemScaleSteps]. Also
 * the number of times its colors are remapped through
 * [Palette.distanceFadeTable], so distant sprites darken as they shrink — the
 * two counts are the same quantity in the original engine.
 */
@JvmInline
value class ScaleSteps(val value: Int) {
    /** -1 marks a quadrant behind the party, which is never drawn. */
    val isVisible: Boolean get() = value >= 0
}

/**
 * A count of game timer ticks — the 18.2 Hz interrupt the DOS original paced
 * everything by, one tick being 55 milliseconds.
 *
 * Scripts pause in these: a scripted walk moves the party a square and waits
 * 15, which is the beat between its steps.
 */
@JvmInline
value class Ticks(val value: Int) {
    val inMilliseconds: Long get() = value.toLong() * MILLISECONDS_PER_TICK
}

private const val MILLISECONDS_PER_TICK = 55L

/**
 * A horizontal pixel coordinate in the 176x120 [ViewPort], measured from its
 * left edge.
 *
 * Kept apart from [ScreenY] because the renderer threads x and y through the
 * same shaped tables ([blockScreenCoords] interleaves them) and the same
 * helper signatures, where transposing them produces a plausible-looking
 * frame rather than an error.
 */
@JvmInline
value class ScreenX(val value: Int) {
    operator fun plus(dx: Int) = ScreenX(value + dx)
    operator fun minus(dx: Int) = ScreenX(value - dx)
}

/** A vertical pixel coordinate in the [ViewPort], measured from its top edge. */
@JvmInline
value class ScreenY(val value: Int) {
    operator fun plus(dy: Int) = ScreenY(value + dy)
    operator fun minus(dy: Int) = ScreenY(value - dy)
}
