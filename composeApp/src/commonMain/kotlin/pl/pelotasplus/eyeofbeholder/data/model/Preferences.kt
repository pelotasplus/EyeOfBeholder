package pl.pelotasplus.eyeofbeholder.data.model

import kotlinx.serialization.Serializable

/**
 * What the game lets the player choose, which lives under Camp → Preferences.
 *
 * Each one reads as its own state rather than as a setting with a value: the
 * line says "Sounds are ON", and clicking it makes it say OFF. That is why
 * there is no separate label — the line is the whole of it.
 *
 * These are the player's rather than the party's, and they are saved all the
 * same: coming back to a game with the sound on again, or the numbers turned
 * back into bars, is the game forgetting something the player said.
 */
@Serializable
data class Preferences(
    /**
     * Nothing plays yet, so this is a switch with nothing behind it. It is
     * here because the menu it belongs to is: the menu has these two lines,
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

    /** The lines of the menu, in the order they are listed. */
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
