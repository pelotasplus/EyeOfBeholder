package pl.pelotasplus.eyeofbeholder.data.model

/**
 * The 320×200 game screen: PLAYFLD.CPS with the rendered 3D view blitted into
 * its top-left window and the compass needle overlaid from DECORATE.CPS.
 *
 * All coordinates are the original game's: the view is copied to (0,0) at
 * 176×120, and the compass is three shapes drawn at [COMPASS_TARGETS].
 */
class PlayField(
    private val background: Cps,
    private val decorations: Cps,
    private val palette: Palette,
    private val font: Font? = null,
) {
    private val pixels = MutableList(WIDTH * HEIGHT) { RGB(0, 0, 0, true) }

    fun render(
        viewPort: ViewPort,
        direction: Direction,
        dialogue: DialogueScene? = null,
    ): PlayField {
        drawBackground()
        drawViewPort(viewPort)
        drawCompass(direction)
        dialogue?.let(::drawDialogue)
        return this
    }

    /**
     * A conversation, drawn over the view the way the script asked for it.
     *
     * Scripts build one out of drawing instructions — clear the view, draw who
     * is talking, draw the frame — so this belongs on the play field itself
     * rather than floating above it. The frame goes down first and hides the 3D
     * view; the speaker sits inside it, and the speech and buttons below.
     */
    private fun drawDialogue(dialogue: DialogueScene) {
        dialogue.frame?.let { frame ->
            copy(
                from = frame,
                sourceLeft = 0,
                sourceTop = 0,
                width = DialogueScene.FRAME_WIDTH,
                height = DialogueScene.FRAME_HEIGHT,
                left = 0,
                top = 0,
            )
        }

        dialogue.portrait?.let { portrait ->
            copy(
                from = portrait.cps,
                sourceLeft = portrait.sourceLeft,
                sourceTop = portrait.sourceTop,
                width = portrait.goes.width,
                height = portrait.goes.height,
                left = portrait.goes.left,
                top = portrait.goes.top,
            )
        }

        // one box over everything below the frame, so the party's boxes and the
        // compass do not show through the words
        drawBox(
            left = 0,
            top = DialogueScene.FRAME_HEIGHT,
            width = WIDTH,
            height = HEIGHT - DialogueScene.FRAME_HEIGHT,
        )

        val font = font ?: return

        dialogue.lines.forEachIndexed { line, text ->
            write(
                text = text,
                font = font,
                left = DialogueScene.TEXT_LEFT,
                top = DialogueScene.TEXT_TOP + line * font.height,
                colour = TEXT_COLOUR,
            )
        }

        dialogue.buttons.forEachIndexed { index, button ->
            drawButton(button, font, highlighted = index == 0)
        }
    }

    /**
     * Both the strip a conversation sits on and the buttons on it are this same
     * box: a flat fill lit along the top and right and shaded along the left and
     * bottom, so it stands slightly proud of what is behind it.
     */
    private fun drawBox(left: Int, top: Int, width: Int, height: Int) {
        val right = left + width - 1
        val bottom = top + height - 1

        for (y in top + 1 until bottom) {
            for (x in left + 1 until right) {
                draw(x, y, palette.colors[FILL.value])
            }
        }

        for (x in left + 1..right) draw(x, top, palette.colors[EDGE_LIT.value])
        for (y in top until bottom) draw(right, y, palette.colors[EDGE_LIT.value])
        for (y in top..bottom) draw(left, y, palette.colors[EDGE_SHADED.value])
        for (x in left..right) draw(x, bottom, palette.colors[EDGE_SHADED.value])
    }

    private fun drawButton(button: DialogueScene.Button, font: Font, highlighted: Boolean) {
        drawBox(
            left = button.left,
            top = button.top,
            width = DialogueScene.Button.WIDTH,
            height = DialogueScene.Button.HEIGHT,
        )

        write(
            text = button.label,
            font = font,
            left = button.left + DialogueScene.Button.WIDTH / 2 - font.widthOf(button.label) / 2,
            top = button.top + BUTTON_LABEL_OFFSET_Y,
            colour = if (highlighted) BUTTON_LABEL_HIGHLIGHTED else BUTTON_LABEL_COLOUR,
        )
    }

    private fun write(
        text: String,
        font: Font,
        left: Int,
        top: Int,
        colour: PaletteIndex,
    ) {
        text.forEachIndexed { position, character ->
            val glyph = font.glyphFor(character) ?: return@forEachIndexed
            for (y in 0 until font.height) {
                for (x in 0 until font.width) {
                    if (!glyph.isInk(x, y)) continue
                    draw(left + position * font.width + x, top + y, palette.colors[colour.value])
                }
            }
        }
    }

    private fun copy(
        from: Cps,
        sourceLeft: Int,
        sourceTop: Int,
        width: Int,
        height: Int,
        left: Int,
        top: Int,
    ) {
        val colours = from.palette ?: palette

        for (y in 0 until height) {
            for (x in 0 until width) {
                val sourceX = sourceLeft + x
                val sourceY = sourceTop + y
                if (sourceX !in 0 until from.width || sourceY !in 0 until from.height) continue

                val index = from.pixels[sourceY * from.width + sourceX]
                draw(left + x, top + y, colours.colors[index.value])
            }
        }
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

        /** Dialogue colours. */
        private val FILL = PaletteIndex(183)
        private val EDGE_LIT = PaletteIndex(181)
        private val EDGE_SHADED = PaletteIndex(186)
        private val TEXT_COLOUR = PaletteIndex(15)
        private val BUTTON_LABEL_COLOUR = PaletteIndex(15)
        private val BUTTON_LABEL_HIGHLIGHTED = PaletteIndex(9)
        private const val BUTTON_LABEL_OFFSET_Y = 2

        /** Where the 3D view is copied into the frame. */
        const val VIEW_X = 0
        const val VIEW_Y = 0

        /**
         * Shape sources in DECORATE.CPS. The original cuts shapes out in
         * 8-pixel units, so the compass columns are 3 units = 24px wide and
         * start at `direction * 24`.
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
