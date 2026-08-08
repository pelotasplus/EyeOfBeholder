package pl.pelotasplus.eyeofbeholder.data.model

/**
 * Putting a shoulder to a door that has stuck in its frame.
 *
 * The strongest of the party who can act tries it, and whether it gives is one
 * throw of a twenty-sided die against their strength. Nobody able to try is
 * answered rather than rolled for.
 */
object ForcingADoor {

    /** What came of it, and the line the game writes for each. */
    sealed interface Outcome {
        val says: String

        /** Nobody is standing, awake and free to put a shoulder to anything. */
        data object NobodyCan : Outcome {
            override val says = "You are not capable of forcing the door."
        }

        data object Gives : Outcome {
            override val says = "You force the door."
        }

        data object Holds : Outcome {
            override val says = "You try to force the door but fail."
        }
    }

    fun tried(by: List<Champion>, dice: Dice): Outcome {
        val strongest = by.filter { it.canAct }
            .maxByOrNull { it.abilities.strength.current }
            ?: return Outcome.NobodyCan

        val strength = strongest.abilities.strength.current.coerceAtMost(STRONGEST)

        return if (dice.roll(1, 20, 0) < chanceInTwenty[strength]) Outcome.Gives
        else Outcome.Holds
    }

    /**
     * The chance in twenty of forcing a door, by the strength of whoever puts
     * their shoulder to it: one at the weakest, twelve at eighteen. Anything
     * above eighteen counts as eighteen, so the last of these is out of reach
     * of any champion.
     */
    private val chanceInTwenty =
        listOf(1, 1, 2, 3, 3, 4, 4, 5, 5, 6, 6, 7, 7, 8, 8, 9, 10, 11, 12, 13)

    private const val STRONGEST = 18
}

/** The lines the game writes about a door that will not simply open. */
object DoorMessages {

    /** A door that no strength will move, as against one that has only stuck. */
    const val NO_ONE_CAN_PRY = "No one is able to pry this door open."
}

/**
 * A door on its way open or shut, which keeps going after whatever started it
 * has finished.
 */
data class Swinging(
    val level: Int,
    val at: Location,
    val side: WallSide,
    val opening: Boolean,
)

/** A tick of every door in motion: the world after it, and what was heard. */
data class DoorsStepped(val world: GameState, val heard: List<TrackIndex>)

/**
 * What a door is heard doing.
 *
 * One sounds at every position the door passes through rather than once for
 * the whole travel, which is what makes a stone door grind its way up instead
 * of clicking and then moving in silence. The last position of a door coming
 * down is the one it lands on, and lands with a different noise.
 */
object DoorSounds {

    private val SLIDING_UP = TrackIndex(3)
    private val SLIDING_DOWN = TrackIndex(4)
    private val LANDING_SHUT = TrackIndex(5)

    /** @param arriving whether this is the last position of the travel */
    fun of(opening: Boolean, arriving: Boolean): TrackIndex = when {
        opening -> SLIDING_UP
        arriving -> LANDING_SHUT
        else -> SLIDING_DOWN
    }
}
