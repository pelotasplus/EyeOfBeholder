package pl.pelotasplus.eyeofbeholder.data.model

/**
 * The sparks a spell throws about the room as it is cast.
 *
 * Sixteen of them, each at a fixed place in the view, lighting and fading over
 * forty-four frames. Nothing about it follows the corridor: the places are the
 * same wherever the party stand and whatever they are looking at, so this is
 * drawn over the view rather than into it.
 *
 * Each spark shows one of three pictures or none at all, and which is decided
 * two bits at a time out of one word per four frames — so the whole animation
 * is eleven words, and every spark's whole life is in the same word as every
 * other's. That packing is why they light in a spreading wave rather than
 * together: the words were written to make the pattern, and there is nothing
 * else in here that would.
 */
data class SparksInTheRoom(val frame: Int = 0) {

    /** The same again a frame later, or null once the last has gone out. */
    fun next(): SparksInTheRoom? = (frame + 1).takeIf { it < FRAMES }?.let { SparksInTheRoom(it) }

    /**
     * Which picture spark [which] shows this frame, counting from one, or zero
     * for a spark that is not lit at all.
     */
    fun showing(which: Int): Int {
        val word = lit.getOrNull(frame / FRAMES_A_WORD) ?: return NONE
        return ((word shr shifts[which]) and A_SPARK).toInt()
    }

    /** Where spark [which] sits in the view. */
    fun x(which: Int) = across[which]

    fun y(which: Int) = down[which]

    companion object {
        /** How many sparks there are, and how long they take. */
        const val HOW_MANY = 16
        const val FRAMES = 44

        /**
         * How long one frame is held: half a tick, which puts the whole
         * forty-four at a shade over a second.
         *
         * That is the length of the sound a casting makes, and the two are
         * meant to be read as one thing. Held a whole tick each the sparks
         * run twice as long and the sound stops halfway through them, which
         * looks like the sound has been cut off rather than the stars gone
         * slow.
         */
        const val A_FRAME = 27L

        /** How many pictures a spark has, past which nothing is drawn. */
        const val PICTURES = 3

        private const val NONE = 0

        /** Each word carries four frames of the animation. */
        private const val FRAMES_A_WORD = 4

        /** Two bits a spark, which is what makes eleven words enough. */
        private const val A_SPARK = 3L

        private val lit = listOf(
            0x40000000L, 0x95000000L, 0xEA550000L, 0xBFAA5400L,
            0x6AFFA954L, 0x15AAFEA9L, 0x0055ABFEL, 0x000056ABL,
            0x00000156L, 0x00000001L, 0x00000000L,
        )

        /** Where each spark's two bits sit in the word, highest first. */
        private val shifts = List(HOW_MANY) { 30 - it * 2 }

        private val across = listOf(
            0x50, 0x70, 0x30, 0x68, 0x20, 0x60, 0x38, 0x78,
            0x80, 0x48, 0x58, 0x28, 0x60, 0x40, 0x70, 0x48,
        )

        private val down = listOf(
            0x31, 0x2B, 0x48, 0x17, 0x16, 0x48, 0x35, 0x1B,
            0x43, 0x2E, 0x24, 0x28, 0x38, 0x1C, 0x16, 0x44,
        )
    }
}
