package pl.pelotasplus.eyeofbeholder.data.model.sequence

/**
 * The ending, beat by beat, in the order it is played.
 *
 * Which sheet is open when, which list of moves is run how many times, where
 * each line is spoken and where it is taken off again. None of it is logic and
 * none of it is in the level data: six of one move and then two of another is
 * choreography, not a rule, and there is nothing to derive it from. So it
 * reads as a list, and it is meant to be edited as one.
 *
 * The waits are here rather than dropped, since without them the whole thing
 * is over in a second and reads as a flicker.
 *
 * ## Where this stops
 *
 * On the picture of the temple coming down, which is where [TheCredits] takes
 * over. One thing that belongs between the two is missing: a screen of the
 * party's own faces set into six frames, which wants their portraits composed
 * into a sheet first. Going straight to the names rather than showing six
 * empty frames is a choice, not an oversight — see [TheFinale.THE_HEROES] for
 * the sheet that is waiting.
 */
object TheFinaleScript {

    sealed interface Beat {
        /** Opens a sheet. Nothing of it reaches the screen by itself. */
        data class Opens(val scene: Int) : Beat

        /** And a second one beside it, for the scenes that draw from both. */
        data class OpensAlongside(val scene: Int) : Beat

        /** Puts the open sheet's picture into the window. */
        data object Shows : Beat

        /** Keeps what is on screen, so the next sheet does not lose it. */
        data object KeepsTheView : Beat

        /** Runs one of the lists of moves. */
        data class Moves(val list: Int) : Beat

        /** Writes a line across the strip. */
        data class Says(val line: Int, val colour: Int) : Beat

        /** And takes it off again. */
        data object Hushes : Beat

        /** Time passing with nothing drawn. */
        data class Waits(val ticks: Int) : Beat

        /** Starts the tune the whole thing is played over. */
        data object Strikes : Beat

        /**
         * One of the noises the sequence makes on its own account, as against
         * the ones its lists of moves make as they go.
         */
        data class Sounds(val track: Int) : Beat
    }

    /** The bank all of it is heard out of, and the tune's track in it. */
    const val BANK = "FINALE1"
    const val THE_TUNE = 1

    /** The narrator's colour, and the colour the one who arrives is quoted in. */
    const val TOLD = 10
    const val SPOKEN = 15

    val BEATS: List<Beat> = buildList {
        add(Beat.Opens(TheFinale.DRAGON_ROOM))
        add(Beat.Waits(18))
        add(Beat.Strikes)
        add(Beat.Shows)

        add(Beat.Opens(TheFinale.KHELBEN_APPEARS))
        add(Beat.Moves(0))
        add(Beat.Moves(0))
        repeat(3) { add(Beat.Moves(2)) }
        add(Beat.Moves(1))
        add(Beat.Moves(2))
        add(Beat.Moves(2))

        add(Beat.Says(0, TOLD))
        repeat(7) { add(Beat.Moves(2)) }
        add(Beat.Hushes)
        add(Beat.Moves(2))

        add(Beat.Says(1, TOLD))
        add(Beat.Moves(4))
        repeat(3) { add(Beat.Moves(2)) }
        add(Beat.Hushes)

        add(Beat.Says(2, SPOKEN))
        repeat(4) { add(Beat.Moves(5)) }
        add(Beat.Moves(2))
        add(Beat.Moves(2))
        add(Beat.Hushes)
        add(Beat.Moves(6))

        add(Beat.Says(3, SPOKEN))
        repeat(5) { add(Beat.Moves(5)) }
        add(Beat.Moves(2))
        add(Beat.Moves(2))
        add(Beat.Hushes)

        listOf(4, 5).forEach { line ->
            add(Beat.Says(line, SPOKEN))
            repeat(4) { add(Beat.Moves(5)) }
            add(Beat.Moves(2))
            add(Beat.Moves(2))
            add(Beat.Hushes)
        }

        add(Beat.Says(6, SPOKEN))
        repeat(3) { add(Beat.Moves(5)) }
        add(Beat.Moves(2))
        add(Beat.Moves(2))
        add(Beat.Hushes)

        listOf(7, 8).forEach { line ->
            add(Beat.Says(line, SPOKEN))
            repeat(4) { add(Beat.Moves(5)) }
            add(Beat.Moves(2))
            add(Beat.Moves(2))
            add(Beat.Hushes)
        }

        // Out of the dragon's room and into the corridor. The corridor is
        // shown and then kept, because the sheet that walks figures down it
        // replaces the one it was drawn from.
        add(Beat.Opens(TheFinale.KHELBEN_HURRIES))
        add(Beat.Shows)
        add(Beat.Opens(THE_CORRIDOR_FIGURES))
        add(Beat.KeepsTheView)

        add(Beat.Says(9, SPOKEN))
        add(Beat.Moves(7))
        add(Beat.Moves(8))
        add(Beat.Moves(7))
        add(Beat.Moves(7))
        add(Beat.Hushes)

        add(Beat.Says(10, SPOKEN))
        repeat(3) { add(Beat.Moves(7)) }
        add(Beat.Moves(8))
        add(Beat.Moves(7))
        add(Beat.Moves(7))
        add(Beat.Moves(8))
        add(Beat.Hushes)

        add(Beat.Says(11, SPOKEN))
        add(Beat.Moves(7))
        add(Beat.Moves(9))
        add(Beat.Moves(8))
        add(Beat.Hushes)

        // Outside. One sheet is opened for nothing but its colours and
        // replaced at once.
        add(Beat.Opens(THE_COLOURS_ONLY))
        add(Beat.Opens(TheFinale.THE_MAGES))
        add(Beat.Shows)

        add(Beat.Opens(THE_TEMPLE_FALLING))
        add(Beat.OpensAlongside(THE_MAGES_WORKING))
        add(Beat.Waits(10))

        add(Beat.Says(12, TOLD))
        add(Beat.Waits(90))
        add(Beat.Hushes)
        add(Beat.Sounds(A_SPELL_GOING_OUT))
        add(Beat.Waits(8))

        add(Beat.Moves(10))
        add(Beat.Moves(13))
        add(Beat.Moves(14))
        add(Beat.Moves(13))
        add(Beat.Moves(14))
        add(Beat.Moves(14))
        add(Beat.Moves(13))
        add(Beat.Moves(15))
        add(Beat.Moves(14))
        add(Beat.Moves(13))
        add(Beat.Moves(15))
        add(Beat.Moves(15))
        add(Beat.Moves(11))

        add(Beat.Says(13, TOLD))
        add(Beat.Waits(72))
        add(Beat.Hushes)
        add(Beat.Says(14, TOLD))
        add(Beat.Waits(72))
        add(Beat.Hushes)
        add(Beat.Sounds(A_SPELL_GOING_OUT))
        add(Beat.Waits(8))

        add(Beat.Moves(10))
        add(Beat.Moves(13))
        add(Beat.Moves(14))
        add(Beat.Moves(13))
        add(Beat.Moves(14))
        add(Beat.Moves(13))
        add(Beat.Moves(13))
        add(Beat.Moves(14))
        add(Beat.Moves(14))
        add(Beat.Moves(13))
        add(Beat.Moves(12))
        repeat(4) { add(Beat.Moves(16)) }
        add(Beat.Moves(17))
        add(Beat.Moves(18))

        add(Beat.Says(15, TOLD))
        add(Beat.Waits(36))
        add(Beat.Sounds(A_STONE_FALLING))
        add(Beat.Waits(54))
        add(Beat.Hushes)

        // And the last word, said over the party.
        add(Beat.Opens(TheFinale.THE_THANKS))
        add(Beat.Sounds(A_PICTURE_ARRIVING))
        add(Beat.Shows)
        add(Beat.Waits(18))

        add(Beat.Says(16, SPOKEN))
        add(Beat.Moves(20))
        add(Beat.Moves(19))
        add(Beat.Moves(19))
        add(Beat.Sounds(A_STONE_SETTLING))
        add(Beat.Hushes)

        add(Beat.Says(17, SPOKEN))
        add(Beat.Moves(19))
        add(Beat.Moves(20))
        add(Beat.Hushes)

        add(Beat.Says(18, SPOKEN))
        add(Beat.Sounds(A_STONE_FALLING))
        add(Beat.Moves(20))
        add(Beat.Moves(19))
        add(Beat.Moves(19))
        add(Beat.Sounds(A_STONE_FALLING))
        add(Beat.Waits(36))
        add(Beat.Hushes)

        add(Beat.Says(19, SPOKEN))
        add(Beat.Moves(19))
        add(Beat.Moves(19))
        add(Beat.Sounds(A_STONE_FALLING))
        add(Beat.Moves(20))
        add(Beat.Hushes)
        add(Beat.Waits(28))
        add(Beat.Sounds(A_STONE_SETTLING))
        add(Beat.Waits(3))

        add(Beat.Opens(TheFinale.THE_ASSAULT))
        add(Beat.Sounds(A_PICTURE_ARRIVING))
        add(Beat.Shows)
    }

    /**
     * The three noises the sequence makes itself, by their track in the bank.
     * Named for what they sound like rather than for anything the data says,
     * which names nothing.
     */
    private const val A_SPELL_GOING_OUT = 7
    private const val A_STONE_FALLING = 11
    private const val A_STONE_SETTLING = 12
    private const val A_PICTURE_ARRIVING = 6

    /**
     * The sheets the script names that are never shown, and so have no place
     * among the ones [TheFinale] names for their pictures.
     */
    private const val THE_CORRIDOR_FIGURES = 3
    private const val THE_COLOURS_ONLY = 7
    private const val THE_TEMPLE_FALLING = 8
    private const val THE_MAGES_WORKING = 6
}
