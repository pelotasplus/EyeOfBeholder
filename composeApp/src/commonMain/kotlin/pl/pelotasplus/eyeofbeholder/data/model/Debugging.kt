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

    private val _monstersMayWalk = MutableStateFlow(true)

    /**
     * Whether monsters go anywhere, or fight only from where they were placed.
     *
     * On by default, because a floor that stands still is not the game and no
     * longer even plays like it: some of what a floor does needs something
     * wandering about, and a plate held down by a monster's weight cannot be
     * worked any other way. Turned off, they stay where they were placed,
     * which is how a scene is held long enough to be read.
     */
    val monstersMayWalk: StateFlow<Boolean> = _monstersMayWalk.asStateFlow()

    fun letMonstersWalk(may: Boolean) {
        _monstersMayWalk.value = may
    }

    private val _everythingIdentified = MutableStateFlow(false)

    /**
     * Whether every item reads by its true name, known or not.
     *
     * Off, because knowing what a thing is before picking it up is most of
     * what an unidentified item is for. On, a pack can be searched for one
     * particular thing without turning the dungeon out. It changes nothing
     * but what things are called — not what they do, not what is cursed, not
     * what is stuck to its slot.
     */
    val everythingIdentified: StateFlow<Boolean> = _everythingIdentified.asStateFlow()

    fun identifyEverything(all: Boolean) {
        _everythingIdentified.value = all
    }

    private val _showingMap = MutableStateFlow(true)

    /** Whether the little map of where the party have been is drawn. */
    val showingMap: StateFlow<Boolean> = _showingMap.asStateFlow()

    fun showMap(show: Boolean) {
        _showingMap.value = show
    }

    companion object {
        /**
         * How big the little map is drawn, as a side in the 320×200 screen's
         * own space — so it scales with the window like everything else does.
         *
         * The whole 32×32 maze is fitted into it, so this is how big the
         * square is and not how much of the maze it holds.
         */
        const val MAP_SIDE = 75
    }
}
