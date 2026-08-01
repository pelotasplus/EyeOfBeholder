package pl.pelotasplus.eyeofbeholder.data.model

/**
 * The 320×200 game screen: PLAYFLD.CPS with the rendered 3D view blitted into
 * its top-left window and the compass needle overlaid from DECORATE.CPS.
 *
 * All coordinates are the original game's, taken from ScummVM's kyra engine:
 * the view is copied to (0,0) at 176×120 (`EoBCoreEngine::drawScene`), and the
 * compass is three shapes drawn at [COMPASS_TARGETS]
 * (`EoBCoreEngine::gui_drawCompass`, EoB2 variant).
 */
class PlayField(
    private val background: Cps,
    private val decorations: Cps,
    private val palette: Palette,
) {
    private val pixels = MutableList(WIDTH * HEIGHT) { RGB(0, 0, 0, true) }

    fun render(viewPort: ViewPort, direction: Direction): PlayField {
        drawBackground()
        drawViewPort(viewPort)
        drawCompass(direction)
        return this
    }

    fun getRows(): List<List<RGB>> = pixels.chunked(WIDTH)

    private fun draw(x: Int, y: Int, rgb: RGB) {
        if (x !in 0 until WIDTH || y !in 0 until HEIGHT) return
        pixels[y * WIDTH + x] = rgb
    }

    private fun drawBackground() {
        for (y in 0 until minOf(HEIGHT, background.height)) {
            for (x in 0 until minOf(WIDTH, background.width)) {
                val index = background.pixels[y * background.width + x]
                // the frame is opaque: index 0 is a real color here, not a hole
                draw(x, y, palette.colors[index.value])
            }
        }
    }

    private fun drawViewPort(viewPort: ViewPort) {
        viewPort.getRows().forEachIndexed { y, row ->
            row.forEachIndexed { x, rgb ->
                if (!rgb.transparent) draw(VIEW_X + x, VIEW_Y + y, rgb)
            }
        }
    }

    private fun drawCompass(direction: Direction) {
        COMPASS_TARGETS.forEachIndexed { part, target ->
            val srcX = direction.compassColumn * COMPASS_WIDTH
            val srcY = COMPASS_SOURCE_Y[part]
            val height = COMPASS_HEIGHT[part]

            for (y in 0 until height) {
                for (x in 0 until COMPASS_WIDTH) {
                    val index = decorations.pixels[(srcY + y) * decorations.width + srcX + x]
                    if (index.isTransparent) continue
                    draw(target.first + x, target.second + y, palette.colors[index.value])
                }
            }
        }
    }

    companion object {
        const val WIDTH = 320
        const val HEIGHT = 200

        /** Where the 3D view is copied into the frame. */
        const val VIEW_X = 0
        const val VIEW_Y = 0

        /**
         * Shape sources in DECORATE.CPS. `Screen_EoB::encodeShape` takes x and
         * width in 8-pixel units, so the compass columns are 3 units = 24px
         * wide and start at `direction * 24`.
         */
        private const val COMPASS_WIDTH = 24
        private val COMPASS_SOURCE_Y = listOf(120, 137, 147)
        private val COMPASS_HEIGHT = listOf(17, 10, 10)

        /** EoB2 shpX/shpY from gui_drawCompass. */
        private val COMPASS_TARGETS = listOf(114 to 131, 79 to 158, 151 to 158)
    }
}

/**
 * Column of this direction's shapes in DECORATE.CPS. The original engine
 * indexes the compass shape array by `_currentDirection`, which is
 * north, east, south, west in that order.
 */
private val Direction.compassColumn: Int
    get() = when (this) {
        Direction.NORTH -> 0
        Direction.EAST -> 1
        Direction.SOUTH -> 2
        Direction.WEST -> 3
    }
