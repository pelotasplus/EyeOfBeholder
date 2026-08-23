package pl.pelotasplus.eyeofbeholder.data.model

/**
 * Somebody the party meet, and the words the meeting is made of.
 *
 * A meeting is a set piece: a script hands over, the person says their piece,
 * and the party answer — one of the answers being to let them come along. The
 * speeches are TEXT.DAT's, which is where every other spoken thing comes from;
 * the words on the buttons are read out of the original's own executable and
 * so are English here, the way [DialogueScene.MORE] and [DialogueScene.OK]
 * are.
 *
 * The dungeon's other two set pieces — the portal, and being told the party
 * have died — go through the same opcode and are not these.
 */
data class NpcMeeting(
    val npc: NpcId,

    /** What is heard as they step up. */
    val heardAs: TrackIndex,

    /** Their piece, which ends in asking to come along. */
    val asks: DialogueTextId,

    /** What they say to being let along. */
    val agrees: DialogueTextId,

    /** And to being turned down, where they have anything to say to it. */
    val refused: DialogueTextId?,

    /** Their picture, and where they stand in the view while they speak. */
    val standing: Standing,
) {
    /**
     * Where somebody's picture is cut from the sheet the meetings share, and
     * how big they are. From the original, which keeps the width in bytes and
     * every one of them at the left edge of the sheet.
     *
     * Where they stand falls out of their size: they are centred on the view
     * and stand on the floor of it, so a taller person's head is higher up
     * rather than their feet being lower down.
     */
    data class Standing(val sourceTop: Int, val width: Int, val height: Int) {
        val left: Int get() = MIDDLE - width / 2
        val top: Int get() = FLOOR - height

        /** The same, as the dialogue box's own way of placing a picture. */
        fun inTheView() = DialogueScene.PictureFrame.Standing(
            left = left,
            top = top,
            width = width,
            height = height,
        )

        private companion object {
            const val MIDDLE = 88
            const val FLOOR = 104
        }
    }
    companion object {
        /**
         * The three the dungeon has, by the number a script calls for.
         *
         * Only the first is written out: the other two open with a speech of
         * their own and a choice that is not about joining at all — one of
         * them decides it at random — and neither is a meeting of this shape.
         */
        fun called(npc: NpcId): NpcMeeting? = if (npc.value == 0) IN_THE_CRYPT else null

        /**
         * The one waiting on the first floor, whose whole meeting is the
         * question.
         */
        private val IN_THE_CRYPT = NpcMeeting(
            npc = NpcId(0),
            heardAs = TrackIndex(57),
            asks = DialogueTextId(1),
            agrees = DialogueTextId(3),
            refused = DialogueTextId(2),
            standing = Standing(sourceTop = 0, width = 40, height = 57),
        )

        /** The sheet every one of them is cut from. */
        const val SHEET = "OUTTAKE.CPS"

        /** The two answers, which the original also keeps in its executable. */
        const val YES = "yes"
        const val NO = "no"
    }
}
