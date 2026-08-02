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
 * The strings and every coordinate here come from the original game.
 */
data class CampMenu(
    val title: String,
    val titleLeft: Int,
    val entries: List<MenuEntry>,
    /** The slot being named, which takes the keyboard while it is. */
    val naming: Naming? = null,
) {
    /** A click means nothing while a name is being typed; the keys have it. */
    fun clicked(x: Int, y: Int): MenuChoice? =
        if (naming != null) null else entries.firstOrNull { it.contains(x, y) }?.choice

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
                MenuChoice.NotYet("Rest Party"),
                MenuChoice.NotYet("Memorize Spells"),
                MenuChoice.NotYet("Pray for Spells"),
                MenuChoice.NotYet("Scribe Scrolls"),
                MenuChoice.NotYet("Preferences"),
                MenuChoice.OpenGameOptions,
                leaving = MenuChoice.Close,
            ),
        )

        fun gameOptions() = CampMenu(
            title = "Game Options:",
            titleLeft = MENU_TITLE_LEFT,
            entries = menuLines(
                MenuChoice.OpenSlots(saving = false),
                MenuChoice.OpenSlots(saving = true),
                MenuChoice.NotYet("Drop Character"),
                MenuChoice.NotYet("Quit Game"),
                leaving = MenuChoice.OpenCamp,
            ),
        )

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
        private const val MENU_X = 12
        private const val MENU_WIDTH = 158
        private const val LEAVE_X = 128
        private const val LEAVE_TOP = 122
        private const val LEAVE_WIDTH = 40

        private const val SLOT_TITLE_LEFT = 52
        private const val SLOT_X = 4
        private const val SLOT_WIDTH = 167
        private const val CANCEL_X = 118
        private const val CANCEL_TOP = 126
        private const val CANCEL_WIDTH = 53
    }
}

data class MenuEntry(
    val label: String,
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int,
    val choice: MenuChoice,
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
    data class OpenSlots(val saving: Boolean) : MenuChoice(if (saving) "Save Game" else "Load Game")
    data class UseSlot(val slot: Int, val saving: Boolean) : MenuChoice("")

    data class NotYet(val what: String) : MenuChoice(what)
}
