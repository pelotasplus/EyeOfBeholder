package pl.pelotasplus.eyeofbeholder.data.model

import pl.pelotasplus.eyeofbeholder.data.model.CharacterClass.CLERIC
import pl.pelotasplus.eyeofbeholder.data.model.CharacterClass.FIGHTER
import pl.pelotasplus.eyeofbeholder.data.model.CharacterClass.MAGE
import pl.pelotasplus.eyeofbeholder.data.model.CharacterClass.PALADIN
import pl.pelotasplus.eyeofbeholder.data.model.CharacterClass.RANGER
import pl.pelotasplus.eyeofbeholder.data.model.CharacterClass.THIEF

/**
 * A champion's own page, which takes over the right-hand side of the screen
 * from the six party boxes for as long as it is open.
 *
 * It has two sides. [Page.BELONGINGS] is what they are carrying, laid out on
 * and around a figure; [Page.STATS] is what they are, which is where the
 * ability scores live. The corner at the bottom right turns from one to the
 * other, and the arrows above walk along the party without closing the page.
 */
data class CharacterSheet(
    /** Which of the six is being looked at. */
    val slot: PartySlot,
    val page: Page = Page.BELONGINGS,
) {
    enum class Page { BELONGINGS, STATS }

    val turnedOver: CharacterSheet
        get() = copy(page = if (page == Page.BELONGINGS) Page.STATS else Page.BELONGINGS)

    /**
     * The page of the champion [step] along the party, which wraps from the
     * last back to the first.
     *
     * Two of the six slots may be empty, and a slot nobody fills is not
     * somebody to look at, so the arrows step past those — walking along a
     * party of four goes round in four, not six. A party of one has nowhere
     * to walk to and stays put.
     */
    fun walked(step: Int, party: List<Champion>): CharacterSheet {
        var next = slot.index
        repeat(Champion.PARTY_SLOTS) {
            next = (next + step).mod(Champion.PARTY_SLOTS)
            if (party.getOrNull(next)?.inTheParty == true) return copy(slot = PartySlot(next))
        }
        return this
    }

    /**
     * What clicking at [x], [y] on the 320×200 screen means, or null where the
     * page has nothing there.
     */
    fun clicked(x: Int, y: Int): SheetChoice? = when {
        SheetControl.CLOSE.contains(x, y) -> SheetChoice.Close
        SheetControl.TURN_PAGE.contains(x, y) -> SheetChoice.TurnPage
        SheetControl.PREVIOUS.contains(x, y) -> SheetChoice.Walk(-1)
        SheetControl.NEXT.contains(x, y) -> SheetChoice.Walk(1)

        // the place laid is only on the belongings side of the page
        page == Page.BELONGINGS && SheetControl.EAT.contains(x, y) -> SheetChoice.Eat
        else -> null
    }

    companion object {
        /** The panel is cut from INVENT.CPS at the place it is drawn to. */
        const val LEFT = 176
        const val TOP = 0
        const val WIDTH = 144
        const val HEIGHT = 168

        /** Where the champion's own face goes, which is also what closes the page. */
        const val PORTRAIT_LEFT = 181
        const val PORTRAIT_TOP = 3

        const val NAME_LEFT = 219
        const val NAME_TOP = 6

        /** The two bars under the name: how hurt they are, and how hungry. */
        const val BAR_LEFT = 250
        const val HIT_POINT_BAR_TOP = 16
        const val FOOD_BAR_TOP = 25
        const val BAR_WIDTH = 51
        const val BAR_HEIGHT = 5
    }
}

/**
 * Where everything on the second page goes, and in what colour.
 *
 * The page is the same panel with its figure and slots painted out, so it
 * starts by blanking [BLANKED] and then writes over it. Transcribed.
 */
object StatsPage {
    /** The parts of the panel the figure and the slots are wiped from. */
    val BLANKED: List<Blanked> = listOf(
        Blanked(179, 36, 271, 165),
        Blanked(272, 51, 300, 165),
        Blanked(301, 51, 318, 147),
    )

    /** Both corners are drawn, so a rectangle's own edges are inside it. */
    data class Blanked(val left: Int, val top: Int, val right: Int, val bottom: Int)

    const val HEADLINE = "CHARACTER INFO"
    const val HEADLINE_LEFT = 183
    const val HEADLINE_TOP = 42

    /** Class, then alignment, then race and sex, one line under the other. */
    const val DESCRIPTION_LEFT = 183
    const val DESCRIPTION_TOP = 55
    const val DESCRIPTION_LINE = 7

    /**
     * The six scores: their names down the left, their numbers off to the
     * right, in the order [abilityNames] is in.
     */
    const val ABILITY_LEFT = 183
    const val ABILITY_TOP = 82
    const val ABILITY_VALUE_LEFT = 275
    const val ABILITY_LINE = 7

    const val ARMOUR_CLASS = "ARMOR CLASS"
    const val ARMOUR_CLASS_LEFT = 183
    const val ARMOUR_CLASS_TOP = 124
    const val ARMOUR_CLASS_VALUE_LEFT = 275

    /**
     * A champion gets a line of these per career they are following, so a
     * multi-class has two. The two numbers are written about their column
     * rather than from it.
     */
    const val EXPERIENCE = "EXP"
    const val EXPERIENCE_LEFT = 239
    const val EXPERIENCE_TOP = 138
    const val EXPERIENCE_MIDDLE = 251

    const val LEVEL = "LVL"
    const val LEVEL_LEFT = 278
    const val LEVEL_TOP = 138
    const val LEVEL_MIDDLE = 286

    /** What that career is called goes on the same line, off to the left. */
    const val CAREER_LEFT = 180

    const val CAREER_TOP = 145
    const val CAREER_LINE = 7

    val HEADLINE_COLOUR = PaletteIndex(15)
    val LABEL_COLOUR = PaletteIndex(12)
    val VALUE_COLOUR = PaletteIndex(15)

    /** The colour the blanked parts of the panel are filled with. */
    val BLANK_COLOUR = PaletteIndex(183)
}

/** The six ability scores in the order the page lists them. */
val abilityNames: List<String> = listOf(
    "STRENGTH", "INTELLIGENCE", "WISDOM", "DEXTERITY", "CONSTITUTION", "CHARISMA",
)

/** What each class is called, in the order [CharacterClass] lists them. */
val classNames: List<String> = listOf(
    "FIGHTER", "RANGER", "PALADIN", "MAGE", "CLERIC", "THIEF",
    "FIGHTER/CLERIC", "FIGHTER/THIEF", "FIGHTER/MAGE", "FIGHTER/MAGE/THIEF",
    "THIEF/MAGE", "CLERIC/THIEF", "FIGHTER/CLERIC/MAGE", "RANGER/CLERIC", "CLERIC/MAGE",
)

val alignmentNames: List<String> = listOf(
    "LAWFUL GOOD", "NEUTRAL GOOD", "CHAOTIC GOOD",
    "LAWFUL NEUTRAL", "TRUE NEUTRAL", "CHAOTIC NEUTRAL",
    "LAWFUL EVIL", "NEUTRAL EVIL", "CHAOTIC EVIL",
)

/** Race and sex are one number, the sexes alternating. */
val raceAndSexNames: List<String> = listOf(
    "HUMAN MALE", "HUMAN FEMALE",
    "ELF MALE", "ELF FEMALE",
    "HALF-ELF MALE", "HALF-ELF FEMALE",
    "DWARF MALE", "DWARF FEMALE",
    "GNOME MALE", "GNOME FEMALE",
    "HALFLING MALE", "HALFLING FEMALE",
)

/**
 * What a champion is, in the words the second page puts it in. A number the
 * save holds that no table covers reads as nothing rather than as a guess.
 */
val Champion.className: String get() = characterClass?.title.orEmpty()

/** What a class is called, single or combined alike. */
val CharacterClass.title: String get() = classNames.getOrNull(ordinal).orEmpty()

/**
 * What each of the classes a champion is levelled in is called on its own,
 * which is what goes beside its level and experience.
 */
val Champion.levelledClassNames: List<String>
    get() = characterClass?.levelledIn.orEmpty().map { it.title }

val Champion.alignmentName: String get() = alignment?.let { alignmentNames[it.ordinal] }.orEmpty()

/**
 * Race and sex are named together, the sexes alternating, so the pair is what
 * the table is indexed by.
 */
val Champion.raceAndSexName: String
    get() {
        val race = race ?: return ""
        val sex = sex ?: return ""
        return raceAndSexNames.getOrNull(race.ordinal * Sex.entries.size + sex.ordinal).orEmpty()
    }

/**
 * The six scores in the order the page lists them, as they stand now rather
 * than at their best.
 *
 * Strength is written with its percentile when there is one: an eighteen is
 * the only score that carries one, and it is what tells two fighters apart.
 */
fun Abilities.asListed(): List<String> = listOf(
    if (strengthPercentile.current > 0) {
        "${strength.current}/${strengthPercentile.current}"
    } else {
        "${strength.current}"
    },
    "${intelligence.current}",
    "${wisdom.current}",
    "${dexterity.current}",
    "${constitution.current}",
    "${charisma.current}",
)

/**
 * A sheet with everything on it filled in: who it belongs to and what they
 * have in each of their twenty-seven slots, an empty one being null.
 */
data class OpenSheet(
    val page: CharacterSheet.Page,
    val champion: Champion,
    val carrying: List<Item?>,
    /**
     * How many arrows are in the quiver, which is a tally rather than an item
     * to draw — and is written even when it is none, so an empty quiver reads
     * as empty rather than as a box nothing has been drawn in.
     */
    val arrows: Int = 0,
)

/** What a click on an open [CharacterSheet] asks for. */
sealed interface SheetChoice {
    data object Close : SheetChoice
    data object TurnPage : SheetChoice

    /** Eat what is held, off the place laid on the belongings page. */
    data object Eat : SheetChoice

    /** Along the party, by [step] slots, wrapping at either end. */
    data class Walk(val step: Int) : SheetChoice
}

/**
 * The parts of the page that can be clicked, in the 320×200 screen space.
 */
enum class SheetControl(
    private val x: Int,
    private val y: Int,
    private val width: Int,
    private val height: Int,
) {
    PREVIOUS(274, 35, 20, 15),
    NEXT(297, 35, 20, 15),
    TURN_PAGE(291, 149, 25, 17),

    /** The place laid beside the arrows: what is held is eaten off it. */
    EAT(237, 35, 32, 17),

    /** Clicking the face puts the page down and the party back up. */
    CLOSE(CharacterSheet.PORTRAIT_LEFT, CharacterSheet.PORTRAIT_TOP, 32, 32);

    fun contains(screenX: Int, screenY: Int): Boolean =
        screenX in x until x + width && screenY in y until y + height
}

/**
 * Where each of the twenty-seven things a champion can be carrying is drawn,
 * as the top left of its sixteen-pixel icon.
 *
 * The order is the one a champion's [Champion.carrying] is in: the two hands
 * first, then the fourteen pockets of the pack down the left, then what is
 * worn — and the last two boxes are smaller than the rest, so their icons hang
 * out over the edges of them.
 */
val inventorySlotPositions: List<InventorySlot> = listOf(
    230 to 116, 278 to 116,
    181 to 40, 199 to 40, 181 to 58, 199 to 58, 181 to 76, 199 to 76,
    181 to 94, 199 to 94, 181 to 112, 199 to 112, 181 to 130, 199 to 130,
    181 to 148, 199 to 148,
    225 to 56, 224 to 76, 225 to 96, 298 to 55, 287 to 75, 277 to 137,
    300 to 94, 300 to 112, 300 to 130, 228 to 136, 240 to 136,
).mapIndexed { slot, (left, top) -> InventorySlot(CarrySlot(slot), left, top) }

/**
 * @property left where the icon is drawn, before the two undersized boxes pull
 *   theirs back to cover them
 */
data class InventorySlot(val slot: CarrySlot, private val left: Int, private val top: Int) {
    val iconLeft: Int get() = if (undersized) left - ICON_OVERHANG else left
    val iconTop: Int get() = if (undersized) top - ICON_OVERHANG else top

    /**
     * The quiver holds a stack rather than one thing, which makes it unlike
     * every other slot twice over: it says how many arrows are in it instead
     * of showing one of them, and taking from it or adding to it works the
     * stack rather than swapping what is there.
     */
    val isQuiver: Boolean get() = slot == CarrySlot.QUIVER

    /**
     * What this slot will take. The pack takes anything; the rest take one
     * kind each, which is what keeps a helmet off a foot.
     */
    val takes: SlotTakes get() = slotTakes.getOrElse(slot.index) { SlotTakes.Anything }

    /** Whether a click landed on this slot's box. */
    fun holdsAt(x: Int, y: Int): Boolean {
        val side = if (undersized) SMALL_BOX else BOX
        return x in left until left + side && y in top until top + side
    }

    val tallyLeft: Int get() = left + TALLY_X
    val tallyTop: Int get() = top + TALLY_Y

    /**
     * Where a tally of [figures] figures starts, which is further in for one
     * than for two, so that either sits about the middle of the strip.
     */
    fun tallyStart(figures: Int): Int = left + if (figures > 1) WIDE_TALLY_X else TALLY_FIGURE_X

    private val undersized: Boolean get() = slot.index >= FIRST_UNDERSIZED

    companion object {
        const val TALLY_WIDTH = 12
        const val TALLY_HEIGHT = 5

        /** The two slots at the bottom have smaller boxes than the rest. */
        private const val BOX = 16
        private const val SMALL_BOX = 10

        private const val FIRST_UNDERSIZED = 25
        private const val ICON_OVERHANG = 4

        private const val TALLY_X = 3
        private const val TALLY_Y = 9
        private const val TALLY_FIGURE_X = 8
        private const val WIDE_TALLY_X = 2
    }
}

/**
 * What each of the twenty-seven slots will take, in the order
 * [Champion.carrying] is in: the two hands, the fourteen pockets of the pack,
 * and then what is worn.
 */
private val slotTakes: List<SlotTakes> = buildList {
    add(SlotTakes.Only(ItemFits.HAND))
    add(SlotTakes.Only(ItemFits.HAND))

    repeat(POCKETS) { add(SlotTakes.Anything) }

    add(SlotTakes.Only(ItemFits.QUIVER))
    add(SlotTakes.Only(ItemFits.ARMOUR))
    add(SlotTakes.Only(ItemFits.BRACERS))
    add(SlotTakes.Only(ItemFits.HELMET))
    add(SlotTakes.Only(ItemFits.NECKLACE))
    add(SlotTakes.Only(ItemFits.BOOTS))
    add(SlotTakes.Anything)
    add(SlotTakes.Only(ItemFits.POUCH))
    add(SlotTakes.Only(ItemFits.POUCH))
    add(SlotTakes.Only(ItemFits.RING))
    add(SlotTakes.Only(ItemFits.RING))
}

/** How many pockets the pack has, in two columns down the left of the page. */
private const val POCKETS = 14

/**
 * Which slot a click on an open page landed on, if any. The order matters
 * where boxes overlap: the two small ones at the bottom sit inside what the
 * bigger boxes would cover.
 */
fun inventorySlotAt(x: Int, y: Int): InventorySlot? =
    inventorySlotPositions.firstOrNull { it.holdsAt(x, y) }

/**
 * One of the icons packed into ITEMICN.CPS, which is where an item is drawn
 * from when it is being carried rather than lying on the floor — sixteen
 * pixels square, twenty to a row.
 */
fun Cps.itemIcon(id: ItemIconId): Cps.ItemIcon = cut(
    x = (id.value % ICONS_PER_ROW) * ICON_SIZE,
    y = (id.value / ICONS_PER_ROW) * ICON_SIZE,
    w = ICON_SIZE,
    h = ICON_SIZE,
)

private const val ICONS_PER_ROW = 20
private const val ICON_SIZE = 16
