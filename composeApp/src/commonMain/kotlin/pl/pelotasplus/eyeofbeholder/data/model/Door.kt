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

