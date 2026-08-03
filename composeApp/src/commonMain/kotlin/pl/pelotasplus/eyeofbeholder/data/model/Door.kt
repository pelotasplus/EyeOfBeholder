package pl.pelotasplus.eyeofbeholder.data.model

/**
 * A door type definition loaded from the INF file's sublevel block.
 *
 * Each sublevel can have up to 2 door types (DoorTypeOne and DoorTypeTwo).
 * The door type determines the visual appearance (CPS graphic) and the
 * clickable button area. Door state (open/closed/stuck) is encoded in the
 * maze wall type value.
 *
 * ## Door rendering
 * A door is drawn in two parts:
 * 1. Door frame — the VCN wall type 2 tileset (archway shape)
 * 2. Door panel — a rectangle from the door's CPS graphic, positioned
 *    according to [DoorRenderData] for the current wall position
 * 3. Optional button — drawn on top if the wall type is "with button"
 *
 * ## Rectangles
 * Three rectangles define the door graphic at different distances:
 * - Index 0: close-up (wall position 21 — directly in front)
 * - Index 1: medium distance (wall positions 15-17)
 * - Index 2: far distance (wall positions 6-10)
 *
 * @property index Door index within the sublevel (0 or 1)
 * @property type Door visual type identifier
 * @property knob Knob/handle position indicator
 * @property rectangles Source rectangles in the CPS for 3 distance levels
 * @property cps The door graphic image (e.g. DOOR1.CPS)
 * @property buttons Up to 2 button definitions (source rect + screen position)
 */
data class Door(
    val index: Int,
    val type: Int,
    val knob: Int,
    val rectangles: List<Rectangle>,
    val cps: Cps,
    val buttons: List<Button>,
) {
    data class Rectangle(
        val x: Int,
        val y: Int,
        val w: Int,
        val h: Int,
    )

    data class Button(
        val x: Int,
        val y: Int,
        val w: Int,
        val h: Int,
        val posX: Int,
        val posY: Int,
    )
}

/**
 * What a door is made of, which is what decides how it opens.
 *
 * The two that have a second layer take it from the sublevel's next door
 * definition rather than carrying one of their own, so a level spends two of
 * its two door slots on a single door.
 */
enum class DoorKind {
    /** One panel, sliding up out of sight. */
    PANEL,

    /**
     * A panel sliding up off something fixed behind it, which is how a door
     * opens onto a view rather than onto darkness.
     */
    OVER_A_VIEW,

    /** Two halves parting, into the lintel and into the threshold. */
    SPLIT;

    companion object {
        fun of(type: Int): DoorKind = entries.getOrElse(type) { PANEL }
    }
}

/**
 * A door does not hang in one place: it slides up out of its frame as it
 * opens, and a jammed one rests a little higher again. Both distances are in
 * screen pixels, so both change with how far off the door is being drawn.
 *
 * [size] is which of the three rectangles is being drawn, which is what names
 * that distance — 0 the square ahead, 2 three squares back.
 */
fun doorPanelTop(size: Int, panelHeight: Int, opened: Int, stuck: Boolean): Int =
    doorwayRows(size).last + 1 - panelHeight - (opened * LIFTED_PER_STEP[size]) -
        (if (stuck) STUCK_LIFT[size] else 0)

/**
 * The rows of the view a door panel may occupy: the opening it hangs in.
 *
 * A door slides up into the ceiling, so most of the way open there is nothing
 * of it left below the lintel and nothing is drawn at all. Only the doorway
 * shows the panel — above it is masonry the panel has gone behind.
 */
fun doorwayRows(size: Int): IntRange = LINTEL[size]..THRESHOLD[size]

/**
 * Where a door panel's left edge goes: the middle of the doorway it fills,
 * less half the panel.
 *
 * The doorways of a row are all as wide as the one straight ahead and sit side
 * by side, so the ones off to either side run past the edge of the view and
 * only part of each is on screen. That is why the frame drawn for one of them
 * is narrower than the doorway really is, and why centring a panel on the
 * frame's own tiles puts it too far in.
 */
fun doorPanelLeft(size: Int, panelWidth: Int, relativeX: Int): Int =
    ViewPort.COLS / 2 + (relativeX * DOORWAY_WIDTH[size]) - panelWidth / 2

fun splitAboveTop(size: Int, opened: Int): Int =
    UPPER_HALF_FROM[size] - (opened * PARTS_PER_STEP[size])

/**
 * The lower half sinks at half the rate the upper half rises, having only the
 * threshold to go into against the other's whole ceiling.
 */
fun splitBelowTop(size: Int, panelHeight: Int, opened: Int): Int =
    THRESHOLD[size] - panelHeight + ((opened * PARTS_PER_STEP[size]) shr 1)

/**
 * How wide a doorway is, the same for every one in a row.
 *
 * Not the original's own number but the width of the frame [viewSlots] draws
 * for the doorway straight ahead, which is the one of a row that fits on the
 * screen whole. It agrees with every hand-placed panel the view table used to
 * carry save one, and that one was the position that looked wrong.
 *
 * Working it instead from the game's own table of where the middle of each
 * square lands — which we hold and which matches the game's byte for byte —
 * puts the panels of the two squares off to the side somewhere else again, and
 * plainly out of their doorways. Something about that route is still misread,
 * so this stands until someone finds what.
 */
private val DOORWAY_WIDTH = listOf(128, 80, 48)

// The tables below are the original game's, one entry per size.

/** Where the opening starts. */
private val LINTEL = listOf(16, 24, 30)

/** Where it ends, which is also where a shut door's foot rests. */
private val THRESHOLD = listOf(85, 70, 58)

private val LIFTED_PER_STEP = listOf(18, 12, 8)

private val STUCK_LIFT = listOf(5, 3, 1)

/** Where a shut [DoorKind.SPLIT] door's upper half starts. */
private val UPPER_HALF_FROM = listOf(15, 24, 31)

private val PARTS_PER_STEP = listOf(12, 8, 5)

