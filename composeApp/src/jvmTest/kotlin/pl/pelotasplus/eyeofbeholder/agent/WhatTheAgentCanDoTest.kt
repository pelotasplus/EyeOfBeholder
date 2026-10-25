package pl.pelotasplus.eyeofbeholder.agent

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.experimental.categories.Category
import pl.pelotasplus.eyeofbeholder.NeedsGameData
import pl.pelotasplus.eyeofbeholder.data.ThePlayField
import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.Location
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * An agent doing what a player does, by name, and being answered as the
 * player's own click or key would be.
 */
@Category(NeedsGameData::class)
class WhatTheAgentCanDoTest {

    private fun field(level: String, at: Location, facing: Direction): ThePlayField = runBlocking {
        ThePlayField().apply {
            opened(level, at, facing)
            until("the party") { viewModel.state.value.game.champions.isNotEmpty() }
        }
    }

    private val ThePlayField.told get() = requireNotNull(viewModel.observation())

    /** A clear stretch of the twelfth floor, looking down it. */
    @Test
    fun `a step moves the party`() = runBlocking {
        val field = field("LEVEL12.INF", Location(19, 15), Direction.SOUTH)

        assertNull(field.viewModel.act(AgentAction.Step(AgentAction.Way.FORWARD)))
        field.until("a step south") { field.told.at == Location(19, 16) }
    }

    @Test
    fun `a turn turns the party`() = runBlocking {
        val field = field("LEVEL12.INF", Location(19, 15), Direction.SOUTH)

        assertNull(field.viewModel.act(AgentAction.Turn(AgentAction.Side.RIGHT)))
        field.until("a turn to the west") { field.told.facing == Direction.WEST }
    }

    /**
     * A party take four ticks over a step, and nothing else moves them
     * until it is done. Told rather than dropped; and on the agent's time,
     * the step ends when the agent lets the time pass — and not before,
     * however long it takes to decide.
     */
    @Test
    fun `what cannot be done yet is said, and done once time has passed`() = runBlocking {
        val field = field("LEVEL12.INF", Location(19, 15), Direction.SOUTH)
        field.agentTime.takeOver()

        assertNull(field.viewModel.act(AgentAction.Step(AgentAction.Way.FORWARD)))
        val refused = field.viewModel.act(AgentAction.Turn(AgentAction.Side.RIGHT))
        assertEquals("Not done: the last step is still being taken.", refused)

        delay(1_000)
        assertEquals(refused, field.viewModel.act(AgentAction.Turn(AgentAction.Side.RIGHT)), "the step ended on the wall's time")

        field.agentTime.advance(500)
        assertNull(field.viewModel.act(AgentAction.Turn(AgentAction.Side.RIGHT)))
        field.until("a turn to the west") { field.told.facing == Direction.WEST }
    }

    /** Laurann holds her spellbook in her lower hand; using it opens it, and the way out shuts it. */
    @Test
    fun `using a hand that holds a book opens it`() = runBlocking {
        val field = field("LEVEL12.INF", Location(19, 15), Direction.SOUTH)

        assertNull(field.viewModel.act(AgentAction.UseHand(champion = 3, hand = 1)))
        field.until("the book to open") { field.told.book != null }
        assertEquals(3, field.told.book?.champion)

        assertNull(field.viewModel.act(AgentAction.BookAbort))
        field.until("the book to shut") { field.told.book == null }
    }

    @Test
    fun `a menu line is chosen by its words`(): Unit = runBlocking {
        val field = field("LEVEL12.INF", Location(19, 15), Direction.SOUTH)

        assertNull(field.viewModel.act(AgentAction.Camp))
        field.until("the camp") { field.told.menu?.title == "Camp:" }

        assertNull(field.viewModel.act(AgentAction.Choose("Game Options")))
        field.until("the game options") { field.told.menu?.title == "Game Options:" }

        assertNotNull(field.viewModel.act(AgentAction.Choose("Fly away")), "a line that is not there was chosen")
    }

    @Test
    fun `the frame is the screen as a picture`() = runBlocking {
        val field = field("LEVEL12.INF", Location(19, 15), Direction.SOUTH)
        field.until("a frame") { field.viewModel.frame() != null }

        val picture = ImageIO.read(requireNotNull(field.viewModel.frame()).inputStream())

        assertEquals(320, picture.width)
        assertEquals(200, picture.height)
    }
}
