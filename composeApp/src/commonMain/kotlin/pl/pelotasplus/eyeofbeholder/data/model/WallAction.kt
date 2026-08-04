package pl.pelotasplus.eyeofbeholder.data.model

/**
 * What a wall does when it is clicked.
 *
 * A decoration carries a number saying which of these it is, and until it is
 * named that number tells a reader — and a log — nothing at all.
 */
enum class WallAction {
    /** Nothing hangs here that answers a click. */
    NOTHING,

    /** The button beside a door, which opens and shuts it. */
    DOOR_SWITCH,

    /** A shape on the wall that does something when it is pressed. */
    SHAPE,

    LEVER_ON,
    LEVER_OFF,

    /** A door stuck in its frame, which can be forced. */
    STUCK_DOOR,

    /** A door stuck in its frame that no amount of forcing will move. */
    JAMMED_DOOR,

    /**
     * A wall whose whole answer is its script, so it does not care where on
     * it the click landed.
     */
    SCRIPT_ONLY,

    /** A shelf set into the wall, which a thing can be taken from or put on. */
    NICHE;

    /** Whether a click anywhere on the wall counts, rather than only on the shape. */
    val answersAnyClick: Boolean get() = this == SCRIPT_ONLY

    /**
     * Whether this is a door to be shoved rather than a shape to be hit. What
     * answers is the doorway — the middle of the view — since the party push
     * the door itself and not the picture of one hanging on it.
     */
    val isShoved: Boolean get() = this == STUCK_DOOR || this == JAMMED_DOOR

    companion object {
        fun of(specialType: Int): WallAction = when (specialType) {
            1 -> DOOR_SWITCH
            2, 8 -> SHAPE
            3 -> LEVER_ON
            4 -> LEVER_OFF
            5 -> STUCK_DOOR
            6 -> JAMMED_DOOR
            7, 9 -> SCRIPT_ONLY
            10 -> NICHE
            else -> NOTHING
        }
    }
}

/** What the wall the party are facing does when it is clicked. */
val Decoration.doesWhenClicked: WallAction get() = WallAction.of(specialType)
