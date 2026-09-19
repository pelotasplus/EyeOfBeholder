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

    private val _menuIsOpen = MutableStateFlow(false)

    /**
     * Whether the panel of these switches is open.
     *
     * Kept here rather than in the composition that draws it because the view
     * reads it too: where the party are standing is written over the corner of
     * the screen for whoever is building the game, and it belongs there only
     * while the rest of the scaffolding is on show.
     */
    val menuIsOpen: StateFlow<Boolean> = _menuIsOpen.asStateFlow()

    fun openMenu(open: Boolean) {
        _menuIsOpen.value = open
    }

    private val _whereTheyStand = MutableStateFlow<String?>(null)

    /**
     * Where the party are, written for whoever is building the game.
     *
     * It is read in the panel rather than over the corner of the view, which
     * is the one place it cannot be covered by the panel itself — and it is
     * the view that knows it, so it is left here on the way past.
     */
    val whereTheyStand: StateFlow<String?> = _whereTheyStand.asStateFlow()

    fun standingAt(where: String?) {
        _whereTheyStand.value = where
    }

    private val _showingMap = MutableStateFlow(false)

    /**
     * Whether the little map of where the party have been is drawn.
     *
     * Off, because the game has none and finding the way is most of what a
     * floor is. It is the quickest way to see where a party have actually
     * been when a floor is not behaving, which is what it is for.
     */
    val showingMap: StateFlow<Boolean> = _showingMap.asStateFlow()

    fun showMap(show: Boolean) {
        _showingMap.value = show
    }

    private val _pickingALevel = MutableStateFlow(false)

    /**
     * Whether the list of floors is up over the game.
     *
     * Over it rather than instead of it: the dungeon is a screen of its own,
     * and taking it away to show a list takes the party with it — what holds
     * them belongs to the screen, and putting a fresh one back hands over six
     * strangers at the entrance. Laid on top, nothing is taken away.
     */
    val pickingALevel: StateFlow<Boolean> = _pickingALevel.asStateFlow()

    fun pickALevel(picking: Boolean) {
        _pickingALevel.value = picking
    }

    private val _jumpWanted = MutableStateFlow<Jump?>(null)

    /**
     * A floor picked off the Levels screen, waiting to be gone to.
     *
     * It is left here rather than carried in the address of a new screen,
     * because a new screen is a new game: the thing holding the party is tied
     * to the one being shown, so replacing it hands back six strangers with
     * nothing in their pockets. Left here, the screen showing the party can
     * read it and take them there itself.
     */
    val jumpWanted: StateFlow<Jump?> = _jumpWanted.asStateFlow()

    fun jumpTo(level: String, entryPoint: LevelEntryPoint?) {
        _jumpWanted.value = Jump(
            level = level,
            x = entryPoint?.location?.x,
            y = entryPoint?.location?.y,
            facing = entryPoint?.direction,
        )
    }

    /** Taken, so that coming back to the screen does not take it again. */
    fun jumpTaken() {
        _jumpWanted.value = null
    }

    /** Where the Levels screen asked for the party to be put. */
    data class Jump(
        val level: String,
        val x: Int?,
        val y: Int?,
        val facing: Direction?,
    )

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
