package pl.pelotasplus.eyeofbeholder.data.model

import pl.pelotasplus.eyeofbeholder.data.model.script.Dialog

/**
 * A scene played over the view, a picture at a time.
 *
 * Made of the same two things a level's own conversations are — a picture put
 * up in the dialogue frame and words written under it — but no level contains
 * one. A scene belongs to the game rather than to a floor, and is played when
 * the game decides it should be; a floor's part is at most to set the flag
 * that says it may.
 */
data class CutScene(val beats: List<Beat>) {

    /**
     * One picture and how long it stays up.
     *
     * A beat with a [readOn] waits for that button to be pressed, however long
     * that takes. One without holds for [holdsFor] and goes on by itself, which
     * is what makes a run of them an animation rather than a slideshow.
     */
    data class Beat(
        val shows: Dialog.DisplayPicture,
        /** A line of the game's own text, by the number it is filed under. */
        val says: DialogueTextId? = null,
        /** Or a line that is nobody's to look up, written out here. */
        val spoken: String? = null,
        val readOn: String? = null,
        val holdsFor: Ticks = Ticks(0),
        val heardAs: TrackIndex? = null,
    )

    companion object {

        /**
         * What is waiting for a party who take the twelfth floor's advice.
         *
         * The floor offers a way to break a spell being readied against the
         * party: put on a certain trinket and let the guardians kill you, and
         * the trinket will bring you back. A party who do it are answered by
         * the one who laid the plan — he was never the friend whose shape he
         * wore — and the game is over. Leave the trinket where it lies and the
         * guardians are only guardians.
         *
         * Four pictures cut from one sheet in a two-by-two grid, 160 wide and
         * 96 deep each, drawn into the frame a speaker stands in. All of it is
         * transcribed: the cuts, the two waits of ten and the last of seven,
         * and which of the game's lines is written under which picture.
         */
        val THE_TRICK_ON_THE_TWELFTH = CutScene(
            listOf(
                Beat(
                    shows = frameOf(TRICKED, FIRST, TOP),
                    spoken = "    Such trusting whelps!",
                    readOn = MORE,
                ),
                Beat(
                    shows = frameOf(TRICKED, SECOND, TOP),
                    holdsFor = Ticks(10),
                    heardAs = TrackIndex(56),
                ),
                Beat(shows = frameOf(TRICKED, FIRST, BOTTOM), holdsFor = Ticks(10)),
                Beat(
                    shows = frameOf(TRICKED, SECOND, BOTTOM),
                    holdsFor = Ticks(7),
                    says = DialogueTextId(76),
                    readOn = ALL_RIGHT,
                ),
            ),
        )

        /**
         * What the party see when the thing at the end of the dungeon is
         * killed and does not die.
         *
         * Laid out exactly as the twelfth floor's scene is — four cuts of one
         * sheet in a two-by-two grid, the same laugh on the second — and the
         * only difference is that nothing waits at the end of it: the last
         * picture is held and then the fight is simply on again, against
         * something else. All of it is transcribed.
         */
        val WHAT_THE_LAST_ONE_TURNS_INTO = CutScene(
            listOf(
                Beat(
                    shows = frameOf(CHANGING, FIRST, TOP),
                    says = DialogueTextId(120),
                    readOn = MORE,
                ),
                Beat(
                    shows = frameOf(CHANGING, SECOND, TOP),
                    holdsFor = Ticks(7),
                    heardAs = TrackIndex(56),
                ),
                Beat(shows = frameOf(CHANGING, FIRST, BOTTOM), holdsFor = Ticks(7)),
                Beat(shows = frameOf(CHANGING, SECOND, BOTTOM), holdsFor = Ticks(18)),
            ),
        )

        /**
         * One of the four cuts of [sheet]. [column] is in eighths of the width
         * the way a picture instruction gives it, [row] in whole pixels.
         */
        private fun frameOf(sheet: String, column: Int, row: Int) = Dialog.DisplayPicture(
            pictureName = sheet,
            rect = IN_THE_SPEAKERS_FRAME,
            x = column,
            y = row,
            flags = 0,
        )

        private const val TRICKED = "KHELDRAN"
        private const val CHANGING = "DRANX"

        private const val IN_THE_SPEAKERS_FRAME = 0

        private const val FIRST = 0
        private const val SECOND = 20
        private const val TOP = 0
        private const val BOTTOM = 96

        private const val MORE = "MORE"
        private const val ALL_RIGHT = "OK"
    }
}
