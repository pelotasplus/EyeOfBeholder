package pl.pelotasplus.eyeofbeholder.data.model

/**
 * A square a floor keeps coming back to, whether or not anybody is near it.
 *
 * This is the only clock a level has of its own. The lightning pads on the
 * seventh floor are one square woken every eighteen ticks, and what it does
 * each time is rearrange the pads and look to see whether the party are
 * standing on a lit one — so a floor without this runs, but stands still.
 *
 * @property watches which square is woken. The file names it as one number
 *   across the whole 32×32 maze rather than as a pair, so it is unpacked here
 *   and nothing downstream has to know that.
 * @property ticks how long between wakings.
 */
data class ScriptTimer(
    val watches: Location,
    val ticks: Ticks,
) {
    companion object {
        /** The width of every maze, which is what a square number is packed by. */
        private const val ACROSS = 32

        fun of(block: Int, ticks: Int) = ScriptTimer(
            watches = Location(x = block % ACROSS, y = block / ACROSS),
            ticks = Ticks(ticks),
        )
    }
}
