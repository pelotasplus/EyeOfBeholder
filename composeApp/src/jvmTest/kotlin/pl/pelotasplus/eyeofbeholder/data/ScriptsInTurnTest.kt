package pl.pelotasplus.eyeofbeholder.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import pl.pelotasplus.eyeofbeholder.data.model.ScriptsInTurn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Two squares answering in the same turn of the clock.
 *
 * The floor raises more than one script at a time as a matter of course: the
 * twelfth's own clock comes round every few turns, and a thing thrown across
 * that floor lands on a plate in one of them sooner or later. Both are
 * accounts of something that has already happened, so both have to be run —
 * the original runs each one where it is raised and returns, and nothing
 * there can cut anything else short.
 *
 * Dropping one is the failure this guards against, and it is invisible while
 * it happens: the thing is drawn lying on the plate, the plate stays up, and
 * the door it should have opened never does.
 */
class ScriptsInTurnTest {

    @Test
    fun `two raised in the same turn both run`() = runBlocking {
        val ran = mutableListOf<String>()
        val scripts = ScriptsInTurn(this)

        scripts.next { ran += "the plate" }
        scripts.next { ran += "the clock" }.join()

        assertEquals(listOf("the plate", "the clock"), ran)
    }

    @Test
    fun `and in the order they were raised`() = runBlocking {
        val ran = mutableListOf<Int>()
        val scripts = ScriptsInTurn(this)

        (1..5).map { n -> scripts.next { ran += n } }.last().join()

        assertEquals(listOf(1, 2, 3, 4, 5), ran)
    }

    /**
     * And one raised while another is still going waits for it rather than
     * cutting in — which is what the order proves: the one held up writes its
     * name first all the same.
     */
    @Test
    fun `one raised while another is running waits for it`() = runBlocking {
        val ran = mutableListOf<String>()
        val letItFinish = CompletableDeferred<Unit>()
        val scripts = ScriptsInTurn(this)

        scripts.next {
            letItFinish.await()
            ran += "the first"
        }
        val last = scripts.next { ran += "the second" }

        letItFinish.complete(Unit)
        last.join()

        assertEquals(listOf("the first", "the second"), ran)
    }

    /** And while one is going, the floor knows a script owns the screen. */
    @Test
    fun `a script owns the screen until it is done`() = runBlocking {
        val letItFinish = CompletableDeferred<Unit>()
        val scripts = ScriptsInTurn(this)

        assertFalse(scripts.running, "nothing has been raised")

        val one = scripts.next { letItFinish.await() }
        assertTrue(scripts.running)

        letItFinish.complete(Unit)
        one.join()
        assertFalse(scripts.running)
    }
}
