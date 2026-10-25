package pl.pelotasplus.eyeofbeholder.agent

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update

/**
 * The game's clock while an agent is playing.
 *
 * A person plays in real time; an agent takes seconds to decide, and the
 * monsters would have the party dead while it thought. Taken over, every
 * clock in the game waits on this instead of on the wall: time passes only
 * when the agent says so, and then as fast as the game can run it. Handed
 * back, whatever was part-way through a wait finishes it in real time.
 */
class AgentTime {

    /** Milliseconds of game time the agent has let pass since the game began. */
    private val now = MutableStateFlow(0L)

    /** When each clock waiting on this is due, one entry per clock. */
    private val due = MutableStateFlow(emptyList<Long>())

    private val _driving = MutableStateFlow(false)
    val driving: StateFlow<Boolean> = _driving.asStateFlow()

    fun takeOver() {
        _driving.value = true
    }

    fun handBack() {
        _driving.value = false
    }

    /** A clock's wait of [millis], in game time. */
    suspend fun passes(millis: Long) {
        val until = now.value + millis
        due.update { it + until }
        try {
            combine(now, _driving) { at, driving -> at >= until || !driving }.first { it }
        } finally {
            due.update { it - until }
        }

        val left = until - now.value
        if (!_driving.value && left > 0) delay(left)
    }

    /**
     * The game run on by [millis]: from one clock's beat to the next due, in
     * order, each given a real moment to run — and to set its next wait —
     * before time moves on. A stretch with nothing due in it passes at once.
     */
    suspend fun advance(millis: Long) {
        val until = now.value + millis
        while (true) {
            val next = due.value.filter { it > now.value }.minOrNull()
            if (next == null || next > until) break
            now.value = next
            delay(SETTLE)
        }
        now.value = until
        delay(SETTLE)
    }

    private companion object {
        /** Real milliseconds a beat is given to run, and to ask for its next. */
        const val SETTLE = 5L
    }
}
