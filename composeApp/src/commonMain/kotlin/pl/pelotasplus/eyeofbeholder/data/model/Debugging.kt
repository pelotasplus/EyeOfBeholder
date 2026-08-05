package pl.pelotasplus.eyeofbeholder.data.model

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Switches that belong to whoever is building the game rather than to whoever
 * is playing it, held in one place so the Debug menu and the play field agree
 * about them.
 *
 * These are not [Preferences]. A preference is the player's and the original
 * offers it; this is scaffolding, and a finished game would have none of it.
 */
class Debugging {

    private val _wallsArePassable = MutableStateFlow(true)

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
}
