package pl.pelotasplus.eyeofbeholder.agent

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable

/**
 * What the code noticed about itself while being played, for an agent in QA
 * mode: every warning and error the game logs, and every place it reaches
 * something nothing has been written for yet — a spell with no effect, a
 * thing used from a hand that does nothing.
 *
 * Nothing here is a player's to know, which is why only QA mode hands it
 * out. Kept as a short run of the latest, numbered, so an agent asks for
 * what came since the last it saw.
 */
object Diagnostics {

    @Serializable
    data class Note(val number: Long, val kind: String, val said: String)

    private val notes = MutableStateFlow(emptyList<Note>())
    private var listening = false

    /** Something not written yet was reached: [what] says what, in words an agent can act on. */
    fun notWrittenYet(what: String) = add("not written yet", what)

    /** The notes after [after], oldest first. */
    fun since(after: Long): List<Note> = notes.value.filter { it.number > after }

    /** Starts catching the game's own warnings and errors, once however often asked. */
    fun listen() {
        if (listening) return
        listening = true
        Logger.addLogWriter(
            object : LogWriter() {
                override fun isLoggable(tag: String, severity: Severity) = severity >= Severity.Warn

                override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
                    add(severity.name.lowercase(), "$tag: $message${throwable?.let { " — $it" }.orEmpty()}")
                }
            },
        )
    }

    private fun add(kind: String, said: String) {
        notes.update { kept -> (kept + Note((kept.lastOrNull()?.number ?: 0) + 1, kind, said)).takeLast(KEPT) }
    }

    private const val KEPT = 200
}
