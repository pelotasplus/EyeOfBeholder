package pl.pelotasplus.eyeofbeholder.navigation

import kotlinx.serialization.Serializable

sealed interface Route {

    @Serializable
    data object DebugGraph : Route

    @Serializable
    data object MainDebug : Route

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
    data object VmpDebug : Route

    @Serializable
    data object ViewConeDebug : Route
}
