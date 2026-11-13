package pl.pelotasplus.eyeofbeholder.agent

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.TimeSource

/**
 * The game's clock while an agent is playing.
 *
 * A person plays in real time; an agent takes seconds to decide, and the
 * monsters would have the party dead while it thought. Taken over, the game
 * stands still between the agent's acts and runs only while it says to wait,
 * at its own speed — the same as a hidden tab stands still, and with the
 * same cost: a beat already on its way when the game stops is let finish.
 *
 * Time is not jumped ahead. The game's clocks are many, are started by what
 * the party do, and do real work on every beat, so there is no telling from
 * outside when all of them have had theirs; running the game as it runs for
 * a person is the one way every beat comes in its order.
 */
class AgentTime {

    private val _driving = MutableStateFlow(false)
    val driving: StateFlow<Boolean> = _driving.asStateFlow()

    /** Whether the agent has said to wait, which is when its game runs. */
    private val running = MutableStateFlow(false)

    fun takeOver() {
        _driving.value = true
    }

    fun handBack() {
        _driving.value = false
    }

    /** Returns once the game's clocks may run: at once for a person, during a wait for an agent. */
    suspend fun mayRun() {
        combine(_driving, running) { driving, waiting -> !driving || waiting }.first { it }
    }

    /**
     * A clock's wait of [millis], counted only while the game runs: a wait
     * the agent stops half way through carries on from there at its next one,
     * so no beat fires early and a wait of the agent's runs no longer than it
     * said.
     */
    suspend fun runFor(millis: Long) {
        var left = millis
        while (left > 0) {
            mayRun()
            val started = TimeSource.Monotonic.markNow()
            withTimeoutOrNull(left) { stoodStill.first { it } }
            left -= started.elapsedNow().inWholeMilliseconds
        }
    }

    private val stoodStill = combine(_driving, running) { driving, waiting -> driving && !waiting }

    /** The game run for [millis], then stood still again. */
    suspend fun advance(millis: Long) {
        running.value = true
        try {
            delay(millis)
        } finally {
            running.value = false
        }
    }
}
