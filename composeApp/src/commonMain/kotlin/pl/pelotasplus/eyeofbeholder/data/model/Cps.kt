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
 * The [shapeMap] array maps an item's icon index to a "shape slot" that
 * determines the icon's position and size within the sprite sheet.
 *
 * @property name Original filename
 * @property width Image width (always 320)
 * @property height Image height (always 200)
 * @property pixels Flat array of palette indices, row-major (width × height entries)
 */
data class Cps(
    val name: String,
    val width: Int,
    val height: Int,
    val pixels: List<Int>, // each pixel index to an entry from the color palette (PAL file)
) {
    fun getItemIcon(icon: Int): ItemIcon? {
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

    private fun extractRect(x: Int, y: Int, w: Int, h: Int): List<Int> {
        return buildList(w * h) {
            for (row in y until y + h) {
                for (col in x until x + w) {
                    if (row in 0 until height && col in 0 until width) {
                        add(pixels[row * width + col])
                    } else {
                        add(0)
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
        val pixels: List<Int>,
    )


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
        0x00
    )


    sealed class ShapeLocation {
        data class SmallItem(val shapeIndex: Int, val x: Int, val y: Int, val w: Int, val h: Int) :
            ShapeLocation()

        data class LargeItem(val shapeIndex: Int, val x: Int, val y: Int, val w: Int, val h: Int) :
            ShapeLocation()

        data object NoShape : ShapeLocation()
    }

    fun locate(icon: Int): ShapeLocation {
        require(icon in shapeMap.indices) { "icon $icon out of range (0..${shapeMap.size - 1})" }

        val shapeMapVal = shapeMap[icon]

        return when {
            shapeMapVal < NUM_LARGE_ITEM_SHAPES -> {
                val i = shapeMapVal
                ShapeLocation.LargeItem(
                    shapeIndex = i,
                    x = (i / DIV) * 64,
                    y = (i % DIV) * MUL,
                    w = 64,
                    h = 24
                )
            }

            shapeMapVal < 15 -> ShapeLocation.NoShape
            else -> {
                val i = shapeMapVal - 15
                ShapeLocation.SmallItem(
                    shapeIndex = i,
                    x = (i / DIV) * 32,
                    y = (i % DIV) * MUL,
                    w = 32,
                    h = 24
                )
            }
        }
    }

    companion object {
        private const val NUM_LARGE_ITEM_SHAPES = 11
        private const val DIV = 8
        private const val MUL = 24
    }
}
