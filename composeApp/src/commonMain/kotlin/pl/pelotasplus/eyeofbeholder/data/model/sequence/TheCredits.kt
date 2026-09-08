package pl.pelotasplus.eyeofbeholder.data.model.sequence

/**
 * The names, scrolled up the picture the ending left on screen.
 *
 * A scene of its own rather than the tail of the ending: it changes the music,
 * opens two sheets the ending never shows, and is built the other way round —
 * everything else in these scenes is rectangles copied off a sheet, and this
 * is a list read out of a file and moved a pixel at a time.
 *
 * ## What the file holds
 *
 * `CREDITS.TXT` is one long byte stream of lines ended by a carriage return.
 * Most are a line of names, centred. Two markers change that, and both come
 * first in the line: one means the next byte names a picture to show instead
 * of any words — that is how the titles at the front arrive — and the other
 * means the rest of the line is set in the smaller of the two fonts.
 *
 * An empty line is a gap, which is most of what gives the roll its spacing:
 * the file opens with a run of fourteen of them, carrying the first title up
 * the screen before anything follows it.
 */
object TheCredits {

    /** The sheets the titles are cut out of. Neither is ever shown whole. */
    const val TITLES = 10
    const val MORE_TITLES = 9

    /** The bank and tune the names are read to, which is not the ending's. */
    const val BANK = "FINALE2"
    const val THE_TUNE = 1

    /**
     * The colours the roll is read under, which are not the ones the ending
     * opened in.
     *
     * Neither sheet the titles are cut from carries a palette of its own, so
     * they take whatever the screen is lit by — and by the time the names
     * start, the ending has faded the screen to this table over the course of
     * the temple's destruction and nothing puts it back. Read under the
     * opening table instead, the gold of the titles comes out speckled red and
     * white.
     */
    val COLOURS: String get() = TheFinale.COLOURS[WHAT_THE_ENDING_FADES_TO_LAST]

    private const val WHAT_THE_ENDING_FADES_TO_LAST = 6

    /** Where the roll happens: the same window the ending's pictures are in. */
    const val LEFT = 8
    const val TOP = 8
    const val WIDTH = 304
    const val HEIGHT = 128

    /** How far everything moves each frame, and how long a frame lasts. */
    const val A_PIXEL = 1
    const val FRAME_TICKS = 1

    /** How long the last picture is left standing before the game goes on. */
    const val HELD_AT_THE_END = 90

    /** The names, and the shadow laid a pixel up and left of them. */
    const val INK = 240
    const val SHADOW = 12

    /** How tall the two fonts count as when the roll is spaced out. */
    const val SMALL_ENOUGH = 6
    const val ORDINARY = 8

    /** One line of the file: some names, or one of the titles. */
    sealed interface Line {
        data class Words(val words: String, val small: Boolean) : Line
        data class Picture(val shape: Int) : Line
    }

    /**
     * The file read into its lines, in order. Nothing here knows how tall a
     * picture is — that belongs to whoever cut it — so nothing here places
     * them; see [stackedUnder].
     */
    fun read(file: UByteArray): List<Line> {
        val lines = mutableListOf<Line>()
        var at = 0

        while (at < file.size) {
            var end = at
            while (end < file.size && file[end] != A_LINE_END) end++

            when {
                file[at] == A_SHAPE && at + 1 < file.size ->
                    // The byte after the marker numbers the shape from one,
                    // where everything else counts from zero.
                    lines += Line.Picture(file[at + 1].toInt() - 1)

                file[at] == SMALL ->
                    lines += Line.Words(text(file, at + 1, end), small = true)

                else -> lines += Line.Words(text(file, at, end), small = false)
            }

            at = end + 1
        }

        return lines
    }

    /** How tall a line counts as, which decides both spacing and when it is done. */
    fun deepOf(line: Line, heightOf: (shape: Int) -> Int): Int = when (line) {
        is Line.Picture -> heightOf(line.shape)
        is Line.Words -> if (line.small) SMALL_ENOUGH else ORDINARY
    }

    /**
     * Where each line starts out, measured down from the top of the window.
     *
     * The first sits on the bottom edge and the rest stack below it, each a
     * quarter of its own height clear of the one before. They are placed once
     * and then moved together, so the gap between two names never changes
     * however long the roll runs.
     */
    fun stackedUnder(lines: List<Line>, heightOf: (shape: Int) -> Int): List<Int> {
        var y = HEIGHT
        return lines.map {
            val here = y
            val deep = deepOf(it, heightOf)
            y += deep + deep / 4
            here
        }
    }

    /**
     * How far the roll travels before it stops.
     *
     * Not until everything has gone by: the file ends on the publisher's mark,
     * and the roll stops with it standing in the middle of the window and
     * holds it there — that picture is the last thing the game shows, so
     * letting it climb out of sight would end on an empty sky.
     */
    fun risesUntil(lines: List<Line>, heightOf: (shape: Int) -> Int): Int {
        val from = stackedUnder(lines, heightOf)
        val last = lines.indexOfLast { it is Line.Picture }
        if (last < 0) return from.last() + deepOf(lines.last(), heightOf)

        val middle = (HEIGHT - deepOf(lines[last], heightOf)) / 2
        return from[last] - middle
    }

    /** Where a line of words sits across the window, which is the middle of it. */
    fun acrossFor(words: Line.Words): Int =
        ((WIDTH - words.words.length * deepOf(words) { 0 }) / 2) + 1

    /** And a picture, by its own width rather than by a count of characters. */
    fun acrossFor(wide: Int): Int = (WIDTH - wide) / 2

    private fun text(file: UByteArray, from: Int, until: Int): String =
        buildString { for (i in from until until) append(file[i].toInt().toChar()) }.trimEnd()

    private val A_LINE_END: UByte = 0x0Du
    private val A_SHAPE: UByte = 0x02u
    private val SMALL: UByte = 0x01u
}
