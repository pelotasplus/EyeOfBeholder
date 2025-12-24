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
}
