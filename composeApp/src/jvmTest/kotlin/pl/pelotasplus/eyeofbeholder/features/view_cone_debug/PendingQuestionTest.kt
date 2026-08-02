package pl.pelotasplus.eyeofbeholder.features.view_cone_debug

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import pl.pelotasplus.eyeofbeholder.data.model.DialogAnswer
import kotlin.test.Test
import kotlin.test.assertEquals

class PendingQuestionTest {

    @Test
    fun `a question asked while the last is being answered is still answerable`() = runBlocking {
        val pending = PendingQuestion()
        val given = mutableListOf<Int>()

        // Unconfined resumes a completed question in place, the way the main
        // dispatcher does — so the second question is asked from inside the
        // call answering the first.
        val script = launch(Dispatchers.Unconfined) {
            given += pending.ask { }.number
            given += pending.ask { }.number
        }

        pending.answer(DialogAnswer(2))
        pending.answer(DialogAnswer(3))

        withTimeout(SHOULD_BE_INSTANT) { script.join() }
        assertEquals(listOf(2, 3), given)
    }

    @Test
    fun `answering when nothing was asked is ignored`() {
        PendingQuestion().answer(DialogAnswer(1))
    }

    private companion object {
        /** Long enough that only a question nobody can answer runs it out. */
        const val SHOULD_BE_INSTANT = 2_000L
    }
}
