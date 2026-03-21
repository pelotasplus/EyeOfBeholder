package pl.pelotasplus.eyeofbeholder.data.model

import kotlinx.collections.immutable.ImmutableList

/**
 * Parsed .DEC file — decoration layout data that defines how overlay graphics
 * are positioned within the 3D viewport at different distances and angles.
 *
 * A DEC file pairs with a CPS file: the CPS holds the pixel data, the DEC
 * holds the layout (which rectangles from the CPS to draw, and where on screen).
 *
 * ## Decoration rendering pipeline
 * 1. Each [Decoration] entry has 10 slots corresponding to 10 "wall positions"
 *    (distances/angles relative to the party — see ViewPort.decorationPositions)
 * 2. For a given view position, look up [rectangleIndices][slot] → rectangle in CPS
 * 3. Draw that CPS rectangle at screen position ([xCoords][slot], [yCoords][slot])
 * 4. Follow [linkToNextDecoration] to draw chained parts (e.g. separate top/bottom pieces)
 *
 * ## Binary format (.DEC)
 * - decorationCount (u16)
 * - For each decoration: 10 rect indices (u8), link (u8), flags (u8),
 *   10 x-coords (u16 each), 10 y-coords (u16 each)
 * - rectangleCount (u16)
 * - For each rectangle: x, y, w, h (u16 each) — x and w are in 8-pixel units
 */
data class Dec(
    val name: String,
    val decorations: ImmutableList<Decoration>,
    val rectangles: ImmutableList<DecorationRectangle>,
) {
    /**
     * Represents a single decoration entry.
     *
     * @param rectangleIndices Indices into DecorationData.rectangles (10 entries)
     * @param linkToNextDecoration Index into DecorationData.decorations
     * @param flags Decoration flags
     * @param xCoords X coordinates in the game view where to render the overlay (10 entries)
     * @param yCoords Y coordinates in the game view where to render the overlay (10 entries)
     */
    data class Decoration(
        val rectangleIndices: ImmutableList<Int>,
        val linkToNextDecoration: Int,
        val flags: Int,
        val xCoords: ImmutableList<Int>,
        val yCoords: ImmutableList<Int>,
    ) {
        override fun toString(): String {
            return "Decoration(rectangleIndices=..., linkToNextDecoration=$linkToNextDecoration, flags=$flags, xCoords=..., yCoords=...)"
        }
    }

    /**
     * Represents a rectangle used for decoration rendering.
     * Raw values should be multiplied by 8 to get actual screen coordinates/dimensions.
     *
     * @param x X coordinate (multiply by 8 for screen coord)
     * @param y Y coordinate (multiply by 8 for screen coord)
     * @param w Width (multiply by 8 for actual width)
     * @param h Height (multiply by 8 for actual height)
     */
    data class DecorationRectangle(
        val x: Int,
        val y: Int,
        val w: Int,
        val h: Int,
    )

    override fun toString(): String {
        return "Dec(name='$name', decorations=${decorations.size}, rectangles=${rectangles.size})"
    }
}
