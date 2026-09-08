package pl.pelotasplus.eyeofbeholder.data.model.sequence

import pl.pelotasplus.eyeofbeholder.data.model.Cps
import pl.pelotasplus.eyeofbeholder.data.model.Font
import pl.pelotasplus.eyeofbeholder.data.model.Palette
import pl.pelotasplus.eyeofbeholder.data.model.PaletteIndex
import pl.pelotasplus.eyeofbeholder.data.model.RGB

/**
 * The whole 320×200 screen, for a scene the game plays *over* everything
 * rather than inside the view.
 *
 * The dungeon is drawn into a window of the panel and everything else is that
 * panel; a scene is neither. It takes the screen, keeps it for as long as it
 * runs, and gives it back at the end — so it is its own surface rather than
 * another thing the play field can be asked to draw.
 *
 * ## A sheet is not a picture
 *
 * What a scene loads is a sheet, and only its top-left corner is the picture:
 * 304×128 of it, shown inset eight pixels down and eight in from the left,
 * which is where the black border round one of these scenes comes from. The
 * rest of the sheet is the scene's moving parts, packed below and beside the
 * picture — a figure in three poses, a mouth open and shut — and they reach
 * the screen by being copied out of it a rectangle at a time.
 *
 * So the sheet stays loaded and untouched beside what is being shown, and
 * serves as both: the frames come out of it, and the background that has to be
 * put back after one of them has been drawn over comes out of it too. Its
 * origin is the picture's origin, which is why putting a rectangle back reads
 * as fetching it from eight pixels up and to the left of where it goes — see
 * [restore].
 *
 * ## Colours are not pixels
 *
 * Pixels are kept as palette indices. The scenes are lit by moving the palette
 * rather than the pixels: the temple at the end is destroyed by fading through
 * a succession of colour tables over a picture that never changes, and a
 * screen holding colours could not do that.
 */
class SequenceScreen {

    private val shown = MutableList(WIDTH * HEIGHT) { NOTHING }

    /** The sheet as it was loaded: every frame the scene has, and its background. */
    private var sheet: List<PaletteIndex> = emptyList()

    /**
     * A second sheet kept open beside the first. The assault on the temple
     * draws from two at once — the mages from one and the temple from the
     * other — and a copy says which by whether it names anything at all.
     */
    private var alongside: List<PaletteIndex> = emptyList()

    /** The shapes cut from the sheets, by the number the commands call them. */
    private val shapes = mutableMapOf<Int, Shape>()

    /** A rectangle lifted off a sheet, drawn with its background left out. */
    private class Shape(val wide: Int, val deep: Int, val pixels: List<PaletteIndex>)

    /**
     * What the indices mean this instant. A sheet brings its own where it
     * carries one, and the fades that light it replace it without touching a
     * pixel.
     */
    var colours: Palette? = null
        private set

    /** Takes a sheet, without putting any of it on the screen. */
    fun load(picture: Cps) {
        sheet = picture.pixels
        picture.palette?.let { colours = it }
    }

    /** And a second sheet, for the scenes that draw from two at once. */
    fun loadAlongside(picture: Cps) {
        alongside = picture.pixels
    }

    /** And the colours on their own, for a fade or for a sheet carrying none. */
    fun light(palette: Palette) {
        colours = palette
    }

    /**
     * Cuts the sheet into the shapes a scene names, replacing whatever was cut
     * before under the same numbers. A shape's across and width are given in
     * columns of eight pixels, as the sheets are laid out.
     */
    fun cut(into: List<SequenceShape>) {
        into.forEach { shape ->
            val wide = shape.wide * A_COLUMN
            val left = shape.across * A_COLUMN
            shapes[shape.index] = Shape(
                wide = wide,
                deep = shape.deep,
                pixels = (0 until shape.deep).flatMap { y ->
                    (0 until wide).map { x ->
                        sheet.getOrElse((shape.down + y) * WIDTH + left + x) { NOTHING }
                    }
                },
            )
        }
    }

    /**
     * Does one of a scene's instructions, and calls [shown] at the moment the
     * screen has it.
     *
     * The moment matters for the one instruction that puts something up and
     * takes it down again: what it leaves behind is the background, so a
     * caller watching only the end of it would never see the thing at all.
     *
     * Whatever a command asks of the ear rather than the eye is not this
     * class's business, and passes through leaving the screen alone.
     */
    fun perform(command: SequenceCommand, shown: () -> Unit = {}) {
        when (command.what) {
            SequenceCommand.Copies -> {
                copyFromSheet(
                    sheetLeft = command.fromX * A_COLUMN,
                    sheetTop = command.fromY,
                    width = command.wide * A_COLUMN,
                    height = command.deep,
                    left = command.x,
                    top = command.y,
                    from = if (command.obj == 0) sheet else alongside,
                )
                shown()
            }

            SequenceCommand.Draws -> {
                draw(command.obj, command.x, command.y)
                shown()
            }

            SequenceCommand.DrawsAndRubsOut -> {
                val shape = shapes[command.obj]
                draw(command.obj, command.x, command.y)
                shown()
                shape?.let { restore(command.x, command.y, it.wide, it.deep) }
            }

            // A flash is a change of colour table and back, which nothing here
            // keeps: the picture under it is the same either way.
            SequenceCommand.FlashesTheColours, SequenceCommand.Sounds -> Unit
        }
    }

    /** One of the cut shapes, drawn with its background left out. */
    fun draw(shape: Int, left: Int, top: Int) {
        val it = shapes[shape] ?: return
        for (y in 0 until it.deep) {
            for (x in 0 until it.wide) {
                val colour = it.pixels[y * it.wide + x]
                if (colour != NOTHING) put(left + x, top + y, colour)
            }
        }
    }

    /** Puts the sheet's picture up in the window, which is how a scene is shown. */
    fun show() = copyFromSheet(
        sheetLeft = 0,
        sheetTop = 0,
        width = PICTURE_WIDTH,
        height = PICTURE_HEIGHT,
        left = PICTURE_LEFT,
        top = PICTURE_TOP,
    )

    /**
     * A rectangle of the sheet drawn onto the screen: one of the scene's
     * frames, put wherever it belongs.
     */
    fun copyFromSheet(
        sheetLeft: Int,
        sheetTop: Int,
        width: Int,
        height: Int,
        left: Int,
        top: Int,
        from: List<PaletteIndex> = sheet,
    ) {
        for (y in 0 until height) {
            for (x in 0 until width) {
                val at = (sheetTop + y) * WIDTH + (sheetLeft + x)
                from.getOrNull(at)?.let { put(left + x, top + y, it) }
            }
        }
    }

    /**
     * The screen written back into the sheet, so that what is on it now is
     * what a background restores to.
     *
     * One scene does this: it shows a corridor and then loads a sheet of
     * figures to walk down it, and the corridor has to survive the swap.
     */
    fun keepWhatIsShowing() {
        val kept = sheet.toMutableList()
        while (kept.size < WIDTH * HEIGHT) kept.add(NOTHING)
        for (y in 0 until PICTURE_HEIGHT) {
            for (x in 0 until PICTURE_WIDTH) {
                kept[y * WIDTH + x] = shown[(PICTURE_TOP + y) * WIDTH + PICTURE_LEFT + x]
            }
        }
        sheet = kept
    }

    /**
     * Whatever the picture had at that rectangle, put back over what has been
     * drawn there.
     *
     * The sheet's origin is the picture's origin and the picture sits eight
     * pixels in, so the rectangle is fetched from that much up and to the left
     * of where it is going.
     */
    fun restore(left: Int, top: Int, width: Int, height: Int) = copyFromSheet(
        sheetLeft = left - PICTURE_LEFT,
        sheetTop = top - PICTURE_TOP,
        width = width,
        height = height,
        left = left,
        top = top,
    )

    /**
     * Writes one line across the strip the scenes speak in, centred in it.
     *
     * The centring is by character rather than by pixel — the strip is
     * measured in whole characters and the line is placed in the middle of
     * them — so an odd number of characters sits half a character left of true
     * centre, as it does in the original. [row] is which line of a speech that
     * takes more than one.
     */
    fun write(line: String, font: Font, colour: PaletteIndex, row: Int = 0) {
        val left = (STRIP_LEFT + (STRIP_WIDTH - line.length) / 2) * A_CHARACTER_COLUMN
        val top = STRIP_TOP + row * (font.height + 1)

        line.forEachIndexed { position, character ->
            val glyph = font.glyphFor(character) ?: return@forEachIndexed
            for (y in 0 until font.height) {
                for (x in 0 until font.width) {
                    if (glyph.isInk(x, y)) {
                        put(left + position * font.width + x, top + y, colour)
                    }
                }
            }
        }
    }

    /**
     * And rubs the strip out again. It is below the picture rather than over
     * it, so what it goes back to is nothing rather than the scene.
     */
    fun clearTheStrip() {
        for (y in STRIP_TOP until HEIGHT) {
            for (x in 0 until WIDTH) put(x, y, NOTHING)
        }
    }

    /** The screen as rows of colour, for whatever is putting it on a display. */
    fun getRows(): List<List<RGB>> {
        val palette = colours
        return (0 until HEIGHT).map { y ->
            (0 until WIDTH).map { x ->
                palette?.colors?.getOrNull(shown[y * WIDTH + x].value) ?: BLACK
            }
        }
    }

    private fun put(x: Int, y: Int, colour: PaletteIndex) {
        if (x in 0 until WIDTH && y in 0 until HEIGHT) shown[y * WIDTH + x] = colour
    }

    companion object {
        const val WIDTH = 320
        const val HEIGHT = 200

        /**
         * The window a scene's picture is shown in, transcribed. Everything of
         * the sheet outside this corner is the scene's moving parts.
         */
        const val PICTURE_LEFT = 8
        const val PICTURE_TOP = 8
        const val PICTURE_WIDTH = 304
        const val PICTURE_HEIGHT = 128

        /**
         * The strip a scene's words are written across, transcribed: eight
         * pixels in from the left, thirty-eight characters wide, and starting a
         * hundred and fifty pixels down — well clear of the picture above it.
         */
        private const val STRIP_LEFT = 1
        private const val STRIP_WIDTH = 38
        private const val STRIP_TOP = 150

        /**
         * The sheets and the strip are both measured in these rather than in
         * pixels, across but not down.
         */
        private const val A_CHARACTER_COLUMN = 8
        private const val A_COLUMN = 8

        private val NOTHING = PaletteIndex(0)
        private val BLACK = RGB(0, 0, 0)
    }
}
