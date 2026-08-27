package pl.pelotasplus.eyeofbeholder.data.model

/**
 * One of the four horns, which are blown rather than swung.
 *
 * Which of them a horn is is its value, counting from one, and that says both
 * what it sounds like and what the party are told they heard. The four winds
 * are these, and a puzzle that wants one blown wants the right one — so what
 * is heard is the whole of what a horn does on its own; the walls make of it
 * what they will.
 *
 * The sounds and the lines are transcribed.
 */
enum class Horn(val heardAs: TrackIndex, val sounds: String) {
    BELLOWING(TrackIndex(0x40), "A bellowing sound comes from the horn."),
    HOLLOW(TrackIndex(0x41), "A hollow sound comes from the horn."),
    MELODIOUS(TrackIndex(0x42), "A melodious sound comes from the horn."),
    EERIE(TrackIndex(0x43), "An eerie sound comes from the horn.");

    companion object {
        /** Which horn a value names, counting from one, or none for no horn. */
        fun of(value: Int): Horn? = entries.getOrNull(value - 1)
    }
}
