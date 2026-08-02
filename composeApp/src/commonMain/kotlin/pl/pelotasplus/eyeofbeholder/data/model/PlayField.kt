package pl.pelotasplus.eyeofbeholder.data.model

import kotlinx.serialization.Serializable

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
        messages: List<Message> = emptyList(),
        party: List<Champion> = emptyList(),
        portraits: Cps? = null,
    ): PlayField {
        drawBackground()
        drawParty(party, portraits)
        drawViewPort(viewPort)
        drawCompass(direction)
        drawMessages(messages)
        dialogue?.let(::drawDialogue)
        return this
    }

    /**
     * A box down the right for each champion, and nothing at all for a slot
     * nobody fills.
     *
     * An empty slot is not an empty box: the original draws a slot only if
     * somebody is in it, so a party of four leaves the bottom of the panel as
     * bare wall.
     */
    private fun drawParty(party: List<Champion>, portraits: Cps?) {
        championBoxes.forEachIndexed { slot, box ->
            val champion = party.getOrNull(slot)?.takeIf { it.inTheParty } ?: return@forEachIndexed

            copy(
                from = background,
                sourceLeft = boxInTheArt.left,
                sourceTop = boxInTheArt.top,
                width = ChampionBox.WIDTH,
                height = ChampionBox.HEIGHT,
                left = box.left,
                top = box.top,
            )
            drawChampion(champion, box, portraits)
        }
    }

    private fun drawChampion(champion: Champion, box: ChampionBox, portraits: Cps?) {
        portraits?.let { sheet ->
            val face = sheet.portrait(champion.portrait)
            val colours = sheet.palette ?: palette
            for (y in 0 until face.h) {
                for (x in 0 until face.w) {
                    val index = face.pixels[y * face.w + x]
                    draw(box.portraitLeft + x, box.portraitTop + y, colours.colors[index.value])
                }
            }
        }

        font?.let { font ->
            write(
                text = champion.name,
                font = font,
                left = box.nameLeft,
                top = box.nameTop,
                colour = if (champion.inTrouble) NAME_IN_TROUBLE else NAME_COLOUR,
            )
        }

        drawHitPointBar(champion, box)
    }

    /** The hit point bar, sunk into the strip it sits on, with HP written beside it. */
    private fun drawHitPointBar(champion: Champion, box: ChampionBox) {
        val bar = hitPointBar(champion.hitPoints)

        drawBox(
            left = box.barLeft - 1,
            top = box.barTop - 1,
            width = ChampionBox.BAR_WIDTH + 2,
            height = ChampionBox.BAR_HEIGHT + 2,
            topRight = EDGE_SHADED,
            bottomLeft = EDGE_LIT,
            fill = null,
        )

        for (y in 0 until ChampionBox.BAR_HEIGHT) {
            for (x in 0 until ChampionBox.BAR_WIDTH) {
                val ink = if (x < bar.filled) bar.colour else BAR_EMPTY
                draw(box.barLeft + x, box.barTop + y, palette.colors[ink.value])
            }
        }

        font?.let { font ->
            write(
                text = ChampionBox.BAR_LABEL,
                font = font,
                left = box.barLabelLeft,
                top = box.barLabelTop,
                colour = NAME_COLOUR,
            )
        }
    }

    /**
     * The bar along the bottom, beside the camp button, where a script writes
     * when it has no dialogue box open.
     *
     * Nothing takes a line off it. It is a fixed height with a fixed font, so
     * once full it scrolls: the newest line is at the bottom and the oldest
     * falls off the top. Each line keeps the colour it was written in, since
     * what scrolls is the pixels.
     */
    private fun drawMessages(messages: List<Message>) {
        val font = font ?: return

        messages
            .flatMap { message -> font.wrap(message.text, MESSAGE_WIDTH).map { it to message.colour } }
            .takeLast(MESSAGE_HEIGHT / font.height)
            .forEachIndexed { line, (text, colour) ->
                write(
                    text = text,
                    font = font,
                    left = MESSAGE_LEFT,
                    top = MESSAGE_TOP + line * font.height,
                    colour = colour,
                )
            }
    }

    /** A line on the bar along the bottom, in the colour the script asked for. */
    @Serializable
    data class Message(val text: String, val colour: PaletteIndex)

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
     * The one box the interface is made of: two edges in one colour, two in
     * the other, and an optional flat fill.
     *
     * Lit along the top and right it stands proud of what is behind it, which
     * is how a conversation strip and its buttons are drawn. Handed the two
     * colours the other way round it becomes a channel cut into the panel,
     * which is how the hit point bars sit in their strip.
     */
    private fun drawBox(
        left: Int,
        top: Int,
        width: Int,
        height: Int,
        topRight: PaletteIndex = EDGE_LIT,
        bottomLeft: PaletteIndex = EDGE_SHADED,
        fill: PaletteIndex? = FILL,
    ) {
        val right = left + width - 1
        val bottom = top + height - 1

        if (fill != null) {
            for (y in top + 1 until bottom) {
                for (x in left + 1 until right) {
                    draw(x, y, palette.colors[fill.value])
                }
            }
        }

        for (x in left + 1..right) draw(x, top, palette.colors[topRight.value])
        for (y in top until bottom) draw(right, y, palette.colors[topRight.value])
        for (y in top..bottom) draw(left, y, palette.colors[bottomLeft.value])
        for (x in left..right) draw(x, bottom, palette.colors[bottomLeft.value])
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

        /** Party panel colours. */
        private val NAME_COLOUR = PaletteIndex(12)
        private val NAME_IN_TROUBLE = PaletteIndex(8)
        private val BAR_EMPTY = PaletteIndex(184)

        /**
         * The message line along the bottom, beside the camp button. The band
         * itself is painted into the play field art; only the words are drawn.
         */
        private const val MESSAGE_LEFT = 8
        private const val MESSAGE_TOP = 180
        private const val MESSAGE_WIDTH = 272
        private const val MESSAGE_HEIGHT = 18

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
