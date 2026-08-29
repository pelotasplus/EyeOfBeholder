package pl.pelotasplus.eyeofbeholder.data.model

/**
 * A fireball going off, as a handful of sparks thrown outward and falling.
 *
 * Not a picture: there is no drawing of an explosion anywhere in the game's
 * files. Thirty-five sparks are flung from one point with a random push
 * each, gravity pulls on them, and each walks its own way down a short list
 * of colours and winks out when it reaches the end of it. What is on screen
 * is wherever they happen to be.
 *
 * Every number here is the game's own. The colours are palette entries, and
 * the last of them being nothing is what says a spark is spent.
 */
data class Burst(
    /** The square it is going off on. Where that lands on screen is the view's business. */
    val at: Location,

    /** Whether it went off on the party's own square rather than one they can see. */
    val inYourFace: Boolean = false,

    val sparks: List<Spark>,
) {
    /** Whether anything is still burning. */
    val burning: Boolean get() = sparks.any { it.lit }

    /**
     * One spark.
     *
     * [x] and [y] are in sixty-fourths of a pixel before the shrinking, which
     * is how the game carries them: whole pixels alone are too coarse a step
     * for something thrown this hard.
     */
    data class Spark(
        val x: Int,
        val y: Int,
        val goingAcross: Int,
        val goingDown: Int,
        /** How far along its colours it is, in two hundred and fifty sixths. */
        val through: Int,
        val fading: Int,
    ) {
        val colour: PaletteIndex?
            get() = COLOURS.getOrNull(through shr 8)?.takeIf { it != 0 }?.let(::PaletteIndex)

        val lit: Boolean get() = colour != null
    }

    /**
     * The burst [steps] moments later.
     *
     * A burst runs its own race rather than the world's, on a clock of its
     * own and a good deal faster: the whole thing is over in about a third of
     * a second, and the world's clock has only three turns in that.
     */
    fun onward(steps: Int = STEPS_PER_TURN): Burst {
        var carried = this
        repeat(steps) { carried = carried.copy(sparks = carried.sparks.map(carried::moved)) }
        return carried
    }

    private fun moved(spark: Spark): Spark {
        if (!spark.lit) return spark

        // The push it was given bleeds away a little at a time, while what
        // pulls it down only ever grows: so a spark goes out and up, slows,
        // and comes back down steeper than it left.
        val across = if (spark.goingAcross <= 0) spark.goingAcross + 1 else spark.goingAcross - 1
        val down = spark.goingDown + PULLED_DOWN

        val x = spark.x + across
        val y = spark.y + down

        // The floor is not fallen through: a spark that reaches it comes back
        // off it with half the speed it arrived. Measured before the shrinking
        // rather than after, so a burst bounces the same however far off it is
        // and the party walking while it burns cannot change its shape.
        val bounced = (y shr 6) + FROM_THE_TOP !in 0..FLOOR

        return spark.copy(
            x = x,
            y = y,
            goingAcross = across,
            goingDown = if (bounced) -(down shr 1) else down,
            through = spark.through + spark.fading,
        )
    }

    companion object {
        /**
         * A burst just gone off on [at], with every spark still at its middle.
         *
         * One going off on the party's own square is a different thing and the
         * game throws it differently: half again as many sparks, three times
         * as hard, and much less of that upward. A fireball at a distance
         * climbs and falls where you can watch it; one in your face sprays
         * outward across the whole view.
         */
        fun of(at: Location, dice: Dice = Dice.random, inYourFace: Boolean = false): Burst {
            val sparks = if (inYourFace) SPARKS_CLOSE_UP else SPARKS
            val thrown = if (inYourFace) THROWN_CLOSE_UP else THROWN
            val upward = thrown shr (if (inYourFace) LIFT_CLOSE_UP else LIFT)

            return Burst(
                at = at,
                inYourFace = inYourFace,
                sparks = List(sparks) {
                    Spark(
                        x = 0,
                        y = 0,
                        goingAcross = dice.roll(1, thrown + 1, -1) - thrown / 2,
                        goingDown = dice.roll(1, thrown + 1, -1) - thrown / 2 - upward,
                        through = 0,
                        fading = dice.roll(
                            1,
                            FADES_FASTEST - FADES_SLOWEST + 1,
                            FADES_SLOWEST - 1,
                        ),
                    )
                },
            )
        }

        /** The colours a spark burns through, and nothing at the end of them. */
        private val COLOURS = listOf(15, 5, 15, 5, 6, 5, 6, 8, 6, 8, 6, 8, 0)

        /** How many sparks one makes. */
        const val SPARKS = 35

        /**
         * How many steps a burst takes for each turn of the world's clock.
         *
         * The whole thing is about forty steps and the game gives them
         * something under a third of a second between them, which is three
         * turns of this clock rather than the seven a gentler pace would take.
         * A fireball going off is a flash, not a bonfire.
         */
        const val STEPS_PER_TURN = 3

        /**
         * How long between one picture of a burst and the next.
         *
         * Fast enough that a third of a second holds a dozen of them, which is
         * what makes it read as sparks flying rather than as three stills.
         */
        const val A_FRAME = 25L

        /** How hard each is thrown, either way from the middle of that. */
        private const val THROWN = 147

        /** And the three of those numbers again, for one going off on top of you. */
        private const val SPARKS_CLOSE_UP = 50
        private const val THROWN_CLOSE_UP = 460

        /**
         * How much of the throw goes upward rather than outward, as a shift of
         * how hard it was thrown. The larger the shift the flatter the spray.
         */
        private const val LIFT = 1
        private const val LIFT_CLOSE_UP = 4

        /** What gravity adds to a spark's fall on every turn of it. */
        private const val PULLED_DOWN = 5

        private const val FADES_SLOWEST = 1024 / 20
        private const val FADES_FASTEST = 2048 / 20

        /** Where the middle of a burst sits up the view, and how far it can fall. */
        const val FROM_THE_TOP = 48
        private const val FLOOR = 120
    }
}
