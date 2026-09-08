package pl.pelotasplus.eyeofbeholder.data.model.sequence

/**
 * How the ending moves: twenty-one short lists of instructions, and the shapes
 * five of its sheets are cut into.
 *
 * These are data, not a formula. Nothing here can be worked out from anything
 * else — not from the sheets, not from the level files — so there is no way to
 * check a number by reasoning about it, and a digit changed by eye moves a
 * figure to the wrong part of the room with nothing to say it has. The frozen
 * frames of the ending are what would notice.
 */
object FinaleFrames {

    private fun c(
        what: Int,
        obj: Int = 0,
        x: Int = 0,
        y: Int = 0,
        delay: Int = 0,
        palette: Int = 0,
        fromX: Int = 0,
        fromY: Int = 0,
        wide: Int = 0,
        deep: Int = 0,
    ) = SequenceCommand(what, obj, x, y, delay, palette, fromX, fromY, wide, deep)

    private const val FLASH = SequenceCommand.FlashesTheColours
    private const val RUB = SequenceCommand.DrawsAndRubsOut
    private const val DRAW = SequenceCommand.Draws
    private const val COPY = SequenceCommand.Copies
    private const val SOUND = SequenceCommand.Sounds

    /** The twenty-one lists, by the number the sequence asks for them by. */
    val MOVES: List<List<SequenceCommand>> = listOf(
        // 0 — the dragon's last breath, with its head turning
        listOf(
            c(SOUND, obj = 4),
            c(COPY, x = 136, y = 8, delay = 8, fromX = 5, fromY = 136, wide = 11, deep = 48),
            c(DRAW, obj = 1, x = 136, y = 8),
            c(COPY, x = 80, y = 8, fromX = 0, fromY = 136, wide = 5, deep = 40),
            c(SOUND, obj = 5),
            c(COPY, x = 232, y = 88, delay = 4, fromX = 0, fromY = 88, wide = 8, deep = 48),
            c(DRAW, obj = 3, x = 80, y = 8),
            c(COPY, x = 232, y = 88, delay = 4, fromX = 8, fromY = 88, wide = 8, deep = 48),
            c(COPY, x = 232, y = 88, delay = 4, fromX = 16, fromY = 88, wide = 8, deep = 48),
            c(DRAW, obj = 2, x = 232, y = 88, delay = 4),
        ),
        // 1 — the same, slower and without the second head
        listOf(
            c(SOUND, obj = 4),
            c(COPY, x = 136, y = 8, delay = 12, fromX = 5, fromY = 136, wide = 11, deep = 48),
            c(DRAW, obj = 1, x = 136, y = 8),
            c(SOUND, obj = 5),
            c(COPY, x = 232, y = 88, delay = 6, fromX = 0, fromY = 88, wide = 8, deep = 48),
            c(COPY, x = 232, y = 88, delay = 6, fromX = 8, fromY = 88, wide = 8, deep = 48),
            c(COPY, x = 232, y = 88, delay = 6, fromX = 16, fromY = 88, wide = 8, deep = 48),
            c(DRAW, obj = 2, x = 232, y = 88, delay = 6),
        ),
        // 2 — an eye blinking in the corner, three frames
        listOf(
            c(COPY, x = 232, y = 112, delay = 6, fromX = 24, fromY = 176, wide = 3, deep = 16),
            c(COPY, x = 232, y = 112, delay = 6, fromX = 27, fromY = 176, wide = 3, deep = 16),
            c(COPY, x = 232, y = 112, delay = 6, fromX = 30, fromY = 176, wide = 3, deep = 16),
        ),
        // 3 — the eye again, with a shape put back over the corner first
        listOf(
            c(COPY, x = 80, y = 8, fromX = 0, fromY = 136, wide = 5, deep = 40),
            c(COPY, x = 232, y = 112, delay = 3, fromX = 24, fromY = 176, wide = 3, deep = 16),
            c(DRAW, obj = 3, x = 80, y = 8, delay = 3),
            c(COPY, x = 232, y = 112, delay = 3, fromX = 27, fromY = 176, wide = 3, deep = 16),
            c(COPY, x = 232, y = 112, delay = 3, fromX = 30, fromY = 176, wide = 3, deep = 16),
        ),
        // 4 — the one who arrives, walking in over six frames
        listOf(
            c(SOUND, obj = 6),
            c(COPY, x = 104, y = 40, delay = 3, fromX = 32, fromY = 88, wide = 8, deep = 88),
            c(COPY, x = 104, y = 40, delay = 3, fromX = 32, fromY = 0, wide = 8, deep = 88),
            c(COPY, x = 104, y = 40, delay = 3, fromX = 24, fromY = 0, wide = 8, deep = 88),
            c(COPY, x = 104, y = 40, delay = 3, fromX = 16, fromY = 0, wide = 8, deep = 88),
            c(COPY, x = 104, y = 40, delay = 3, fromX = 0, fromY = 0, wide = 8, deep = 88),
            c(COPY, x = 104, y = 40, delay = 3, fromX = 24, fromY = 88, wide = 8, deep = 88),
        ),
        // 5 — him talking: the eye behind him, and his mouth
        listOf(
            c(COPY, x = 232, y = 112, fromX = 24, fromY = 176, wide = 3, deep = 16),
            c(COPY, x = 128, y = 40, delay = 4, fromX = 33, fromY = 176, wide = 2, deep = 16),
            c(COPY, x = 232, y = 112, fromX = 27, fromY = 176, wide = 3, deep = 16),
            c(COPY, x = 128, y = 40, delay = 4, fromX = 35, fromY = 176, wide = 2, deep = 16),
            c(COPY, x = 232, y = 112, fromX = 30, fromY = 176, wide = 3, deep = 16),
            c(COPY, x = 128, y = 40, delay = 4, fromX = 37, fromY = 176, wide = 2, deep = 16),
        ),
        // 6 — he turns
        listOf(
            c(COPY, x = 104, y = 40, fromX = 16, fromY = 136, wide = 8, deep = 48),
        ),
        // 7 — the party following him down the corridor, five frames
        listOf(
            c(COPY, x = 208, y = 80, delay = 4, fromX = 0, fromY = 128, wide = 6, deep = 56),
            c(COPY, x = 208, y = 80, delay = 4, fromX = 6, fromY = 128, wide = 6, deep = 56),
            c(COPY, x = 208, y = 80, delay = 4, fromX = 12, fromY = 128, wide = 6, deep = 56),
            c(COPY, x = 208, y = 80, delay = 4, fromX = 18, fromY = 128, wide = 6, deep = 56),
            c(COPY, x = 208, y = 80, delay = 4, fromX = 24, fromY = 128, wide = 6, deep = 56),
        ),
        // 8 — and his mouth as he calls them on
        listOf(
            c(COPY, x = 224, y = 56, delay = 3, fromX = 0, fromY = 184, wide = 4, deep = 16),
            c(COPY, x = 224, y = 56, fromX = 4, fromY = 184, wide = 4, deep = 16),
        ),
        // 9 — a shape crossing the screen, a step at a time
        listOf(
            c(RUB, obj = 1, x = -10, y = 40, delay = 2),
            c(RUB, obj = 1, x = 0, y = 40, delay = 2),
            c(RUB, obj = 1, x = 10, y = 40, delay = 2),
            c(RUB, obj = 1, x = 20, y = 40, delay = 2),
            c(RUB, obj = 1, x = 30, y = 40, delay = 2),
            c(RUB, obj = 1, x = 40, y = 40, delay = 2),
            c(DRAW, obj = 2, x = 48, y = 40, delay = 2),
        ),
        // 10 — the mages raising their arms, and the first bolt leaving them
        listOf(
            c(COPY, obj = 1, x = 8, y = 40, fromX = 32, fromY = 0, wide = 8, deep = 80),
            c(COPY, obj = 1, x = 280, y = 96, fromX = 24, fromY = 160, wide = 4, deep = 40),
            c(COPY, obj = 1, x = 96, y = 96, delay = 3, fromX = 12, fromY = 80, wide = 1, deep = 16),
            c(COPY, obj = 1, x = 8, y = 40, fromX = 32, fromY = 80, wide = 8, deep = 80),
            c(COPY, obj = 1, x = 280, y = 96, fromX = 28, fromY = 160, wide = 4, deep = 40),
            c(COPY, obj = 1, x = 96, y = 96, delay = 3, fromX = 13, fromY = 80, wide = 1, deep = 16),
            c(COPY, obj = 1, x = 8, y = 40, fromX = 24, fromY = 80, wide = 8, deep = 80),
            c(COPY, obj = 1, x = 280, y = 96, fromX = 32, fromY = 160, wide = 4, deep = 40),
            c(COPY, obj = 1, x = 96, y = 96, delay = 3, fromX = 14, fromY = 80, wide = 1, deep = 16),
            c(SOUND, obj = 8),
            c(COPY, obj = 1, x = 40, y = 32, fromX = 0, fromY = 40, wide = 16, deep = 40),
            c(COPY, obj = 1, x = 248, y = 88, fromX = 12, fromY = 176, wide = 6, deep = 24),
            c(COPY, obj = 1, x = 96, y = 80, delay = 3, fromX = 0, fromY = 128, wide = 12, deep = 24),
        ),
        // 11 — the temple standing while they work
        listOf(
            c(COPY, obj = 1, x = 40, y = 32, fromX = 16, fromY = 40, wide = 16, deep = 40),
            c(COPY, obj = 1, x = 248, y = 88, fromX = 18, fromY = 176, wide = 6, deep = 24),
            c(COPY, obj = 1, x = 96, y = 80, fromX = 0, fromY = 152, wide = 12, deep = 24),
            c(COPY, obj = 1, x = 8, y = 40, fromX = 24, fromY = 80, wide = 8, deep = 80),
            c(COPY, obj = 1, x = 280, y = 96, fromX = 32, fromY = 160, wide = 4, deep = 40),
            c(COPY, obj = 1, x = 96, y = 96, fromX = 14, fromY = 80, wide = 1, deep = 16),
            c(COPY, x = 168, y = 16, fromX = 32, fromY = 0, wide = 8, deep = 80),
            c(COPY, x = 232, y = 16, fromX = 32, fromY = 80, wide = 8, deep = 80),
            c(COPY, obj = 1, x = 8, y = 40, fromX = 24, fromY = 80, wide = 8, deep = 80),
            c(COPY, obj = 1, x = 280, y = 96, fromX = 32, fromY = 160, wide = 4, deep = 40),
            c(COPY, obj = 1, x = 96, y = 96, delay = 3, fromX = 14, fromY = 80, wide = 1, deep = 16),
            c(COPY, obj = 1, x = 8, y = 40, fromX = 32, fromY = 0, wide = 8, deep = 80),
            c(COPY, obj = 1, x = 280, y = 96, fromX = 24, fromY = 160, wide = 4, deep = 40),
            c(COPY, obj = 1, x = 96, y = 96, delay = 3, fromX = 12, fromY = 80, wide = 1, deep = 16),
            c(COPY, obj = 1, x = 8, y = 40, fromX = 16, fromY = 80, wide = 8, deep = 80),
            c(COPY, obj = 1, x = 280, y = 96, fromX = 36, fromY = 160, wide = 4, deep = 40),
            c(COPY, obj = 1, x = 96, y = 96, delay = 3, fromX = 15, fromY = 80, wide = 1, deep = 16),
        ),
        // 12 — the temple beginning to come apart
        listOf(
            c(COPY, obj = 1, x = 40, y = 32, fromX = 16, fromY = 40, wide = 16, deep = 40),
            c(COPY, obj = 1, x = 248, y = 88, fromX = 18, fromY = 176, wide = 6, deep = 24),
            c(COPY, obj = 1, x = 96, y = 80, fromX = 0, fromY = 152, wide = 12, deep = 24),
            c(COPY, obj = 1, x = 8, y = 40, fromX = 24, fromY = 80, wide = 8, deep = 80),
            c(COPY, obj = 1, x = 280, y = 96, fromX = 32, fromY = 160, wide = 4, deep = 40),
            c(COPY, obj = 1, x = 96, y = 96, fromX = 14, fromY = 80, wide = 1, deep = 16),
            c(COPY, x = 168, y = 16, fromX = 32, fromY = 0, wide = 8, deep = 80),
            c(COPY, x = 232, y = 16, fromX = 32, fromY = 80, wide = 8, deep = 80),
            c(SOUND, obj = 15),
            c(DRAW, obj = 2, x = 168, y = 16),
            c(COPY, obj = 1, x = 8, y = 40, fromX = 32, fromY = 0, wide = 8, deep = 80),
            c(COPY, obj = 1, x = 280, y = 96, fromX = 24, fromY = 160, wide = 4, deep = 40),
            c(COPY, obj = 1, x = 96, y = 96, delay = 3, fromX = 12, fromY = 80, wide = 1, deep = 16),
            c(COPY, x = 168, y = 16, fromX = 32, fromY = 0, wide = 8, deep = 80),
            c(COPY, x = 232, y = 16, fromX = 32, fromY = 80, wide = 8, deep = 80),
            c(SOUND, obj = 15),
            c(DRAW, obj = 3, x = 168, y = 16),
            c(COPY, obj = 1, x = 8, y = 40, fromX = 16, fromY = 80, wide = 8, deep = 80),
            c(COPY, obj = 1, x = 280, y = 96, fromX = 36, fromY = 160, wide = 4, deep = 40),
            c(COPY, obj = 1, x = 96, y = 96, delay = 3, fromX = 15, fromY = 80, wide = 1, deep = 16),
            c(COPY, x = 168, y = 16, fromX = 32, fromY = 0, wide = 8, deep = 80),
            c(COPY, x = 232, y = 16, fromX = 32, fromY = 80, wide = 8, deep = 80),
        ),
        // 13 — bolts landing on it, three rounds
        listOf(
            c(SOUND, obj = 15),
            c(DRAW, obj = 1, x = 168, y = 16),
            c(SOUND, obj = 9),
            c(COPY, obj = 1, x = 40, y = 32, fromX = 0, fromY = 0, wide = 16, deep = 40),
            c(COPY, obj = 1, x = 248, y = 88, fromX = 0, fromY = 176, wide = 6, deep = 24),
            c(COPY, obj = 1, x = 96, y = 80, delay = 3, fromX = 0, fromY = 80, wide = 12, deep = 24),
            c(COPY, x = 168, y = 16, fromX = 32, fromY = 0, wide = 8, deep = 80),
            c(COPY, x = 232, y = 16, fromX = 32, fromY = 80, wide = 8, deep = 80),
            c(SOUND, obj = 15),
            c(DRAW, obj = 3, x = 168, y = 16),
            c(COPY, obj = 1, x = 40, y = 32, fromX = 16, fromY = 0, wide = 16, deep = 40),
            c(COPY, obj = 1, x = 248, y = 88, fromX = 6, fromY = 176, wide = 6, deep = 24),
            c(COPY, obj = 1, x = 96, y = 80, delay = 3, fromX = 0, fromY = 104, wide = 12, deep = 24),
            c(COPY, x = 168, y = 16, fromX = 32, fromY = 0, wide = 8, deep = 80),
            c(COPY, x = 232, y = 16, fromX = 32, fromY = 80, wide = 8, deep = 80),
            c(SOUND, obj = 15),
            c(DRAW, obj = 2, x = 168, y = 16),
            c(COPY, obj = 1, x = 40, y = 32, fromX = 0, fromY = 40, wide = 16, deep = 40),
            c(COPY, obj = 1, x = 248, y = 88, fromX = 12, fromY = 176, wide = 6, deep = 24),
            c(COPY, obj = 1, x = 96, y = 80, delay = 3, fromX = 0, fromY = 128, wide = 12, deep = 24),
            c(COPY, x = 168, y = 16, fromX = 32, fromY = 0, wide = 8, deep = 80),
            c(COPY, x = 232, y = 16, fromX = 32, fromY = 80, wide = 8, deep = 80),
        ),
        // 14 — the same again with another bolt in it
        listOf(
            c(SOUND, obj = 15),
            c(DRAW, obj = 1, x = 168, y = 16),
            c(SOUND, obj = 9),
            c(COPY, obj = 1, x = 40, y = 32, fromX = 0, fromY = 0, wide = 16, deep = 40),
            c(COPY, obj = 1, x = 248, y = 88, fromX = 0, fromY = 176, wide = 6, deep = 24),
            c(COPY, obj = 1, x = 96, y = 80, delay = 3, fromX = 0, fromY = 80, wide = 12, deep = 24),
            c(COPY, x = 168, y = 16, fromX = 32, fromY = 0, wide = 8, deep = 80),
            c(COPY, x = 232, y = 16, fromX = 32, fromY = 80, wide = 8, deep = 80),
            c(SOUND, obj = 15),
            c(DRAW, obj = 3, x = 168, y = 16),
            c(COPY, obj = 1, x = 40, y = 32, fromX = 16, fromY = 0, wide = 16, deep = 40),
            c(COPY, obj = 1, x = 248, y = 88, fromX = 6, fromY = 176, wide = 6, deep = 24),
            c(COPY, obj = 1, x = 96, y = 80, delay = 3, fromX = 0, fromY = 104, wide = 12, deep = 24),
            c(COPY, x = 168, y = 16, fromX = 32, fromY = 0, wide = 8, deep = 80),
            c(COPY, x = 232, y = 16, fromX = 32, fromY = 80, wide = 8, deep = 80),
            c(SOUND, obj = 15),
            c(DRAW, obj = 2, x = 168, y = 16),
            c(SOUND, obj = 9),
            c(COPY, obj = 1, x = 40, y = 32, fromX = 0, fromY = 40, wide = 16, deep = 40),
            c(COPY, obj = 1, x = 248, y = 88, fromX = 12, fromY = 176, wide = 6, deep = 24),
            c(COPY, obj = 1, x = 96, y = 80, delay = 3, fromX = 0, fromY = 128, wide = 12, deep = 24),
            c(COPY, x = 168, y = 16, fromX = 32, fromY = 0, wide = 8, deep = 80),
            c(COPY, x = 232, y = 16, fromX = 32, fromY = 80, wide = 8, deep = 80),
        ),
        // 15 — and once more, without the first flash
        listOf(
            c(COPY, obj = 1, x = 40, y = 32, fromX = 0, fromY = 0, wide = 16, deep = 40),
            c(COPY, obj = 1, x = 248, y = 88, fromX = 0, fromY = 176, wide = 6, deep = 24),
            c(COPY, obj = 1, x = 96, y = 80, delay = 3, fromX = 0, fromY = 80, wide = 12, deep = 24),
            c(COPY, x = 168, y = 16, fromX = 32, fromY = 0, wide = 8, deep = 80),
            c(COPY, x = 232, y = 16, fromX = 32, fromY = 80, wide = 8, deep = 80),
            c(SOUND, obj = 15),
            c(DRAW, obj = 3, x = 168, y = 16),
            c(COPY, obj = 1, x = 40, y = 32, fromX = 16, fromY = 0, wide = 16, deep = 40),
            c(COPY, obj = 1, x = 248, y = 88, fromX = 6, fromY = 176, wide = 6, deep = 24),
            c(COPY, obj = 1, x = 96, y = 80, delay = 3, fromX = 0, fromY = 104, wide = 12, deep = 24),
            c(COPY, x = 168, y = 16, fromX = 32, fromY = 0, wide = 8, deep = 80),
            c(COPY, x = 232, y = 16, fromX = 32, fromY = 80, wide = 8, deep = 80),
            c(SOUND, obj = 9),
            c(COPY, obj = 1, x = 40, y = 32, fromX = 0, fromY = 40, wide = 16, deep = 40),
            c(COPY, obj = 1, x = 248, y = 88, fromX = 12, fromY = 176, wide = 6, deep = 24),
            c(COPY, obj = 1, x = 96, y = 80, delay = 3, fromX = 0, fromY = 128, wide = 12, deep = 24),
            c(COPY, x = 168, y = 16, fromX = 32, fromY = 0, wide = 8, deep = 80),
            c(COPY, x = 232, y = 16, fromX = 32, fromY = 80, wide = 8, deep = 80),
        ),
        // 16 — the temple lit three times over and put back each time
        listOf(
            c(SOUND, obj = 15),
            c(DRAW, obj = 1, x = 168, y = 16, delay = 3),
            c(COPY, x = 168, y = 16, fromX = 32, fromY = 0, wide = 8, deep = 80),
            c(COPY, x = 232, y = 16, fromX = 32, fromY = 80, wide = 8, deep = 80),
            c(SOUND, obj = 15),
            c(DRAW, obj = 2, x = 168, y = 16, delay = 3),
            c(COPY, x = 168, y = 16, fromX = 32, fromY = 0, wide = 8, deep = 80),
            c(COPY, x = 232, y = 16, fromX = 32, fromY = 80, wide = 8, deep = 80),
            c(SOUND, obj = 15),
            c(DRAW, obj = 3, x = 168, y = 16, delay = 3),
            c(COPY, x = 168, y = 16, fromX = 32, fromY = 0, wide = 8, deep = 80),
            c(COPY, x = 232, y = 16, fromX = 32, fromY = 80, wide = 8, deep = 80),
        ),
        // 17 — it going, with the colours changing under it
        listOf(
            c(COPY, x = 168, y = 16, delay = 1, fromX = 0, fromY = 0, wide = 16, deep = 80),
            c(COPY, x = 168, y = 16, delay = 2, fromX = 16, fromY = 0, wide = 16, deep = 80),
            c(COPY, x = 168, y = 16, delay = 3, fromX = 0, fromY = 0, wide = 16, deep = 80),
            c(COPY, x = 168, y = 16, delay = 1, fromX = 0, fromY = 0, wide = 16, deep = 80),
            c(COPY, x = 168, y = 16, delay = 2, fromX = 16, fromY = 0, wide = 16, deep = 80),
            c(COPY, x = 168, y = 16, delay = 3, fromX = 0, fromY = 80, wide = 16, deep = 80),
            c(COPY, x = 168, y = 16, delay = 1, fromX = 16, fromY = 0, wide = 16, deep = 80),
            c(COPY, x = 168, y = 16, delay = 2, fromX = 0, fromY = 80, wide = 16, deep = 80),
            c(COPY, x = 168, y = 16, delay = 3, fromX = 16, fromY = 0, wide = 16, deep = 80),
            c(COPY, x = 168, y = 16, delay = 1, fromX = 0, fromY = 0, wide = 16, deep = 80),
            c(COPY, x = 168, y = 16, delay = 2, fromX = 16, fromY = 0, wide = 16, deep = 80),
            c(SOUND, obj = 10),
            c(COPY, x = 168, y = 16, delay = 3, fromX = 0, fromY = 0, wide = 16, deep = 80),
            c(COPY, x = 168, y = 16, delay = 1, fromX = 16, fromY = 0, wide = 16, deep = 80),
            c(COPY, x = 168, y = 16, delay = 2, fromX = 0, fromY = 80, wide = 16, deep = 80),
            c(COPY, x = 168, y = 16, delay = 3, fromX = 16, fromY = 0, wide = 16, deep = 80),
            c(COPY, x = 168, y = 16, delay = 1, fromX = 0, fromY = 80, wide = 16, deep = 80),
            c(COPY, x = 168, y = 16, delay = 2, fromX = 16, fromY = 0, wide = 16, deep = 80),
            c(COPY, x = 168, y = 16, delay = 3, fromX = 16, fromY = 0, wide = 16, deep = 80),
        ),
        // 18 — and gone, twenty-seven frames of it
        listOf(
            c(COPY, x = 168, y = 16, delay = 1, fromX = 0, fromY = 80, wide = 16, deep = 80),
            c(COPY, x = 168, y = 16, delay = 2, fromX = 0, fromY = 80, wide = 16, deep = 80),
            c(COPY, x = 168, y = 16, delay = 3, fromX = 16, fromY = 80, wide = 16, deep = 80),
            c(COPY, x = 168, y = 16, delay = 1, fromX = 0, fromY = 80, wide = 16, deep = 80),
            c(COPY, x = 168, y = 16, delay = 2, fromX = 16, fromY = 80, wide = 16, deep = 80),
            c(COPY, x = 168, y = 16, delay = 3, fromX = 0, fromY = 80, wide = 16, deep = 80),
            c(COPY, x = 168, y = 16, delay = 1, fromX = 16, fromY = 0, wide = 16, deep = 80),
            c(COPY, x = 168, y = 16, delay = 2, fromX = 0, fromY = 80, wide = 16, deep = 80),
            c(COPY, x = 168, y = 16, delay = 3, fromX = 16, fromY = 80, wide = 16, deep = 80),
            c(COPY, x = 168, y = 16, delay = 1, fromX = 0, fromY = 80, wide = 16, deep = 80),
            c(COPY, x = 168, y = 16, delay = 2, fromX = 16, fromY = 0, wide = 16, deep = 80),
            c(COPY, x = 168, y = 16, delay = 3, fromX = 0, fromY = 0, wide = 16, deep = 80),
            c(COPY, x = 168, y = 16, delay = 1, fromX = 0, fromY = 0, wide = 16, deep = 80),
            c(COPY, x = 168, y = 16, delay = 2, fromX = 16, fromY = 0, wide = 16, deep = 80),
            c(COPY, x = 168, y = 16, delay = 3, fromX = 0, fromY = 80, wide = 16, deep = 80),
            c(COPY, x = 168, y = 16, delay = 1, fromX = 16, fromY = 80, wide = 16, deep = 80),
            c(COPY, x = 168, y = 16, delay = 2, fromX = 0, fromY = 80, wide = 16, deep = 80),
            c(COPY, x = 168, y = 16, delay = 3, fromX = 16, fromY = 0, wide = 16, deep = 80),
            c(COPY, x = 168, y = 16, delay = 1, fromX = 0, fromY = 80, wide = 16, deep = 80),
            c(COPY, x = 168, y = 16, delay = 2, fromX = 16, fromY = 80, wide = 16, deep = 80),
            c(COPY, x = 168, y = 16, delay = 3, fromX = 0, fromY = 80, wide = 16, deep = 80),
            c(COPY, x = 168, y = 16, delay = 1, fromX = 16, fromY = 80, wide = 16, deep = 80),
            c(COPY, x = 168, y = 16, delay = 2, fromX = 0, fromY = 80, wide = 16, deep = 80),
            c(COPY, x = 168, y = 16, delay = 3, fromX = 16, fromY = 0, wide = 16, deep = 80),
            c(COPY, x = 168, y = 16, delay = 1, fromX = 0, fromY = 80, wide = 16, deep = 80),
            c(COPY, x = 168, y = 16, delay = 2, fromX = 16, fromY = 80, wide = 16, deep = 80),
            c(COPY, x = 168, y = 16, fromX = 16, fromY = 80, wide = 16, deep = 80),
        ),
        // 19 — the party at the end, six frames of them
        listOf(
            c(COPY, x = 80, y = 80, delay = 4, fromX = 0, fromY = 128, wide = 6, deep = 56),
            c(COPY, x = 80, y = 80, delay = 4, fromX = 6, fromY = 128, wide = 6, deep = 56),
            c(COPY, x = 80, y = 80, delay = 4, fromX = 12, fromY = 128, wide = 6, deep = 56),
            c(COPY, x = 80, y = 80, delay = 4, fromX = 18, fromY = 128, wide = 6, deep = 56),
            c(COPY, x = 80, y = 80, delay = 4, fromX = 24, fromY = 128, wide = 6, deep = 56),
            c(COPY, x = 80, y = 80, delay = 4, fromX = 30, fromY = 128, wide = 6, deep = 56),
        ),
        // 20 — and his mouth, saying the last of it
        listOf(
            c(COPY, x = 96, y = 56, delay = 3, fromX = 0, fromY = 184, wide = 4, deep = 16),
            c(COPY, x = 96, y = 56, fromX = 4, fromY = 184, wide = 4, deep = 16),
        ),
    )

    /**
     * How five of the sheets are cut into shapes, by the scene they belong to.
     * A scene not named here has none, and the shapes a scene does define
     * replace whatever was cut before under the same number.
     */
    val SHAPES: Map<Int, List<SequenceShape>> = mapOf(
        0 to listOf(
            SequenceShape(index = 1, across = 16, down = 0, wide = 11, deep = 48),
            SequenceShape(index = 2, across = 28, down = 80, wide = 8, deep = 48),
            SequenceShape(index = 3, across = 9, down = 0, wide = 5, deep = 40),
        ),
        3 to listOf(
            SequenceShape(index = 1, across = 30, down = 0, wide = 8, deep = 96),
            SequenceShape(index = 2, across = 30, down = 104, wide = 10, deep = 96),
        ),
        7 to listOf(
            SequenceShape(index = 1, across = 0, down = 0, wide = 16, deep = 72),
            SequenceShape(index = 2, across = 16, down = 0, wide = 16, deep = 72),
            SequenceShape(index = 3, across = 0, down = 72, wide = 16, deep = 72),
        ),
        9 to listOf(
            SequenceShape(index = 0, across = 0, down = 0, wide = 32, deep = 16),
            SequenceShape(index = 2, across = 0, down = 36, wide = 35, deep = 41),
            SequenceShape(index = 3, across = 0, down = 77, wide = 24, deep = 17),
            SequenceShape(index = 4, across = 0, down = 94, wide = 15, deep = 33),
            SequenceShape(index = 5, across = 24, down = 77, wide = 10, deep = 17),
            SequenceShape(index = 6, across = 16, down = 99, wide = 23, deep = 69),
            SequenceShape(index = 10, across = 0, down = 136, wide = 8, deep = 64),
            SequenceShape(index = 11, across = 8, down = 136, wide = 8, deep = 64),
        ),
        10 to listOf(
            SequenceShape(index = 1, across = 0, down = 0, wide = 40, deep = 30),
            SequenceShape(index = 15, across = 9, down = 37, wide = 21, deep = 48),
            SequenceShape(index = 16, across = 16, down = 88, wide = 6, deep = 56),
        ),
    )
}
