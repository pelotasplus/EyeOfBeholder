package pl.pelotasplus.eyeofbeholder.data.model

import kotlinx.collections.immutable.ImmutableList

/**
 * Represents parsed DEC file data containing decoration information.
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
}
