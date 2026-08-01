package pl.pelotasplus.eyeofbeholder.navigation

import kotlinx.serialization.Serializable

sealed interface Route {

    @Serializable
    data object DecDebug : Route

    @Serializable
    data object PalDebug : Route

    @Serializable
    data object CpsDebug : Route

    @Serializable
    data object InfDebug : Route

    @Serializable
    data object MazDebug : Route

    @Serializable
    data object VcnDebug : Route

    @Serializable
    data object LevelsDebug : Route

    /**
     * The game view. [level] is the INF file to render; null means the
     * view model's own default level and start position.
     */
    @Serializable
    data class ViewConeDebug(val level: String? = null) : Route
}
