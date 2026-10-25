package pl.pelotasplus.eyeofbeholder.agent

import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.experimental.categories.Category
import pl.pelotasplus.eyeofbeholder.NeedsGameData
import pl.pelotasplus.eyeofbeholder.data.ThePlayField
import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.GameState
import pl.pelotasplus.eyeofbeholder.data.model.Location
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The clock an agent drives: nothing passes in real time, and what it lets
 * pass passes in order and at once.
 */
class AgentTimeTest {

    @Test
    fun `a wait does not end in real time while the agent drives`() = runBlocking {
        val time = AgentTime().apply { takeOver() }
        val waiting = async { time.passes(100) }

        delay(300)

        assertFalse(waiting.isCompleted, "the wall clock ended a wait the agent never let pass")
        time.advance(100)
        assertTrue(waiting.isCompleted)
    }

    @Test
    fun `clocks wake in the order they are due`() = runBlocking {
        val time = AgentTime().apply { takeOver() }
        val woke = mutableListOf<String>()
        val slow = async { time.passes(300); woke += "slow" }
        val quick = async { time.passes(100); woke += "quick" }
        delay(20)

        time.advance(1_000)

        slow.await()
        quick.await()
        assertEquals(listOf("quick", "slow"), woke)
    }

    @Test
    fun `handed back, a wait finishes in real time`() = runBlocking {
        val time = AgentTime().apply { takeOver() }
        val waiting = async { time.passes(50) }
        delay(20)

        time.handBack()
        delay(200)

        assertTrue(waiting.isCompleted)
    }

    /**
     * On the real field: a party grow hungry a mouthful a minute. Driven by the
     * agent, a second of real time costs them nothing and a minute of its time
     * costs them the mouthful.
     */
    @Test
    @Category(NeedsGameData::class)
    fun `the party grow hungry on the agent's time and not the wall's`() = runBlocking {
        val field = ThePlayField()
        field.agentTime.takeOver()
        field.opened("LEVEL12.INF", Location(19, 15), Direction.NORTH)
        field.until("the party to arrive") { field.viewModel.state.value.game.champions.isNotEmpty() }
        val before = field.foodOfAll()

        delay(1_000)
        assertEquals(before, field.foodOfAll(), "the wall clock fed on them")

        field.agentTime.advance(GameState.A_MOUTHFUL_LASTS.inMilliseconds)
        field.until("a mouthful to go") { field.foodOfAll() < before }
    }

    private fun ThePlayField.foodOfAll() = viewModel.state.value.game.champions.sumOf { it.food.value }
}
