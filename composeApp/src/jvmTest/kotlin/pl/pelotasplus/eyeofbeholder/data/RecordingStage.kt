package pl.pelotasplus.eyeofbeholder.data

import pl.pelotasplus.eyeofbeholder.data.model.DialogAnswer
import pl.pelotasplus.eyeofbeholder.data.model.GameState
import pl.pelotasplus.eyeofbeholder.data.model.ScriptQuestion
import pl.pelotasplus.eyeofbeholder.data.model.ScriptSpeech
import pl.pelotasplus.eyeofbeholder.data.model.ScriptStage
import pl.pelotasplus.eyeofbeholder.data.model.Ticks

/**
 * A screen that draws nothing and answers instantly, but remembers everything
 * it was asked to do — which is how a script's timing is tested without any
 * of it taking time.
 *
 * [answers] are given out in order, one per question; once they run out every
 * further question is answered with the first button, which is what clicking
 * "ok" on a speech does.
 */
class RecordingStage(answers: List<Int> = emptyList()) : ScriptStage {

    private val toGive = ArrayDeque(answers.map { DialogAnswer(it) })

    /** What the script did, in order, one entry per call. */
    val beats = mutableListOf<Beat>()

    val questions get() = beats.filterIsInstance<Beat.Asked>().map { it.question }
    val holds get() = beats.filterIsInstance<Beat.Held>().map { it.ticks }
    val shown get() = beats.filterIsInstance<Beat.Shown>().map { it.world }

    sealed interface Beat {
        data class Shown(val world: GameState) : Beat
        data class Said(val speech: ScriptSpeech) : Beat
        data class Held(val ticks: Ticks) : Beat
        data class Asked(val question: ScriptQuestion, val answered: DialogAnswer) : Beat
    }

    override suspend fun show(world: GameState) {
        beats += Beat.Shown(world)
    }

    override suspend fun say(speech: ScriptSpeech) {
        beats += Beat.Said(speech)
    }

    override suspend fun hold(ticks: Ticks) {
        beats += Beat.Held(ticks)
    }

    override suspend fun ask(question: ScriptQuestion): DialogAnswer {
        val answer = toGive.removeFirstOrNull() ?: DialogAnswer(1)
        beats += Beat.Asked(question, answer)
        return answer
    }
}
