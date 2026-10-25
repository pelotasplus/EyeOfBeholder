package pl.pelotasplus.eyeofbeholder.agent

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * What an agent playing can do: the things a player does, by what they mean
 * rather than where they are on the screen. Each is done the way the
 * player's own click or key does it, so it is turned down for the same
 * reasons. [Click] and [Use] are for whatever has no name here yet.
 *
 * Champions are counted by their place in the party, 0 to 5; hands are 0 for
 * the upper slot of a box and 1 for the lower.
 */
@Serializable
sealed interface AgentAction {

    @Serializable
    @SerialName("step")
    data class Step(val way: Way) : AgentAction

    @Serializable
    @SerialName("turn")
    data class Turn(val toThe: Side) : AgentAction

    /** Every champion in the front rank swings what they hold. */
    @Serializable
    @SerialName("front_rank_strikes")
    data object FrontRankStrikes : AgentAction

    /** What a right click on a hand does: swing, read, drink, eat, open the book. */
    @Serializable
    @SerialName("use_hand")
    data class UseHand(val champion: Int, val hand: Int) : AgentAction

    /** What a left click on a hand does: what is held changes places with what is on the pointer. */
    @Serializable
    @SerialName("take_hand")
    data class TakeHand(val champion: Int, val hand: Int) : AgentAction

    /** Opens the camp, or shuts it if it is open. */
    @Serializable
    @SerialName("camp")
    data object Camp : AgentAction

    /** A line of the open menu, by the words on it. */
    @Serializable
    @SerialName("choose")
    data class Choose(val label: String) : AgentAction

    /** A button of the conversation on screen, counted from 0. */
    @Serializable
    @SerialName("answer")
    data class Answer(val button: Int) : AgentAction

    /** The champion a spell waiting to be told whom it is for is cast on. */
    @Serializable
    @SerialName("cast_on")
    data class CastOn(val champion: Int) : AgentAction

    @Serializable
    @SerialName("book_tab")
    data class BookTab(val page: Int) : AgentAction

    /** Picks a line of the open book and casts it. */
    @Serializable
    @SerialName("book_line")
    data class BookLine(val line: Int) : AgentAction

    @Serializable
    @SerialName("book_abort")
    data object BookAbort : AgentAction

    /** A left click anywhere, in the screen's own 320×200. */
    @Serializable
    @SerialName("click")
    data class Click(val x: Int, val y: Int) : AgentAction

    /** A right click anywhere, in the screen's own 320×200. */
    @Serializable
    @SerialName("use")
    data class Use(val x: Int, val y: Int) : AgentAction

    @Serializable
    enum class Way { FORWARD, BACK, LEFT, RIGHT }

    @Serializable
    enum class Side { LEFT, RIGHT }
}
