package pl.pelotasplus.eyeofbeholder.data.model

/**
 * The strip along the bottom, and what has been written into it so far.
 *
 * A script does not put a speech on the screen; it writes into a box that is
 * already there, and the box is emptied only by being drawn again. Between
 * two draws a whole conversation can go in — a speech, the party's reply, an
 * answer to that — and what the reader sees is the box scrolling.
 *
 * Scrolling is the whole of it. The box only ever moves forward: what is
 * said next is written on from where the reading stopped, never over the top
 * of what has been read. Forgetting that is how the ninth floor's farewell
 * came to show its end, then its beginning, then its end again.
 */
class DialogueBox(
    private val font: Font,
    private val readOff: DialogueScene.ReadOff.Written = DialogueScene.ReadOff.TheStripBelow,
) {
    private var standing: String = ""
    private var read: Int = 0

    /** What is on the screen, and the boxfuls behind it still to be read. */
    data class Shown(val onScreen: String, val stillToRead: List<String>)

    /** The box drawn again, which is the one thing that empties it. */
    fun drawnAgain() {
        standing = ""
        read = 0
    }

    /**
     * [words] written into the box, and what that comes to on the screen.
     *
     * [canBeReadOff] is whether there is a button to press. With one, the
     * reader is stopped at each boxful and the rest wait behind it. With
     * none, nothing may wait — the box has scrolled and shows the end of
     * what was said, because a speech held back for a button that does not
     * exist is a game that stops.
     */
    fun said(words: String, canBeReadOff: Boolean): Shown {
        val whole = listOf(standing, words).filter { it.isNotEmpty() }.joinToString("\n")
        val boxfuls = DialogueScene.boxfulsLeft(whole, font, readOff, read)

        val onScreen = if (canBeReadOff) boxfuls.first() else boxfuls.last()
        return Shown(onScreen, if (canBeReadOff) boxfuls.drop(1) else emptyList()).also {
            nowShowing(onScreen)
        }
    }

    /** The reader has turned to [page], which is now the whole of what stands there. */
    fun turnedTo(page: String) = nowShowing(page)

    private fun nowShowing(page: String) {
        standing = page
        read = DialogueScene.linesOf(page, font, readOff)
    }
}
