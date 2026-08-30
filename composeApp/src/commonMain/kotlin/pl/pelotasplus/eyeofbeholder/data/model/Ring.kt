package pl.pelotasplus.eyeofbeholder.data.model

/**
 * One of the four rings, which do their work by being worn.
 *
 * Which of them a ring is is its value, counting from zero. The names are
 * transcribed; only [FEATHER_FALL] is asked about anywhere, and it is asked
 * about by every pit in the game.
 */
enum class Ring {
    ADORNMENT,
    WIZARDRY,
    SUSTENANCE,
    FEATHER_FALL;

    companion object {
        /** Which ring a value names, or none for no ring. */
        fun of(value: Int): Ring? = entries.getOrNull(value)
    }
}
