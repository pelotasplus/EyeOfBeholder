package pl.pelotasplus.eyeofbeholder.data

import pl.pelotasplus.eyeofbeholder.data.model.DialogAnswer
import pl.pelotasplus.eyeofbeholder.data.model.GameState
import pl.pelotasplus.eyeofbeholder.data.model.MessageId
import pl.pelotasplus.eyeofbeholder.data.model.ScriptQuestion
import pl.pelotasplus.eyeofbeholder.data.model.ScriptSpeech
import pl.pelotasplus.eyeofbeholder.data.model.ScriptStage
import pl.pelotasplus.eyeofbeholder.data.model.Ticks
import pl.pelotasplus.eyeofbeholder.data.model.TrackIndex
import pl.pelotasplus.eyeofbeholder.data.model.Volume

/** Answers questions with [buttons] in turn, the last for as many more as are asked. */
class Picks(vararg buttons: Int) : ScriptStage {
    private val buttons = buttons.toList()

    val asked = mutableListOf<ScriptQuestion>()
    val played = mutableListOf<TrackIndex>()
    val shown = mutableListOf<GameState>()
    val said = mutableListOf<MessageId>()

    override fun notImplemented(what: String) = Unit
    override suspend fun show(world: GameState) {
        shown += world
    }
    override suspend fun say(speech: ScriptSpeech) {
        said += speech.said
    }
    override suspend fun hold(ticks: Ticks) = Unit
    override suspend fun play(track: TrackIndex, volume: Volume) {
        played += track
    }
    override suspend fun opensThePortal() = Unit

    override suspend fun ask(question: ScriptQuestion): DialogAnswer {
        val button = buttons[asked.size.coerceAtMost(buttons.lastIndex)]
        asked += question
        return DialogAnswer(button)
    }
}
