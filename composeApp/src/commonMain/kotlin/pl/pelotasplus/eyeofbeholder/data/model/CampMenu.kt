package pl.pelotasplus.eyeofbeholder.data.model

/**
 * The menus behind the Camp button.
 *
 * All of them are the same 176×144 panel over the top left of the screen, with
 * a title along the top and a column of lines under it. The menus proper put
 * their lines at one indent and their way out in the bottom right corner; the
 * save and load lists put theirs at another and call it Cancel. That is the
 * only difference between them, so they are one type.
 *
 * The strings and every coordinate here are transcribed.
 */
data class CampMenu(
    val title: String,
    val titleLeft: Int,
    val entries: List<MenuEntry>,
    /** The slot being named, which takes the keyboard while it is. */
    val naming: Naming? = null,

    /**
     * The box this one is drawn in. The menus proper fill the panel; a question
     * put to the player is a smaller box set into the middle of it, so the
     * answer is read where it was asked rather than across the whole screen.
     */
    val left: Int = LEFT,
    val top: Int = TOP,
    val width: Int = WIDTH,
    val height: Int = HEIGHT,

    /**
     * What is written across the box above its lines, a line to an entry.
     * A menu says everything in its lines and has none of this; a question
     * needs the room to ask.
     */
    val says: List<String> = emptyList(),
    val saysLeft: Int = 0,
    val saysTop: Int = 0,
    val saysColour: GuiColour = GuiColour.WHITE,

    /**
     * What this one is drawn on top of, if anything.
     *
     * A question put to a sleeping party is a small box set into the resting
     * one rather than a screen of its own: the hours stay legible above it,
     * and answering rubs the question out and leaves them there.
     */
    val over: CampMenu? = null,

    /** Words written where a menu puts them rather than as its lines — see [MenuNote]. */
    val notes: List<MenuNote> = emptyList(),

    /**
     * Champions whose names are checkered over, being no answer to what the
     * menu asks.
     */
    val greyed: Set<PartySlot> = emptySet(),
) {
    /**
     * While a name is being typed the slots are deaf — clicking another row
     * would take the caret off a half-typed word — but the rest of the screen
     * is not. Cancel especially: it is the only way out a mouse has, and
     * without it the naming can only be left from the keyboard.
     */
    fun clicked(x: Int, y: Int): MenuChoice? =
        entries.firstOrNull { it.contains(x - left, y - top) }
            ?.choice
            ?.takeUnless { naming != null && it is MenuChoice.UseSlot }

    fun rowOf(slot: Int): MenuEntry = entries[slot]

    companion object {
        const val LEFT = 0
        const val TOP = 0
        const val WIDTH = 176
        const val HEIGHT = 144

        const val TITLE_TOP = 5

        fun camp() = CampMenu(
            title = "Camp:",
            titleLeft = MENU_TITLE_LEFT,
            entries = menuLines(
                MenuChoice.RestParty,
                MenuChoice.DoSpellWork(CampSpellWork.MEMORIZE),
                MenuChoice.DoSpellWork(CampSpellWork.PRAY),
                MenuChoice.DoSpellWork(CampSpellWork.SCRIBE),
                MenuChoice.OpenPreferences,
                MenuChoice.OpenGameOptions,
                leaving = MenuChoice.Close,
            ),
        )

        /**
         * Who is to do it: the question written down the panel in white, a
         * portrait of each champion who could to click, the others' names
         * checkered over, and a way out.
         */
        fun whoDoesIt(asked: WhoDoesIt.Ask, greyed: Set<PartySlot>) = CampMenu(
            title = "",
            titleLeft = 0,
            says = asked.says,
            saysLeft = PROMPT_LEFT,
            saysTop = TITLE_TOP,
            greyed = greyed,
            entries = asked.able.sortedBy { it.index }.map { whose ->
                MenuEntry(
                    label = "",
                    left = PORTRAIT_BUTTON_LEFTS[whose.index % 2],
                    top = PORTRAIT_BUTTON_TOPS[whose.index / 2],
                    width = PORTRAIT_BUTTON_WIDTH,
                    height = PORTRAIT_BUTTON_HEIGHT,
                    choice = MenuChoice.ThisOneDoesIt(whose),
                    framed = false,
                )
            } + MenuEntry(
                label = MenuChoice.OpenCamp.label,
                left = LEAVE_X,
                top = LEAVE_TOP,
                width = LEAVE_WIDTH,
                height = LINE_HEIGHT,
                choice = MenuChoice.OpenCamp,
                centred = true,
            ),
        )

        /**
         * Memorize Spells or Pray for Spells: a tab a level along the top, how
         * many places are left on it, its spells a line each with how many are
         * chosen, and Clear and Exit along the bottom.
         */
        fun choosingSpells(menu: SpellChoosing, greyed: Set<PartySlot> = emptySet()) = CampMenu(
            title = CampSpellMessages.SPELLS_AVAILABLE,
            titleLeft = PROMPT_LEFT,
            greyed = greyed,
            entries = List(SpellChoosing.PAGES_SHOWN) { page ->
                MenuEntry(
                    label = "${page + 1}",
                    left = FIRST_TAB_LEFT + page * TAB_STEP,
                    top = TAB_TOP,
                    width = TAB_WIDTH,
                    height = TAB_HEIGHT,
                    choice = MenuChoice.SpellPage(page),
                    centred = true,
                    highlighted = page == menu.page,
                )
            } + listLines(menu.onThisPage.size) { MenuChoice.SpellLine(it) } + listOf(
                MenuEntry(
                    label = CampSpellMessages.CLEAR,
                    left = CLEAR_X,
                    top = LIST_BUTTONS_TOP,
                    width = CLEAR_WIDTH,
                    height = LINE_HEIGHT,
                    choice = MenuChoice.ClearSpellPage,
                    centred = true,
                ),
                MenuEntry(
                    label = MenuChoice.LeaveSpells.label,
                    left = LEAVE_X,
                    top = LIST_BUTTONS_TOP,
                    width = LEAVE_WIDTH,
                    height = LINE_HEIGHT,
                    choice = MenuChoice.LeaveSpells,
                    centred = true,
                ),
            ),
            notes = listOf(
                MenuNote(
                    text = CampSpellMessages.remaining(menu.remaining(), menu.places[menu.page]),
                    left = LIST_X,
                    top = REMAINING_TOP,
                    colour = GuiColour.LIGHT_BLUE,
                ),
            ) + menu.onThisPage.take(LIST_LINES).mapIndexed { line, spell ->
                MenuNote(
                    text = CampSpellMessages.line(
                        CampSpellMessages.menuNameOf(spell, menu.discipline),
                        menu.countOf(spell),
                    ),
                    left = LIST_X,
                    top = LIST_TOP + line * LIST_STEP,
                    colour = if (line == menu.line) GuiColour.LIGHT_RED else GuiColour.WHITE,
                )
            },
        )

        /**
         * Scribe Scrolls, once a mage is chosen: the scrolls their book lacks,
         * a line each, and Exit.
         */
        fun scribing(names: List<String>, picked: Int, greyed: Set<PartySlot> = emptySet()) = CampMenu(
            title = "",
            titleLeft = 0,
            greyed = greyed,
            says = CampSpellMessages.CHOOSE_SCROLLS,
            saysLeft = PROMPT_LEFT,
            saysTop = TITLE_TOP,
            saysColour = GuiColour.LIGHT_BLUE,
            entries = listLines(names.size) { MenuChoice.ScribeLine(it) } + MenuEntry(
                label = MenuChoice.OpenCamp.label,
                left = LEAVE_X,
                top = LIST_BUTTONS_TOP,
                width = LEAVE_WIDTH,
                height = LINE_HEIGHT,
                choice = MenuChoice.OpenCamp,
                centred = true,
            ),
            notes = names.mapIndexed { line, name ->
                MenuNote(
                    text = name,
                    left = LIST_X,
                    top = LIST_TOP + line * LIST_STEP,
                    colour = if (line == picked) GuiColour.LIGHT_RED else GuiColour.WHITE,
                )
            },
        )

        /**
         * What the game puts up in a small box of its own over a menu and takes
         * down again on a click, or by itself a moment later.
         */
        fun message(says: List<String>, over: CampMenu) = CampMenu(
            title = "",
            titleLeft = 0,
            over = over,
            left = ASKED_LEFT,
            top = ASKED_TOP,
            width = ASKED_WIDTH,
            height = ASKED_HEIGHT,
            says = says,
            saysLeft = MESSAGE_TEXT_INDENT,
            saysTop = MESSAGE_TEXT_INDENT,
            // a click anywhere on the screen is what reads it
            entries = listOf(
                MenuEntry(
                    label = "",
                    left = -ASKED_LEFT,
                    top = -ASKED_TOP,
                    width = SCREEN_WIDTH,
                    height = SCREEN_HEIGHT,
                    choice = MenuChoice.Dismiss,
                    framed = false,
                ),
            ),
        )

        /** How long a message box stays up if nobody clicks it away. */
        const val MESSAGE_LASTS_MILLIS = 1440L

        /** Invisible lines down the list, a click on each answering for that line. */
        private fun listLines(count: Int, choice: (Int) -> MenuChoice) =
            List(minOf(count, LIST_LINES)) { line ->
                MenuEntry(
                    label = "",
                    left = LIST_X,
                    top = LIST_TOP + line * LIST_STEP,
                    width = LIST_WIDTH,
                    height = LIST_STEP,
                    choice = choice(line),
                    framed = false,
                )
            }

        /**
         * The two things the player may choose, each line saying which way it
         * stands. It is titled the same as the camp menu it is opened from.
         */
        fun preferences(preferences: Preferences) = CampMenu(
            title = "Camp:",
            titleLeft = MENU_TITLE_LEFT,
            entries = menuLines(
                choices = Preferences.Setting.entries.map {
                    MenuChoice.Toggle(it, preferences.isOn(it))
                },
                leaving = MenuChoice.OpenCamp,
            ),
        )

        /**
         * The party asleep, counting the hours away.
         *
         * Nothing on it can be clicked, because nothing on it is a button:
         * anything the player does at all wakes them, which is why there is no
         * way out drawn in the corner.
         */
        fun resting(hours: Int) = CampMenu(
            title = "Resting party.",
            titleLeft = REST_TITLE_LEFT,
            entries = emptyList(),
            says = listOf("Hours rested: $hours"),
            saysLeft = REST_TITLE_LEFT,
            saysTop = FIRST_LINE_TOP,
        )

        /**
         * Asked as the party lie down hurt: a rest runs until they are mended,
         * and that is a long time to have agreed to by accident.
         */
        fun stillInjured(hours: Int) = asked(
            hours = hours,
            says = listOf("Someone is still", "injured. Rest", "until healed?"),
        )

        /**
         * Asked when the party sleep on empty stomachs, because from here on
         * the sleep costs them rather than mends them.
         */
        fun starving(hours: Int) = asked(
            hours = hours,
            says = listOf(
                "Your party is",
                "starving. Do you",
                "wish to continue",
                "resting?",
            ),
        )

        /**
         * A question put to a sleeping party: a smaller box set into the
         * resting one, the question written across it and Yes and No side by
         * side underneath. Every number here is transcribed.
         */
        private fun asked(hours: Int, says: List<String>) = CampMenu(
            title = "",
            titleLeft = 0,
            over = resting(hours),
            left = ASKED_LEFT,
            top = ASKED_TOP,
            width = ASKED_WIDTH,
            height = ASKED_HEIGHT,
            says = says,
            saysLeft = ASKED_TEXT_LEFT,
            saysTop = ASKED_TEXT_TOP,
            entries = listOf(
                answer(MenuChoice.KeepResting, ASKED_YES_LEFT),
                answer(MenuChoice.StopResting, ASKED_NO_LEFT),
            ),
        )

        /** One of the two answers, side by side under the question. */
        private fun answer(choice: MenuChoice, left: Int) = MenuEntry(
            label = choice.label,
            left = left,
            top = ASKED_ANSWER_TOP,
            width = ASKED_ANSWER_WIDTH,
            height = LINE_HEIGHT,
            choice = choice,
        )

        fun gameOptions() = CampMenu(
            title = "Game Options:",
            titleLeft = MENU_TITLE_LEFT,
            entries = menuLines(
                MenuChoice.OpenSlots(saving = false),
                MenuChoice.OpenSlots(saving = true),
                MenuChoice.DropCharacter,
                MenuChoice.NotYet("Quit Game"),
                leaving = MenuChoice.OpenCamp,
            ),
        )

        /**
         * Who the party are to send away, a champion to a line, with a way out
         * where the save list keeps its Cancel.
         *
         * Everybody in the party is offered whatever state they are in. The
         * dead are the point of it: somebody past raising is carried about as
         * a pack and dropped when the party would rather have the room.
         */
        fun whoLeaves(names: List<String>) = CampMenu(
            title = DROP_TITLE,
            titleLeft = SLOT_TITLE_LEFT,
            entries = names.mapIndexed { slot, name ->
                MenuEntry(
                    label = name,
                    left = SLOT_X,
                    top = FIRST_LINE_TOP + slot * LINE_STEP,
                    width = SLOT_WIDTH,
                    height = LINE_HEIGHT,
                    choice = MenuChoice.DropThisOne(PartySlot(slot)),
                )
            } + MenuEntry(
                label = "Cancel",
                left = CANCEL_X,
                top = CANCEL_TOP,
                width = CANCEL_WIDTH,
                height = LINE_HEIGHT,
                choice = MenuChoice.OpenGameOptions,
            ),
        )

        /**
         * What a party too small to lose anybody are told instead. Written as
         * the game writes it, three short lines in a box of its own.
         */
        fun tooFewToDrop() = CampMenu(
            title = DROP_TITLE,
            titleLeft = SLOT_TITLE_LEFT,
            entries = listOf(
                MenuEntry(
                    label = "Cancel",
                    left = CANCEL_X,
                    top = CANCEL_TOP,
                    width = CANCEL_WIDTH,
                    height = LINE_HEIGHT,
                    choice = MenuChoice.OpenGameOptions,
                ),
            ),
            says = listOf("You cannot have", "less than four", "characters."),
            saysLeft = SLOT_X,
            saysTop = FIRST_LINE_TOP,
        )

        /**
         * What the box is headed. The game puts its question in three lines
         * of a box of its own and then has the player click a portrait; this
         * asks the same thing the way the save list asks its own.
         */
        private const val DROP_TITLE = "Drop Character:"

        /**
         * How few the party may not go below. Four is the game's number and
         * not a round one: it is checked before the question is put, so a
         * party of four are told rather than asked.
         */
        const val NEVER_FEWER_THAN = 4

        /**
         * The six slots, each showing what was saved in it or that it is
         * empty. Saving offers every slot; loading offers only the ones with
         * something in them, since there is nothing to read out of the rest.
         */
        fun slots(saving: Boolean, describedBy: (Int) -> String?) = CampMenu(
            title = if (saving) "Save Game" else "Load Game",
            titleLeft = SLOT_TITLE_LEFT,
            entries = (0 until SLOTS).map { slot ->
                val description = describedBy(slot)
                MenuEntry(
                    label = description ?: EMPTY_SLOT,
                    left = SLOT_X,
                    top = FIRST_LINE_TOP + slot * LINE_STEP,
                    width = SLOT_WIDTH,
                    height = LINE_HEIGHT,
                    choice = when {
                        saving || description != null -> MenuChoice.UseSlot(slot, saving)
                        else -> MenuChoice.NotYet(EMPTY_SLOT)
                    },
                )
            } + MenuEntry(
                label = "Cancel",
                left = CANCEL_X,
                top = CANCEL_TOP,
                width = CANCEL_WIDTH,
                height = LINE_HEIGHT,
                choice = MenuChoice.OpenGameOptions,
            ),
        )

        private fun menuLines(vararg choices: MenuChoice, leaving: MenuChoice) =
            menuLines(choices.toList(), leaving)

        private fun menuLines(choices: List<MenuChoice>, leaving: MenuChoice) =
            choices.mapIndexed { line, choice ->
                MenuEntry(
                    label = choice.label,
                    left = MENU_X,
                    top = FIRST_LINE_TOP + line * LINE_STEP,
                    width = MENU_WIDTH,
                    height = LINE_HEIGHT,
                    choice = choice,
                )
            } + MenuEntry(
                label = leaving.label,
                left = LEAVE_X,
                top = LEAVE_TOP,
                width = LEAVE_WIDTH,
                height = LINE_HEIGHT,
                choice = leaving,
            )

        private const val SLOTS = 6
        private const val EMPTY_SLOT = "Empty Slot"

        private const val FIRST_LINE_TOP = 20
        private const val LINE_STEP = 17
        private const val LINE_HEIGHT = 14

        private const val MENU_TITLE_LEFT = 5

        /** Where the resting box writes its title and its count of the hours. */
        private const val REST_TITLE_LEFT = 8

        // The box a question is asked in, and the two answers under it.
        private const val ASKED_LEFT = 8
        private const val ASKED_TOP = 20
        private const val ASKED_WIDTH = 160
        private const val ASKED_HEIGHT = 56
        private const val ASKED_TEXT_LEFT = 8
        private const val ASKED_TEXT_TOP = 4
        private const val ASKED_ANSWER_TOP = 37
        private const val ASKED_ANSWER_WIDTH = 32
        private const val ASKED_YES_LEFT = 8
        private const val ASKED_NO_LEFT = 120
        private const val MENU_X = 12
        private const val MENU_WIDTH = 158
        private const val LEAVE_X = 128
        private const val LEAVE_TOP = 122
        private const val LEAVE_WIDTH = 40

        // Where the spell menus put things.
        private const val PROMPT_LEFT = 5
        private const val FIRST_TAB_LEFT = 11
        private const val TAB_STEP = 27
        private const val TAB_TOP = 16
        private const val TAB_WIDTH = 20
        private const val TAB_HEIGHT = 18
        private const val REMAINING_TOP = 38
        private const val LIST_X = 8
        private const val LIST_TOP = 50
        private const val LIST_STEP = 9
        private const val LIST_WIDTH = 160
        private const val LIST_LINES = 8
        private const val LIST_BUTTONS_TOP = 126
        private const val CLEAR_X = 8
        private const val CLEAR_WIDTH = 48
        private const val MESSAGE_TEXT_INDENT = 5

        // A click on a portrait, over the champion boxes down the right.
        private val PORTRAIT_BUTTON_LEFTS = listOf(184, 256)
        private val PORTRAIT_BUTTON_TOPS = listOf(0, 56, 112)
        private const val PORTRAIT_BUTTON_WIDTH = 64
        private const val PORTRAIT_BUTTON_HEIGHT = 48

        private const val SCREEN_WIDTH = 320
        private const val SCREEN_HEIGHT = 200

        private const val SLOT_TITLE_LEFT = 52
        private const val SLOT_X = 4
        private const val SLOT_WIDTH = 167
        private const val CANCEL_X = 118
        private const val CANCEL_TOP = 126
        private const val CANCEL_WIDTH = 53
    }
}

/** Words at a place of their own on a menu, in a colour of their own. */
data class MenuNote(val text: String, val left: Int, val top: Int, val colour: GuiColour)

data class MenuEntry(
    val label: String,
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int,
    val choice: MenuChoice,
    /** Drawn as a button, or only a place that answers a click — a portrait, a line of a list. */
    val framed: Boolean = true,
    /** The label in the middle of the button rather than at its left. */
    val centred: Boolean = false,
    /** The label in light red: the page a menu is on. */
    val highlighted: Boolean = false,
) {
    fun contains(x: Int, y: Int) = x in left until left + width && y in top until top + height

    val labelLeft: Int get() = left + LABEL_X
    val labelTop: Int get() = top + LABEL_Y

    private companion object {
        const val LABEL_X = 4
        const val LABEL_Y = 3
    }
}

sealed class MenuChoice(val label: String) {
    data object Close : MenuChoice("Exit")
    data object OpenCamp : MenuChoice("Exit")
    data object OpenGameOptions : MenuChoice("Game Options")
    data object OpenPreferences : MenuChoice("Preferences")

    /** @param on which way the setting stands now, which is what the line says. */
    data class Toggle(val setting: Preferences.Setting, val on: Boolean) :
        MenuChoice(setting.saying(on))

    data class OpenSlots(val saving: Boolean) : MenuChoice(if (saving) "Save Game" else "Load Game")
    data class UseSlot(val slot: Int, val saving: Boolean) : MenuChoice("")

    data object RestParty : MenuChoice("Rest Party")

    /** The line that counts the hours away; there is nothing to do with it. */
    data class HoursRested(val hours: Int) : MenuChoice("Hours rested: $hours")

    /** The two answers a starving party give, which is all a rest ever asks. */
    data object KeepResting : MenuChoice("Yes")

    data object StopResting : MenuChoice("No")

    /** Memorize Spells, Pray for Spells, Scribe Scrolls. */
    data class DoSpellWork(val work: CampSpellWork) : MenuChoice(work.label)

    /** The champion clicked as the one to do it. */
    data class ThisOneDoesIt(val whose: PartySlot) : MenuChoice("")

    data class SpellPage(val page: Int) : MenuChoice("${page + 1}")

    /** A line of the spells offered, which a click adds one of. */
    data class SpellLine(val line: Int) : MenuChoice("")

    data object ClearSpellPage : MenuChoice(CampSpellMessages.CLEAR)

    data object LeaveSpells : MenuChoice("Exit")

    /** A line of the scrolls offered, which a click copies. */
    data class ScribeLine(val line: Int) : MenuChoice("")

    /** A message box read, or left to time out. */
    data object Dismiss : MenuChoice("")

    data object DropCharacter : MenuChoice("Drop Character")

    /** Which of the six the party are sending away. */
    data class DropThisOne(val whose: PartySlot) : MenuChoice("")

    /**
     * A line that is drawn and can be clicked but does nothing yet. Choosing
     * one says so in the message bar rather than only in the log, so it is
     * plain from inside the game that the gap is a gap.
     */
    data class NotYet(val what: String) : MenuChoice(what)
}
