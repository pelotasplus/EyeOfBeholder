package pl.pelotasplus.eyeofbeholder.data.model

/**
 * The clocks a floor keeps — see [ScriptTimer] — and how long each has left.
 *
 * They start at their full interval, so that walking onto a floor is not
 * itself a waking, and a floor changed underfoot brings its own.
 *
 * A clock is held rather than dropped while something else owns the screen. A
 * waking runs a script, and a script starting while another is talking takes
 * down whatever the first had up: a floor met with a vision would show it for
 * as long as the clock's interval and no longer. Held, a clock keeps its full
 * interval and comes round after, rather than the moment the screen is free.
 */
class FloorClocks(timers: List<ScriptTimer> = emptyList()) {

    private var keeping = timers
    private var left = timers.startingFull()

    fun nowKeeping(timers: List<ScriptTimer>) {
        if (timers == keeping) return
        keeping = timers
        left = timers.startingFull()
    }

    /** The squares whose clocks came round, having counted them all down. */
    fun stepped(by: Ticks, held: Boolean = false): List<Location> {
        if (held) return emptyList()

        return keeping.indices
            .filter { which ->
                left[which] -= by.value
                (left[which] <= 0).also { if (it) left[which] = keeping[which].ticks.value }
            }
            .map { keeping[it].watches }
    }

    private companion object {
        fun List<ScriptTimer>.startingFull() = map { it.ticks.value }.toIntArray()
    }
}
