package pl.pelotasplus.eyeofbeholder.data.model

/**
 * What the game lets the player choose, which lives under Camp → Preferences.
 *
 * Each one reads as its own state rather than as a setting with a value: the
 * line says "Sounds are ON", and clicking it makes it say OFF. That is why
 * there is no separate label — the line is the whole of it.
 *
 * These are the player's, not the party's, so they are no part of a saved
 * game. Nothing writes them down yet either, so they last as long as the tab
 * does.
 */
data class Preferences(
    /**
     * Nothing plays yet, so this is a switch with nothing behind it. It is
     * here because the menu it belongs to is: the original offers these two,
     * and a Preferences menu with one line on it is not that menu.
     */
    val sounds: Boolean = true,
    /**
     * Whether hit points are drawn as a bar or written out. The party's boxes
     * and a champion's page both follow it.
     */
    val barGraphs: Boolean = true,
) {
    fun isOn(setting: Setting): Boolean = when (setting) {
        Setting.SOUNDS -> sounds
        Setting.BAR_GRAPHS -> barGraphs
    }

    fun toggling(setting: Setting): Preferences = when (setting) {
        Setting.SOUNDS -> copy(sounds = !sounds)
        Setting.BAR_GRAPHS -> copy(barGraphs = !barGraphs)
    }

    /** The lines of the menu, in the order the original lists them. */
    enum class Setting(val reads: String) {
        SOUNDS("Sounds are"),
        BAR_GRAPHS("Bar Graphs are");

        fun saying(on: Boolean) = "$reads ${if (on) ON else OFF}"

        private companion object {
            const val ON = "ON"
            const val OFF = "OFF"
        }
    }
}
