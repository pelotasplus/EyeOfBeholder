package pl.pelotasplus.eyeofbeholder.data.model

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Switches that belong to whoever is building the game rather than to whoever
 * is playing it, held in one place so the Debug menu and the play field agree
 * about them.
 *
 * These are not [Preferences]. A preference is the player's and the game
 * offers it; this is scaffolding, and a finished game would have none of it.
 */
class Debugging {

    private val _wallsArePassable = MutableStateFlow(false)

    /**
     * Whether the party walk through walls.
     *
     * On by default, because crossing a level in a straight line is how a
     * renderer gets looked at. Turned off, walls stop the party and say so —
     * which is the game, and is also the only way to find out that a wall the
     * party should have been stopped by was never solid.
     */
    val wallsArePassable: StateFlow<Boolean> = _wallsArePassable.asStateFlow()

    fun passWalls(may: Boolean) {
        _wallsArePassable.value = may
    }

    private val _monstersMayWalk = MutableStateFlow(false)

    /**
     * Whether monsters go anywhere, or fight only from where they were placed.
     *
     * Off by default, which is the opposite of the game: something that walks
     * arrives while a scene is being looked at, and every other switch here
     * exists so that what is on the screen stays still long enough to be read.
     * Turned on, they hunt.
     */
    val monstersMayWalk: StateFlow<Boolean> = _monstersMayWalk.asStateFlow()

    fun letMonstersWalk(may: Boolean) {
        _monstersMayWalk.value = may
    }

    private val _showingMap = MutableStateFlow(true)

    /** Whether the little map of where the party have been is drawn. */
    val showingMap: StateFlow<Boolean> = _showingMap.asStateFlow()

    fun showMap(show: Boolean) {
        _showingMap.value = show
    }

    private val _mapSize = MutableStateFlow(MapSize.NORMAL)

    /** How big that map is drawn. */
    val mapSize: StateFlow<MapSize> = _mapSize.asStateFlow()

    fun sizeMap(to: MapSize) {
        _mapSize.value = to
    }

    /**
     * How big the little map is drawn, as a side in the 320×200 screen's own
     * space — so it scales with the window like everything else does.
     *
     * The whole 32×32 maze is fitted to whichever box this names, so a bigger
     * one is a bigger square rather than more of the maze.
     */
    enum class MapSize(val side: Int, val reads: String) {
        SMALL(38, "small"),
        NORMAL(50, "normal"),
        LARGE(60, "large"),
        HUGE(75, "huge");

        /** The next one round, so one control can walk the whole list. */
        val next: MapSize get() = entries[(ordinal + 1) % entries.size]
    }
}
