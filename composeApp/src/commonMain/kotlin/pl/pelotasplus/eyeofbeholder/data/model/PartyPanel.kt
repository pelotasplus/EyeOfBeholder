package pl.pelotasplus.eyeofbeholder.data.model

/**
 * One of the six boxes down the right of the play field, one per party slot.
 *
 * Two columns of three, and the slot order runs across before it runs down.
 *
 * The play field art has only the top four drawn into it — every box is
 * stamped from a template kept on a page of its own — so the bottom row
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

    /** Where a blow just taken is shown, over the face and the bars alike. */
    val splatLeft: Int get() = left + SPLAT_OFFSET_X
    val splatTop: Int get() = top + SPLAT_OFFSET_Y

    /**
     * Where the number on it goes. It is centred by counting the digits off
     * the middle rather than measured, the same as everything else here.
     */
    fun damageLeft(digits: Int): Int = left + DAMAGE_MIDDLE - digits * DAMAGE_DIGIT
    val damageTop: Int get() = top + DAMAGE_Y

    /**
     * Where what a champion holds is drawn: two slots stacked beside the
     * face, one hand above the other, with the icon set in from the left of
     * its slot rather than centred in it.
     */
    val handSlotLeft: Int get() = left + HAND_X
    val handLeft: Int get() = handSlotLeft + HAND_ICON_X

    fun handTop(hand: Int): Int = top + HAND_Y + hand * HAND_STEP

    /** Whether a click landed on one of the two slots a champion holds with. */
    fun holdsHandAt(x: Int, y: Int, hand: Int): Boolean =
        x in handSlotLeft until handSlotLeft + HAND_SLOT_WIDTH &&
            y in handTop(hand) until handTop(hand) + HAND_SLOT_HEIGHT

    /**
     * Whether a click landed on the strip along the top of the box, which is
     * where the name is written and where two champions are swapped from.
     *
     * The strip is the width of the box rather than the width of the name:
     * a short name would otherwise be a smaller target than a long one.
     */
    fun showsNameAt(x: Int, y: Int): Boolean =
        x in left until left + WIDTH && y in top until top + NAME_STRIP_HEIGHT

    /** Whether a click landed on the face, which is what opens a champion's page. */
    fun showsFaceAt(x: Int, y: Int): Boolean =
        x in portraitLeft until portraitLeft + PORTRAIT_SIZE &&
            y in portraitTop until portraitTop + PORTRAIT_SIZE

    companion object {
        const val WIDTH = 64
        const val HEIGHT = 50

        private const val NAME_X = 2
        private const val NAME_Y = 2

        private const val PORTRAIT_X = 0
        private const val PORTRAIT_Y = 9

        /** Down to where the portrait starts, the strip being the rest of it. */
        private const val NAME_STRIP_HEIGHT = PORTRAIT_Y
        private const val HAND_X = 32
        private const val HAND_Y = 9
        private const val HAND_STEP = 16
        private const val HAND_ICON_X = 8
        const val HAND_SLOT_WIDTH = 31
        const val HAND_SLOT_HEIGHT = 16

        private const val BAR_X = 15
        private const val BAR_Y = 44
        private const val BAR_LABEL_X = 2
        private const val BAR_LABEL_Y = 43

        /** Where the splat sits in the box, and the number on it. All the game's own. */
        private const val SPLAT_OFFSET_X = 13
        private const val SPLAT_OFFSET_Y = 30
        private const val DAMAGE_MIDDLE = 34
        private const val DAMAGE_DIGIT = 3
        private const val DAMAGE_Y = 42

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
 * What an empty hand is drawn as, one icon for each of the two — the hand
 * itself, which is what says a champion would strike with it rather than with
 * anything they are holding.
 */
fun emptyHandIcon(hand: Int) = ItemIconId(FIRST_EMPTY_HAND + hand)

private const val FIRST_EMPTY_HAND = 85

/**
 * The grid laid over a hand its champion cannot strike with, cut from the same
 * sheet as the compass.
 */
fun Cps.weaponSlotGrid(): Cps.ItemIcon = cut(
    x = GRID_X,
    y = GRID_Y,
    w = GRID_WIDTH,
    h = GRID_HEIGHT,
)

private const val GRID_X = 64
private const val GRID_Y = 88
private const val GRID_WIDTH = 32
private const val GRID_HEIGHT = 16

/**
 * The splash a blow's outcome is written on, cut from the sheet of things that
 * can be thrown — which is where it is kept, along with the beams and the
 * other splash that is red.
 */
fun Cps.greenSplat(): Cps.ItemIcon = cut(
    x = SPLAT_X,
    y = SPLAT_Y,
    w = SPLAT_WIDTH,
    h = SPLAT_HEIGHT,
)

/**
 * The bigger, redder one, which goes over a champion's face when they are hit
 * rather than in the slot a weapon reports from.
 */
fun Cps.redSplat(): Cps.ItemIcon = cut(
    x = SPLAT_X,
    y = RED_SPLAT_Y,
    w = SPLAT_WIDTH,
    h = RED_SPLAT_HEIGHT,
)

const val THROWN_CPS = "THROWN.CPS"

private const val SPLAT_X = 128
private const val SPLAT_Y = 96
private const val SPLAT_WIDTH = 40
private const val SPLAT_HEIGHT = 16
private const val RED_SPLAT_Y = 72
private const val RED_SPLAT_HEIGHT = 24

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

/**
 * The face of whoever is in a slot, from whichever sheet keeps it.
 *
 * A champion the player made counts from zero across the sheet the maker drew
 * from. Somebody met in the dungeon counts backwards from -1 along the top row
 * of a sheet of their own, which is how a face nobody chose is told from one
 * that was chosen.
 */
fun faceOf(id: PortraitId, made: Cps, met: Cps?): Cps.ItemIcon? = when {
    id.value >= 0 -> made.portrait(id)
    met == null -> null
    else -> met.cut(
        x = (-(id.value + 1)) * PORTRAIT_SIZE,
        y = 0,
        w = PORTRAIT_SIZE,
        h = PORTRAIT_SIZE,
    )
}

private const val PORTRAITS_PER_ROW = 10
private const val PORTRAIT_SIZE = 32

/** How much of a bar is coloured in, and in which of the three colours. */
data class BarFill(val filled: Int, val colour: PaletteIndex)

/**
 * The bar that says how hurt a champion is: green while they are well, yellow
 * once they are down to a third, red when they are down.
 *
 * Both the length and the colour count from -10 rather than from 0, because
 * -10 is as dead as a champion gets and 0 is merely unconscious. Anyone with
 * anything left over keeps a pixel of bar however little it is, so being
 * knocked out reads differently from being dead.
 */
fun hitPointBar(hitPoints: HitPoints, width: Int): BarFill {
    val room = hitPoints.max + DEAD_FOR_GOOD
    val left = (hitPoints.current + DEAD_FOR_GOOD).coerceIn(0, room)

    return BarFill(
        filled = if (room < 1) 0 else (left * width / room).coerceAtLeast(if (left > 0) 1 else 0),
        colour = when {
            left <= DEAD_FOR_GOOD -> BAR_DOWN
            room / 3 > left -> BAR_HURT
            else -> BAR_WELL
        },
    )
}

/** The same bar for how hungry a champion is, which is counted out of a hundred. */
fun foodBar(food: Food, width: Int): BarFill {
    val left = food.value.coerceIn(0, FULL)

    return BarFill(
        filled = left * width / FULL,
        colour = when {
            left < STARVING -> BAR_DOWN
            left < PECKISH -> BAR_HURT
            else -> BAR_WELL
        },
    )
}

/** How far below zero a champion's hit points can go before it is permanent. */
private const val DEAD_FOR_GOOD = 10

private const val FULL = 100
private const val PECKISH = 33
private const val STARVING = 20

private val BAR_WELL = PaletteIndex(3)
private val BAR_HURT = PaletteIndex(5)
private val BAR_DOWN = PaletteIndex(8)
