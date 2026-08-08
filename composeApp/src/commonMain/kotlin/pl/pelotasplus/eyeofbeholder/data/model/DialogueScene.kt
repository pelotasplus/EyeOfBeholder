package pl.pelotasplus.eyeofbeholder.data.model

/**
 * A conversation drawn over the play field: whoever is speaking, framed, what
 * they say underneath, and a button per answer.
 *
 * Every coordinate here is the original game's rather than something to tune by
 * eye. The frame being 184 wide is why the party's side of the screen survives a
 * conversation.
 */
data class DialogueScene(
    val frame: Cps?,
    val portrait: Picture?,
    val lines: List<String>,
    val buttons: List<Button>,
    val readOff: ReadOff = ReadOff.TheStripBelow,
) {
    /**
     * What a scene is read off, which is where its words go and what is
     * cleared behind them.
     */
    sealed interface ReadOff {

        /**
         * Somewhere words are written: a panel cleared to the interface's own
         * colours, the corner the writing starts in, and the corner the button
         * that reads it on sits in. All of the original's.
         */
        sealed class Written(
            val panelLeft: Int,
            val panelTop: Int,
            val panelWidth: Int,
            val panelHeight: Int,
            val textLeft: Int,
            val textTop: Int,
            val textWidth: Int,
            val readOnLeft: Int,
            val readOnTop: Int,
        ) : ReadOff

        /**
         * The strip along the bottom, under the frame a speaker is drawn in,
         * which is where a script speaks and where it puts its answers.
         */
        data object TheStripBelow : Written(
            panelLeft = 0,
            panelTop = FRAME_HEIGHT,
            panelWidth = 320,
            panelHeight = 200 - FRAME_HEIGHT,
            textLeft = 8,
            textTop = 125,
            textWidth = 304,
            readOnLeft = 221,
            readOnTop = 189,
        )

        /**
         * A page held up over the view, which is how something carried is
         * read. It covers the dungeon and leaves the party's side of the
         * screen alone, so a note is read without losing sight of who is
         * carrying it.
         */
        data object APageOverTheView : Written(
            panelLeft = 0,
            panelTop = 0,
            panelWidth = 176,
            panelHeight = 175,
            textLeft = 8,
            textTop = 4,
            textWidth = 160,
            readOnLeft = 76,
            readOnTop = 162,
        )

        /**
         * A picture in the frame a speaker would be in, with nothing written
         * and nothing to press: a map is looked at rather than read, and any
         * click puts it away.
         */
        data object APictureAlone : ReadOff
    }

    /** Part of a .CPS put on screen, in the play field's own coordinates. */
    data class Picture(
        val cps: Cps,
        val sourceLeft: Int,
        val sourceTop: Int,
        val goes: PictureFrame,
    )

    /**
     * The two places a script can put a picture, and how much of the file each
     * one takes. Both are the original's.
     *
     * A script names one of these per picture: whoever is speaking goes in the
     * box inset in the dialogue frame, while a plate — the temple seen from the
     * gate, a map — spans the screen above the speech instead of the frame.
     */
    enum class PictureFrame(
        val left: Int,
        val top: Int,
        val width: Int,
        val height: Int,
    ) {
        SPEAKER(left = 8, top = 8, width = 160, height = 96),
        ACROSS_THE_TOP(left = 0, top = 0, width = 320, height = 121);

        /** Nothing is drawn under a picture that covers where the frame goes. */
        val insteadOfTheFrame: Boolean get() = width > FRAME_WIDTH

        companion object {
            fun of(rect: Int) = entries.getOrElse(rect) { SPEAKER }
        }
    }

    data class Button(
        val label: String,
        val left: Int,
        val top: Int,
    ) {
        fun contains(x: Int, y: Int): Boolean =
            x in left until left + WIDTH && y in top until top + HEIGHT

        companion object {
            const val WIDTH = 95
            const val HEIGHT = 9
        }
    }

    companion object {
        /** Where the frame and the speaker go. */
        const val FRAME_WIDTH = 184
        const val FRAME_HEIGHT = 121
        const val PORTRAIT_LEFT = 8
        const val PORTRAIT_TOP = 8

        /**
         * Wraps the speech and puts the answers a line below the last one
         * written — except the single button a speech is read on, which has a
         * corner of its own and does not move with the text. It is the same
         * button whether it turns the page or ends the speech.
         *
         * [frame] is the box a speaker sits in, so it goes down only when there
         * is someone to put in it and nothing already covers it. A script can
         * ask a question while drawing nobody, and then there is no box: an
         * empty one hides the view and says nothing, while the words and the
         * answers below it are the whole of what is being asked.
         */
        fun layout(
            frame: Cps?,
            portrait: Picture?,
            text: String,
            buttonLabels: List<String>,
            font: Font,
            waitsToBeRead: Boolean = false,
            readOff: ReadOff.Written = ReadOff.TheStripBelow,
        ): DialogueScene {
            val lines = font.wrap(text, readOff.textWidth)
            val buttonTop = (lines.size + 1) * font.height + readOff.textTop + 4
            val buttonLeft =
                if (buttonLabels.size > TWO_ACROSS.size) THREE_ACROSS else TWO_ACROSS

            return DialogueScene(
                frame = frame.takeIf { portrait != null && !portrait.goes.insteadOfTheFrame },
                portrait = portrait,
                lines = lines,
                buttons = buttonLabels.mapIndexed { index, label ->
                    Button(
                        label = label.uppercase(),
                        left = if (waitsToBeRead) readOff.readOnLeft else {
                            buttonLeft.getOrElse(index) { buttonLeft.last() }
                        },
                        top = if (waitsToBeRead) readOff.readOnTop else buttonTop,
                    )
                },
                readOff = readOff,
            )
        }

        /** A map, in the frame and with nothing written: it is only looked at. */
        fun aPicture(frame: Cps?, picture: Picture) = DialogueScene(
            frame = frame,
            portrait = picture,
            lines = emptyList(),
            buttons = emptyList(),
            readOff = ReadOff.APictureAlone,
        )

        /**
         * Where the answers go, both rows from the original: a pair sits inset,
         * three spread across the full width. Asking three questions with the
         * pair's positions puts the third on top of the second.
         */
        private val TWO_ACROSS = listOf(59, 166)
        private val THREE_ACROSS = listOf(4, 112, 220)

        /**
         * The word that turns a page part way through a speech. The button it
         * is on sits apart from the answers, in a corner of its own, wherever
         * the speech happens to end.
         *
         * The original reads both of these words from its own executable, so
         * they are English here and would be the language of the copy there.
         */
        const val MORE = "more"

        /** And the word that closes one there is no more of. */
        const val OK = "ok"
    }
}
