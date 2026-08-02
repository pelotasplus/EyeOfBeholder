package pl.pelotasplus.eyeofbeholder.data.model

/**
 * One of the six boxes down the right of the play field, one per party slot.
 *
 * Two columns of three, and the slot order runs across before it runs down.
 *
 * The play field art has only the top four drawn into it — the original stamps
 * every box from a template it keeps on a page of its own — so the bottom row
 * is stamped from the art's own first box, which comes to the same picture.
 *
 * The offsets inside a box are its own art read off: a name strip along the
 * top, a square hole for the portrait, two weapon slots stacked beside it, and
 * a bar along the bottom.
 */
data class ChampionBox(val left: Int, val top: Int) {

    val nameLeft: Int get() = left + NAME_X
    val nameTop: Int get() = top + NAME_Y
    val portraitLeft: Int get() = left + PORTRAIT_X
    val portraitTop: Int get() = top + PORTRAIT_Y
    val barLeft: Int get() = left + BAR_X
    val barTop: Int get() = top + BAR_Y
    val barLabelLeft: Int get() = left + BAR_LABEL_X
    val barLabelTop: Int get() = top + BAR_LABEL_Y

    companion object {
        const val WIDTH = 64
        const val HEIGHT = 50

        private const val NAME_X = 2
        private const val NAME_Y = 2
        private const val PORTRAIT_X = 0
        private const val PORTRAIT_Y = 9
        private const val BAR_X = 15
        private const val BAR_Y = 44
        private const val BAR_LABEL_X = 2
        private const val BAR_LABEL_Y = 43

        const val BAR_WIDTH = 39
        const val BAR_HEIGHT = 3

        const val BAR_LABEL = "HP"
    }
}

/** The six boxes in slot order, across then down. */
val championBoxes: List<ChampionBox> = listOf(2, 54, 106).flatMap { top ->
    listOf(184, 256).map { left -> ChampionBox(left, top) }
}

/** The one the play field art draws, which the rest are stamped from. */
val boxInTheArt: ChampionBox = championBoxes.first()

/**
 * One of the 44 faces a champion can wear, cut from CHARGENA.CPS, where they
 * are laid out ten to a row.
 */
fun Cps.portrait(id: PortraitId): Cps.ItemIcon = cut(
    x = (id.value % PORTRAITS_PER_ROW) * PORTRAIT_SIZE,
    y = (id.value / PORTRAITS_PER_ROW) * PORTRAIT_SIZE,
    w = PORTRAIT_SIZE,
    h = PORTRAIT_SIZE,
)

private const val PORTRAITS_PER_ROW = 10
private const val PORTRAIT_SIZE = 32

data class HitPointBar(val filled: Int, val colour: PaletteIndex)

/**
 * The bar in a champion's box: green while they are well, yellow once they are
 * down to a third, red when they are down.
 *
 * Both the length and the colour count from -10 rather than from 0, because
 * -10 is as dead as a champion gets and 0 is merely unconscious. Anyone with
 * anything left over keeps a pixel of bar however little it is, so being
 * knocked out reads differently from being dead.
 */
fun hitPointBar(hitPoints: HitPoints): HitPointBar {
    val room = hitPoints.max + DEAD_FOR_GOOD
    val left = (hitPoints.current + DEAD_FOR_GOOD).coerceIn(0, room)

    val filled = if (room < 1) 0 else (left * ChampionBox.BAR_WIDTH / room).coerceAtLeast(
        if (left > 0) 1 else 0
    )

    return HitPointBar(
        filled = filled,
        colour = when {
            left <= DEAD_FOR_GOOD -> BAR_DOWN
            room / 3 > left -> BAR_HURT
            else -> BAR_WELL
        },
    )
}

/** How far below zero a champion's hit points can go before it is permanent. */
private const val DEAD_FOR_GOOD = 10

private val BAR_WELL = PaletteIndex(3)
private val BAR_HURT = PaletteIndex(5)
private val BAR_DOWN = PaletteIndex(8)
