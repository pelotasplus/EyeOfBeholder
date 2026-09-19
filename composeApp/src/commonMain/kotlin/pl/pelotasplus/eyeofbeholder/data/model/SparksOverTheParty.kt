package pl.pelotasplus.eyeofbeholder.data.model

/**
 * The sparks a spell cast on the whole party throws over their portraits.
 *
 * Four to a box, each lighting and fading over thirty-two frames. Which of the
 * three pictures a spark shows is two bits of one word per four frames, the
 * same packing [SparksInTheRoom] uses; the tables are the game's own.
 */
data class SparksOverTheParty(
    val frame: Int = 0,

    /**
     * Whose box they are over, or nobody named for all six of them.
     *
     * The same thirty-two frames serve a spell laid on the whole party and one
     * laid on a single champion — a mending is the party's spell shown over
     * one portrait, not an animation of its own.
     */
    val over: PartySlot? = null,
) {

    /** Whether they are lighting the box of [slot] at all. */
    fun lighting(slot: PartySlot) = over == null || over == slot

    /** The same again a frame later, or null once the last has gone out. */
    fun next(): SparksOverTheParty? =
        (frame + 1).takeIf { it < FRAMES }?.let { copy(frame = it) }

    /** Which picture spark [which] of a box shows this frame, from one, or zero for none. */
    fun showing(which: Int): Int {
        val word = LIT.getOrNull(frame / FRAMES_A_WORD) ?: return 0
        return (word and MASKS[which]) shr SHIFTS[which]
    }

    companion object {
        const val FRAMES = 32
        const val EACH_BOX = 4

        /** Half a tick, as the sparks in the room are held. */
        const val A_FRAME = 27L

        /**
         * How many frames each word of [LIT] covers, and so how often the
         * picture actually changes: thirty-two frames are eight pictures.
         */
        const val FRAMES_A_WORD = 4

        private val LIT = listOf(0x40, 0x90, 0xE4, 0xB9, 0x6E, 0x1B, 0x06, 0x01)
        private val MASKS = listOf(0xC0, 0x30, 0x0C, 0x03)
        private val SHIFTS = listOf(6, 4, 2, 0)

        /** Where each of the four sits, from the left and top of the screen's box column. */
        private val ACROSS = listOf(8, 28, 13, 13)
        private val DOWN = listOf(6, 20, 24, 15)

        private val BOX_LEFTS = listOf(184, 256)
        private val BOX_TOPS = listOf(2, 52, 102)

        /** Where spark [which] is drawn over the box of [slot], on the screen. */
        fun x(slot: PartySlot, which: Int) = ACROSS[which] - 8 + BOX_LEFTS[slot.index % 2]

        fun y(slot: PartySlot, which: Int) = DOWN[which] + BOX_TOPS[slot.index / 2]
    }
}
