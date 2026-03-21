package pl.pelotasplus.eyeofbeholder.data.model

/**
 * A timed script trigger — calls a script function at regular intervals.
 *
 * Used for periodic events like wandering monsters, trap resets, or
 * environmental effects. Timers are defined per sublevel.
 *
 * The raw tick value from the INF file is multiplied by 18 during parsing
 * (converting from game timer ticks to a time-based value).
 *
 * @property func Script function offset to call when the timer fires
 * @property ticks Interval between calls (raw value × 18 from INF)
 */
data class ScriptTimer(
    val func: Int,
    val ticks: Int,
)
