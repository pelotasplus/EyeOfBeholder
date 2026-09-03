package pl.pelotasplus.eyeofbeholder.navigation

import kotlinx.serialization.Serializable
import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.LevelEntryPoint

sealed interface Route {

    @Serializable
    data object CpsDebug : Route

    @Serializable
    data object LevelsDebug : Route

    /**
     * The game view. [level] is the INF file to render; null means the
     * view model's own default level and start position. [startX], [startY]
     * and [startDirection] say where the party appears; each one left null
     * keeps what the party already had.
     *
     * [startDirection] is a [Direction] name rather than the enum itself:
     * outside Android the navigation library has no NavType for an enum and
     * refuses to build the graph.
     */
    @Serializable
    data class ViewConeDebug(
        val level: String? = null,
        val startX: Int? = null,
        val startY: Int? = null,
        val startDirection: String? = null,
    ) : Route {
        constructor(level: String?, entryPoint: LevelEntryPoint?) : this(
            level = level,
            startX = entryPoint?.location?.x,
            startY = entryPoint?.location?.y,
            startDirection = entryPoint?.direction?.name,
        )

        val startFacing: Direction?
            get() = startDirection?.let(Direction::valueOf)
    }
}
