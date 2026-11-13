package pl.pelotasplus.eyeofbeholder.agent

import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The clock an agent drives: the game stands still between its acts, and
 * runs only while it says to wait.
 */
class AgentTimeTest {

    @Test
    fun `nobody driving, the game runs`() = runBlocking {
        val time = AgentTime()
        val beat = async { time.mayRun() }

        delay(50)

        assertTrue(beat.isCompleted)
    }

    @Test
    fun `driven, the game stands still until the agent waits`() = runBlocking {
        val time = AgentTime().apply { takeOver() }
        val beat = async { time.mayRun() }

        delay(300)
        assertFalse(beat.isCompleted, "the game ran without the agent waiting")

        time.advance(50)
        assertTrue(beat.isCompleted)
    }

    @Test
    fun `and stands still again once the wait is over`() = runBlocking {
        val time = AgentTime().apply { takeOver() }
        time.advance(20)

        val beat = async { time.mayRun() }
        delay(200)

        assertFalse(beat.isCompleted, "the game ran on after the wait")
        beat.cancel()
    }

    @Test
    fun `a clock's wait does not end at the start of the agent's`() = runBlocking {
        val time = AgentTime().apply { takeOver() }
        val beat = async { time.runFor(200) }
        delay(50)

        time.advance(50)

        assertFalse(beat.isCompleted, "a 200 ms wait ended after 50 ms of the game running")
        beat.cancel()
    }

    @Test
    fun `a clock's wait carries on across the agent's waits`() = runBlocking {
        val time = AgentTime().apply { takeOver() }
        val beat = async { time.runFor(150) }
        delay(20)

        time.advance(100)
        delay(200)
        assertFalse(beat.isCompleted, "the wait ran on while the game stood still")

        time.advance(100)
        assertTrue(beat.isCompleted)
    }

    @Test
    fun `handed back, the game runs again`() = runBlocking {
        val time = AgentTime().apply { takeOver() }
        val beat = async { time.mayRun() }
        delay(20)

        time.handBack()
        delay(50)

        assertTrue(beat.isCompleted)
    }
}
