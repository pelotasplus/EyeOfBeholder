package pl.pelotasplus.eyeofbeholder.data.model.sequence

/**
 * The scene played when the thing at the end of the dungeon is finally killed.
 *
 * It is not a conversation and not a set piece a floor runs: it takes the
 * whole screen for several minutes, moving between thirteen pictures and
 * twenty lines while the party stand nowhere at all. Everything here is
 * transcribed — the pictures in the order the scene walks through them, the
 * colour tables it lights them with, and the words — and none of it is in the
 * level data, which is why it is written out rather than read.
 */
object TheFinale {

    /**
     * The sheets, numbered as the sequence numbers them rather than ordered as
     * it walks through them.
     *
     * Only five of the thirteen are ever put up as a picture — [DRAGON_ROOM],
     * [KHELBEN_HURRIES], [THE_MAGES], [THE_ASSAULT] and [THE_THANKS] — and one
     * more, [THE_HEROES], takes the whole screen with no window at all. The
     * rest never appear whole: they are cut up by the scene as it runs, and one
     * of them is loaded for nothing but the colours it carries.
     */
    val PICTURES = listOf(
        "DRAGON1.CPS",
        "DRAGON2.CPS",
        "HURRY1.CPS",
        "HURRY2.CPS",
        "DESTROY0.CPS",
        "DESTROY1.CPS",
        "DESTROY2.CPS",
        "MAGIC.CPS",
        "DESTROY3.CPS",
        "CREDITS2.CPS",
        "CREDITS3.CPS",
        "HEROES.CPS",
        "THANKS.CPS",
    )

    /**
     * The colour tables the scene is lit by, numbered as the fades ask for
     * them. The first is the one a picture is shown in before anything fades,
     * and it is named twice so that asking for table zero and table one gives
     * the same answer.
     */
    val COLOURS = listOf(
        "FINALE_0.PAL",
        "FINALE_0.PAL",
        "FINALE_1.PAL",
        "FINALE_2.PAL",
        "FINALE_3.PAL",
        "FINALE_4.PAL",
        "FINALE_5.PAL",
        "FINALE_6.PAL",
        "FINALE_7.PAL",
    )

    /**
     * What is said, in the order it is said. A return in one of these is a
     * line break rather than a pause: the strip takes as many rows as the line
     * has parts.
     */
    val WORDS = listOf(
        "Finally, Dran has been defeated.",
        "Suddenly, your friend Khelben appears.",
        "Greetings, my victorious friends.",
        "You have defeated Dran!",
        "I did not know Dran was a dragon.",
        "He must have been over 300 years old!",
        "His power is gone.",
        "But Darkmoon is still a source\rof great evil.",
        "And many of his minions remain.",
        "Now we must leave this place.",
        "So my forces can destroy it\ronce and for all.",
        "Follow me.",
        "Powerful mages stand ready\rfor the final assault\ron Darkmoon.",
        "The Temple's evil is very strong.",
        "It must not be allowed to survive!",
        "The Temple ceases to exist.",
        "My friends, our work is done.",
        "Thank you.",
        "You have earned my deepest respect.",
        "We will remember you always.",
    )

    /** Where a line breaks, which is not a character anybody should see. */
    const val A_LINE_BREAK = '\r'

    /** The room the dragon is dead in, which the ending opens on. */
    const val DRAGON_ROOM = 0

    /**
     * The sheet the one who arrives is cut out of. Nothing of it is ever shown
     * whole: it is him in five poses and the dragon's head in three, and the
     * room he walks into belongs to [DRAGON_ROOM].
     */
    const val KHELBEN_APPEARS = 1

    /** The corridor the party are hurried down once he has had his say. */
    const val KHELBEN_HURRIES = 2

    /** The mages standing ready outside, and the first of the two assaults. */
    const val THE_MAGES = 4
    const val THE_ASSAULT = 5

    /**
     * The six frames the party's own faces are set into, shown edge to edge
     * with no window. Nothing plays it yet: the faces have to be composed into
     * it first, and until they are it is six empty frames — see
     * [TheFinaleScript].
     */
    const val THE_HEROES = 11

    /** And the last word. */
    const val THE_THANKS = 12
}
