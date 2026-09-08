package pl.pelotasplus.eyeofbeholder.data.model.sequence

/**
 * One instruction of a scene's animation.
 *
 * A scene moves by running short lists of these against the screen. Ten
 * numbers each, and which of them mean anything depends on the first — a
 * sound uses one field and a copied rectangle uses eight — which is why they
 * are named for their place in the record rather than for what they do.
 *
 * @property what which of [Draws], [DrawsAndRubsOut], [Copies], [Sounds] or
 *   [FlashesTheColours] this is
 * @property obj the shape to draw, or the sound to play, or — for a copy —
 *   which sheet to take it from: the scene's own, or the one loaded beside it
 * @property x where it goes, in pixels
 * @property y likewise
 * @property delay how long to hold afterwards, in ticks
 * @property palette which colour table to flash to, counted from the scene's
 *   first
 * @property fromX where a copy comes from, in columns of eight pixels
 * @property fromY likewise, but in pixels
 * @property wide how much a copy takes, again in columns of eight
 * @property deep likewise, in pixels
 */
data class SequenceCommand(
    val what: Int,
    val obj: Int,
    val x: Int,
    val y: Int,
    val delay: Int,
    val palette: Int,
    val fromX: Int,
    val fromY: Int,
    val wide: Int,
    val deep: Int,
) {
    companion object {
        /** Flash to another colour table, hold, and come back. */
        const val FlashesTheColours = 0x00

        /** Draw a shape, hold, and put the background back over it. */
        const val DrawsAndRubsOut = 0x01

        /** Draw a shape and leave it there. */
        const val Draws = 0x02

        /** Copy a rectangle of a sheet onto the screen. */
        const val Copies = 0x05

        /** Make a noise, and nothing else. */
        const val Sounds = 0x06

        /** The end of a list. */
        const val Ends = 0xFF
    }
}

/**
 * Where one of a scene's shapes is cut from its sheet, and how big it is.
 *
 * [across] and [wide] are in columns of eight pixels and the other two in
 * pixels, which is how the sheets are laid out and not a convention worth
 * tidying.
 */
data class SequenceShape(
    val index: Int,
    val across: Int,
    val down: Int,
    val wide: Int,
    val deep: Int,
)
