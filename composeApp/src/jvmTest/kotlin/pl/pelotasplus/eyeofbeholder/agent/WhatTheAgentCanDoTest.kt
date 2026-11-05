package pl.pelotasplus.eyeofbeholder.agent

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.experimental.categories.Category
import pl.pelotasplus.eyeofbeholder.NeedsGameData
import pl.pelotasplus.eyeofbeholder.data.ThePlayField
import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.Naming
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

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

    /**
     * A save asks for a name and waits for return, so an agent that cannot
     * type cannot save at all — and a run that cannot save cannot be picked
     * up from where it went wrong.
     */
    @Test
    fun `a save is named by typing and accepted by return`(): Unit = runBlocking {
        val field = field("LEVEL12.INF", Location(19, 15), Direction.SOUTH)

        assertNotNull(field.viewModel.act(AgentAction.Type("start")), "typed where nothing was being named")

        assertNull(field.viewModel.act(AgentAction.Camp))
        field.until("the camp") { field.told.menu?.title == "Camp:" }
        assertNull(field.viewModel.act(AgentAction.Choose("Game Options")))
        field.until("the game options") { field.told.menu?.title == "Game Options:" }
        assertNull(field.viewModel.act(AgentAction.Choose("Save Game")))
        field.until("the slots") { field.told.menu?.choices?.any { it.contains("Slot") } == true }

        assertNull(field.viewModel.act(AgentAction.Choose("Empty Slot")))
        field.until("a name to type into") { field.viewModel.state.value.menu?.naming != null }

        assertNull(field.viewModel.act(AgentAction.Press(AgentAction.Stroke.RUBOUT, times = Naming.LONGEST)))
        field.until("the suggested name gone") { field.viewModel.state.value.menu?.naming?.typed == "" }

        assertNull(field.viewModel.act(AgentAction.Type("start")))
        field.until("the name typed") { field.viewModel.state.value.menu?.naming?.typed == "start" }

        assertNull(field.viewModel.act(AgentAction.Press(AgentAction.Stroke.ACCEPT)))
        field.until("the save to be written") { field.viewModel.state.value.menu?.naming == null }
    }

    /**
     * Picking something up fills the pointer, and nothing else can be picked
     * up until it is empty again. Without somewhere to put things an agent
     * carries one item at a time, which is no way to clear a floor.
     */
    @Test
    fun `what is on the pointer goes into the pack`(): Unit = runBlocking {
        val field = field("LEVEL12.INF", Location(19, 15), Direction.SOUTH)

        assertEquals(
            "Nothing is on the pointer.",
            field.viewModel.act(AgentAction.PutInPack(champion = 0)),
            "an empty pointer was packed",
        )

        assertNull(field.viewModel.act(AgentAction.TakeHand(champion = 3, hand = 0)))
        field.until("the dagger on the pointer") { field.told.holding != null }
        val taken = requireNotNull(field.told.holding)

        assertNull(field.viewModel.act(AgentAction.PutInPack(champion = 0)))
        field.until("the pointer empty") { field.told.holding == null }

        assertTrue(
            taken in field.told.party.single { it.slot == 0 }.pack,
            "what was packed is not in the pack it went into",
        )
    }

    /**
     * The forest's first rock, which `observe` tells as lying on the party's
     * own square at the far right corner. Reaching there with an empty hand
     * takes it, and reaching again with it held puts it back.
     */
    @Test
    fun `a thing on the floor is picked up and put back by naming the corner`(): Unit = runBlocking {
        val field = field("LEVEL4.INF", Location(13, 6), Direction.SOUTH)
        val rockHere = { field.told.inSight.single { it.ahead == 0 && it.across == 0 }.items.any { it.name == "Rock" } }
        assertTrue(rockHere(), "the rock is not where the test expects it")

        assertNull(field.viewModel.act(AgentAction.Reach(ahead = 0, side = AgentAction.Side.RIGHT)))
        assertEquals("Rock", field.told.holding)
        assertTrue(!rockHere(), "the rock was taken and is still on the floor")

        assertNull(field.viewModel.act(AgentAction.Reach(ahead = 0, side = AgentAction.Side.RIGHT)))
        assertNull(field.told.holding)
        assertTrue(rockHere(), "the rock was put down and is not on the floor")

        assertNotNull(
            field.viewModel.act(AgentAction.Reach(ahead = 0, side = AgentAction.Side.LEFT)),
            "reaching into a bare corner with an empty hand came to something",
        )
    }

    /** Memorize Spells as the agent is told it: the spells by name, chosen by name, and the places left. */
    @Test
    fun `spells are memorized by name`(): Unit = runBlocking {
        val field = field("LEVEL12.INF", Location(19, 15), Direction.SOUTH)

        assertNull(field.viewModel.act(AgentAction.Camp))
        field.until("the camp") { field.told.menu?.title == "Camp:" }
        assertNull(field.viewModel.act(AgentAction.Choose("Memorize Spells")))
        field.until("the spells") { field.told.menu?.choices?.contains("Magic Missile") == true }

        val before = field.told.menu!!.says
        assertTrue(before.any { it.startsWith("4 of 4 Remaining") }, "the places left were not told: $before")

        assertNull(field.viewModel.act(AgentAction.Choose("Magic Missile")))
        field.until("one place taken") { field.told.menu!!.says.any { it.startsWith("3 of 4 Remaining") } }
        assertTrue(
            field.told.menu!!.says.any { it.startsWith("Magic Missile") && it.endsWith("1") },
            "the spell chosen does not show as chosen once: ${field.told.menu!!.says}",
        )
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
