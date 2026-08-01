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
) {
    /** Part of a .CPS put on screen, in the play field's own coordinates. */
    data class Picture(
        val cps: Cps,
        val sourceLeft: Int,
        val sourceTop: Int,
        val width: Int,
        val height: Int,
        val left: Int,
        val top: Int,
    )

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

        /** The strip the speech is written into. */
        const val TEXT_LEFT = 8
        const val TEXT_TOP = 125
        const val TEXT_WIDTH = 304

        /**
         * Wraps the speech and puts the answers a line below the last one
         * written — except the single button a speech is read on, which has a
         * corner of its own and does not move with the text. It is the same
         * button whether it turns the page or ends the speech.
         */
        fun layout(
            frame: Cps?,
            portrait: Picture?,
            text: String,
            buttonLabels: List<String>,
            font: Font,
            waitsToBeRead: Boolean = false,
        ): DialogueScene {
            val lines = font.wrap(text, TEXT_WIDTH)
            val buttonTop = (lines.size + 1) * font.height + TEXT_TOP + 4
            val buttonLeft =
                if (buttonLabels.size > TWO_ACROSS.size) THREE_ACROSS else TWO_ACROSS

            return DialogueScene(
                frame = frame,
                portrait = portrait,
                lines = lines,
                buttons = buttonLabels.mapIndexed { index, label ->
                    Button(
                        label = label.uppercase(),
                        left = if (waitsToBeRead) READ_ON_LEFT else {
                            buttonLeft.getOrElse(index) { buttonLeft.last() }
                        },
                        top = if (waitsToBeRead) READ_ON_TOP else buttonTop,
                    )
                },
            )
        }

        /**
         * Where the answers go, both rows from the original: a pair sits inset,
         * three spread across the full width. Asking three questions with the
         * pair's positions puts the third on top of the second.
         */
        private val TWO_ACROSS = listOf(59, 166)
        private val THREE_ACROSS = listOf(4, 112, 220)

        /**
         * The corner a speech is read on, and the word that turns a page part
         * way through one. Both are the original's: the button that reads on
         * sits apart from the answers, in the bottom right, wherever the
         * speech happens to end.
         */
        const val MORE = "more"
        private const val READ_ON_LEFT = 221
        private const val READ_ON_TOP = 189
    }
}
