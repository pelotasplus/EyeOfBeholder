package pl.pelotasplus.eyeofbeholder.data.model

/**
 * Parsed .CPS file — a LCW-compressed 320×200 image used for various game graphics.
 *
 * CPS files are the primary image format in Eye of the Beholder. They store:
 * - Door graphics (DOOR1.CPS, DOOR2.CPS)
 * - Decoration overlays (wall paintings, levers, alcoves, etc.)
 * - Item icon sprite sheets (ITEMS1.CPS — all small/large item icons)
 * - UI elements, portraits, cutscene images, etc.
 *
 * Pixels are palette indices (0-255) that reference the current sublevel's .PAL file.
 * Index 0 is always transparent.
 *
 * ## Item icon sprite sheet (ITEMS1.CPS)
 * Contains all item icons packed in a grid. Icons come in two sizes:
 * - Large items: 64×24 pixels (weapons, shields, armor)
 * - Small items: 32×24 pixels (potions, keys, gems, scrolls)
 *
 * [Companion.shapeOf] maps an item's icon index to a "shape slot" that
 * determines the icon's position and size within the sprite sheet.
 *
 * @property name Original filename
 * @property width Image width (always 320)
 * @property height Image height (always 200)
 * @property pixels Flat array of palette indices, row-major (width × height entries)
 * @property palette The file's own palette when it embeds one; such an image
 *   must be drawn with it rather than with a sublevel .PAL
 */
data class Cps(
    val name: String,
    val width: Int,
    val height: Int,
    val pixels: List<PaletteIndex>,
    val palette: Palette? = null,
) {
    fun getItemIcon(icon: ItemIconId): ItemIcon? {
        return when (val shape = locate(icon)) {
            is ShapeLocation.SmallItem -> ItemIcon(
                w = shape.w,
                h = shape.h,
                pixels = extractRect(shape.x, shape.y, shape.w, shape.h),
            )

            is ShapeLocation.LargeItem -> ItemIcon(
                w = shape.w,
                h = shape.h,
                pixels = extractRect(shape.x, shape.y, shape.w, shape.h),
            )

            is ShapeLocation.NoShape -> null
        }
    }

    /** A rectangle of the sheet on its own, ready to be drawn. */
    fun cut(x: Int, y: Int, w: Int, h: Int): ItemIcon =
        ItemIcon(w = w, h = h, pixels = extractRect(x, y, w, h))

    private fun extractRect(x: Int, y: Int, w: Int, h: Int): List<PaletteIndex> {
        return buildList(w * h) {
            for (row in y until y + h) {
                for (col in x until x + w) {
                    if (row in 0 until height && col in 0 until width) {
                        add(pixels[row * width + col])
                    } else {
                        add(PaletteIndex.TRANSPARENT)
                    }
                }
            }
        }
    }

    override fun toString(): String {
        return "Cps(name='$name', width=$width, height=$height, pixels=${pixels.size})"
    }

    data class ItemIcon(
        val w: Int,
        val h: Int,
        val pixels: List<PaletteIndex>,
    )

    sealed class ShapeLocation {
        data class SmallItem(val shapeIndex: Int, val x: Int, val y: Int, val w: Int, val h: Int) :
            ShapeLocation()

        data class LargeItem(val shapeIndex: Int, val x: Int, val y: Int, val w: Int, val h: Int) :
            ShapeLocation()

        data object NoShape : ShapeLocation()
    }

    /**
     * Which sheet an icon's shape is on and where. The answer does not depend
     * on which sheet is asked — the map from icon to shape is one table for
     * the whole game — so [Companion.shapeOf] says the same without a sheet.
     */
    fun locate(icon: ItemIconId): ShapeLocation = shapeOf(icon)

    companion object {
        /** A speaker's portrait: four to a file, one per corner. */
        const val PORTRAIT_WIDTH = 160
        const val PORTRAIT_HEIGHT = 96

        /**
         * Where an icon's shape lives, and so how big the thing is: the small
         * shapes and the large ones are packed into different sheets, and
         * which of the two an icon uses is what says whether a thing is small
         * enough to go somewhere a large one cannot.
         */
        fun shapeOf(icon: ItemIconId): ShapeLocation {
            require(icon.value in shapeMap.indices) {
                "icon ${icon.value} out of range (0..${shapeMap.size - 1})"
            }

            val shape = shapeMap[icon.value]

            return when {
                shape < NUM_LARGE_ITEM_SHAPES -> ShapeLocation.LargeItem(
                    shapeIndex = shape,
                    x = (shape / DIV) * 64,
                    y = (shape % DIV) * MUL,
                    w = 64,
                    h = 24,
                )

                shape < FIRST_SMALL_ITEM_SHAPE -> ShapeLocation.NoShape

                else -> {
                    val i = shape - FIRST_SMALL_ITEM_SHAPE
                    ShapeLocation.SmallItem(
                        shapeIndex = i,
                        x = (i / DIV) * 32,
                        y = (i % DIV) * MUL,
                        w = 32,
                        h = 24,
                    )
                }
            }
        }

        private const val NUM_LARGE_ITEM_SHAPES = 11
        private const val FIRST_SMALL_ITEM_SHAPE = 15
        private const val DIV = 8
        private const val MUL = 24

        private val shapeMap = intArrayOf(
            0x00, 0x00, 0x00, 0x05, 0x01, 0x02, 0x03, 0x04,
            0x03, 0x05, 0x06, 0x06, 0x12, 0x05, 0x0F, 0x12,
            0x14, 0x08, 0x13, 0x11, 0x15, 0x18, 0x07, 0x07,
            0x07, 0x1F, 0x09, 0x23, 0x09, 0x09, 0x09, 0x09,
            0x08, 0x1C, 0x1C, 0x1A, 0x1B, 0x21, 0x1D, 0x1D,
            0x22, 0x22, 0x22, 0x00, 0x16, 0x00, 0x00, 0x17,
            0x17, 0x17, 0x17, 0x17, 0x22, 0x21, 0x19, 0x23,
            0x10, 0x1E, 0x17, 0x25, 0x17, 0x26, 0x12, 0x21,
            0x17, 0x23, 0x1C, 0x00, 0x20, 0x25, 0x12, 0x18,
            0x1F, 0x07, 0x07, 0x15, 0x15, 0x0F, 0x03, 0x09,
            0x1E, 0x1E, 0x00, 0x00, 0x00, 0x00, 0x00, 0x1B,
            0x1B, 0x17, 0x17, 0x16, 0x16, 0x21, 0x08, 0x25,
            0x25, 0x25, 0x25, 0x28, 0x03, 0x04, 0x21, 0x00,
            0x17, 0x00, 0x20, 0x24, 0x27, 0x27, 0x1C, 0x27,
            0x00,
        )
    }
}
