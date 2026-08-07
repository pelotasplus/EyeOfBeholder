package pl.pelotasplus.eyeofbeholder.features.view_cone_debug

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import pl.pelotasplus.eyeofbeholder.data.model.DialogAnswer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PendingQuestionTest {

    @Test
    fun `a question asked while the last is being answered is still answerable`() = runBlocking {
        val pending = PendingQuestion()
        val given = mutableListOf<Int>()

        // Unconfined resumes a completed question in place, the way the main
        // dispatcher does — so the second question is asked from inside the
        // call answering the first.
        val script = launch(Dispatchers.Unconfined) {
            given += pending.ask { true }.number
            given += pending.ask { true }.number
        }

        pending.answer(DialogAnswer(2))
        pending.answer(DialogAnswer(3))

        withTimeout(SHOULD_BE_INSTANT) { script.join() }
        assertEquals(listOf(2, 3), given)
    }

    /**
     * A speech with no button to click is not waited for, and the script goes
     * on without anything having been answered.
     */
    @Test
    fun `nothing to click is not waited for`() = runBlocking {
        val pending = PendingQuestion()

        val answer = withTimeout(SHOULD_BE_INSTANT) { pending.ask { false } }

        assertEquals(DialogAnswer.UNASKED, answer)
    }

    /** And nothing is left waiting behind it for the next click to answer. */
    @Test
    fun `a click after it answers whatever is asked next`() = runBlocking {
        val pending = PendingQuestion()
        pending.ask { false }

        val given = mutableListOf<Int>()
        val script = launch(Dispatchers.Unconfined) { given += pending.ask { true }.number }
        pending.answer(DialogAnswer(1))

        withTimeout(SHOULD_BE_INSTANT) { script.join() }
        assertEquals(listOf(1), given)
    }

    @Test
    fun `answering when nothing was asked is ignored`() {
        PendingQuestion().answer(DialogAnswer(1))
    }

    /**
     * Whether anything was waiting is the answer to a question of its own: a
     * box nobody is waiting on has to be taken down by whoever answered it,
     * because nothing else is going to draw.
     */
    @Test
    fun `answering says whether anything was waiting`() = runBlocking {
        val pending = PendingQuestion()

        assertFalse(pending.answer(DialogAnswer(1)), "nothing was asked")

        launch(Dispatchers.Unconfined) { pending.ask { true } }
        assertTrue(pending.answer(DialogAnswer(1)), "a question was waiting")

        assertFalse(pending.answer(DialogAnswer(1)), "and is not waiting twice")
    }

    private companion object {
        /** Long enough that only a question nobody can answer runs it out. */
        const val SHOULD_BE_INSTANT = 2_000L
    }
}
