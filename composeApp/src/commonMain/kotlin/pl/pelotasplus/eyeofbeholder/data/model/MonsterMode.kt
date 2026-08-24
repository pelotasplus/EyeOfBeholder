package pl.pelotasplus.eyeofbeholder.data.model

/**
 * What a monster does with a turn, which a level chooses for each one it
 * places rather than for a kind or a floor.
 *
 * [HUNTING] is where they all end up: noticing the party sets it, being roused
 * sets it, and one of a group being struck sets it for the whole group. The
 * rest are what something does *until* it notices.
 */
enum class MonsterMode(val asWritten: Int) {
    /** Going after the party, and swinging when it gets there. */
    HUNTING(0),

    /** Up and down: when the way ahead shuts, it turns right round. */
    PACING(1),

    /** Along the wall on its left, turning that way when stopped. */
    FOLLOWING_LEFT(2),

    /** And on its right. */
    FOLLOWING_RIGHT(3),

    /**
     * Standing where it was put until the party come near, and hunting from
     * then on. The commonest mode in the game after hunting itself: most of
     * what a floor holds is asleep rather than patrolling.
     */
    ASLEEP(4),

    /** The same two as above, but also turning off into an opening it passes. */
    STRAYING_LEFT(5),
    STRAYING_RIGHT(6),

    /**
     * Waiting to see what the party do: deaf and harmless until somebody hits
     * it, and then the whole group of them turns at once. The mode a level
     * gives something that talks before it fights.
     */
    WAITING_TO_SEE(8),

    /**
     * Held, and not to be woken by anything the party do. Several numbers mean
     * it — one of them counts down a spell — and none is told apart yet.
     */
    HELD(-1);

    /** Whether it goes anywhere of its own accord. */
    val wanders: Boolean get() = this !in setOf(ASLEEP, WAITING_TO_SEE, HELD)

    /**
     * Whether the party walking past sets it hunting.
     *
     * Nearly everything: something asleep wakes, and a patrol drops its round.
     * Only what is waiting to see, and what is held, will not be started this
     * way — the first has to be struck, and the second has to run out.
     */
    val noticesTheParty: Boolean get() = this != WAITING_TO_SEE && this != HELD

    /**
     * Which way it turns when the way ahead is shut, in quarter turns. Pacing
     * turns right round; the others turn to the side they follow.
     */
    val turnsBy: Int
        get() = when (this) {
            PACING -> 2
            FOLLOWING_LEFT, STRAYING_LEFT -> -1
            FOLLOWING_RIGHT, STRAYING_RIGHT -> 1
            else -> 0
        }

    /** Which side it looks down as it goes, for the two that stray. */
    val straysTowards: Int?
        get() = when (this) {
            STRAYING_LEFT -> -1
            STRAYING_RIGHT -> 1
            else -> null
        }

    companion object {
        fun of(asWritten: Int) = entries.firstOrNull { it.asWritten == asWritten } ?: HELD
    }
}

/**
 * Where a straying monster is in its own small loop of looking about.
 *
 * It is one byte, and the whole of what makes straying
 * different from following a wall: without it a monster only ever turns when
 * something stops it, and never takes the opening it walks past.
 */
enum class Straying {
    /** It stepped forward last turn, so an opening beside it is worth taking. */
    WENT_FORWARD,

    /** It turned away from something and has not moved since. */
    TURNED_AWAY,

    /** It has just committed to a way, and only wants to walk. */
    SETTLED,
}
