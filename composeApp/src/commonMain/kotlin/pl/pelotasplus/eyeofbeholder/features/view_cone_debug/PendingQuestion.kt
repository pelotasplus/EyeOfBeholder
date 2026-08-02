package pl.pelotasplus.eyeofbeholder.features.view_cone_debug

import kotlinx.coroutines.CompletableDeferred
import pl.pelotasplus.eyeofbeholder.data.model.DialogAnswer

/**
 * The question a script is waiting on, and the click that answers it.
 *
 * Answering resumes the script where it stands rather than later, so the
 * script can reach its next question before [answer] has returned. The
 * answered question is therefore let go of before it is completed: clearing
 * afterwards would wipe the question that had just arrived, and the script
 * would wait on a click nothing could deliver.
 */
class PendingQuestion {

    private var waiting: CompletableDeferred<DialogAnswer>? = null

    /** Puts a question up with [show] and suspends until it is answered. */
    suspend fun ask(show: suspend () -> Unit): DialogAnswer {
        val question = CompletableDeferred<DialogAnswer>()
        waiting = question
        show()
        return question.await()
    }

    /** Answers whatever is waiting, if anything is. */
    fun answer(answer: DialogAnswer) {
        val question = waiting
        waiting = null
        question?.complete(answer)
    }
}
