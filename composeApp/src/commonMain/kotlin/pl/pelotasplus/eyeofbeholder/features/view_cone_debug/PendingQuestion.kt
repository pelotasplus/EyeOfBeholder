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

    /**
     * Puts a question up with [show] and suspends until it is answered.
     *
     * A [show] that says there is nothing to click comes straight back
     * instead: not everything a script puts on screen is waited for, and one
     * that is not must not leave the script hanging on a click that can never
     * come.
     */
    suspend fun ask(show: suspend () -> Boolean): DialogAnswer {
        val question = CompletableDeferred<DialogAnswer>()
        waiting = question

        if (!show()) {
            waiting = null
            return DialogAnswer.UNASKED
        }

        return question.await()
    }

    /**
     * Answers whatever is waiting, if anything is, and says whether anything
     * was — which is how the caller knows whether something else is about to
     * carry on, or whether answering was the end of it.
     */
    fun answer(answer: DialogAnswer): Boolean {
        val question = waiting
        waiting = null
        return question?.complete(answer) == true
    }
}
