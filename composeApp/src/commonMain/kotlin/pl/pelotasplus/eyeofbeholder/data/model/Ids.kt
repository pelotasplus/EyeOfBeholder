package pl.pelotasplus.eyeofbeholder.data.model

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
value class ItemIconId(val value: Int)

/** An index into the item type table parsed from ITEM.DAT. */
@JvmInline
value class ItemTypeId(val value: Int)

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
value class MonsterTypeId(val value: Int)

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
