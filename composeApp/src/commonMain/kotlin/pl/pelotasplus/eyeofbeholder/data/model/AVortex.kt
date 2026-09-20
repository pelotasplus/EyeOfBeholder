package pl.pelotasplus.eyeofbeholder.data.model

import kotlin.random.Random

/**
 * The swirl of cold drawn over the view when a cone of cold is cast.
 *
 * A hundred and fifty sparks, each thrown in at the edge of a circle and
 * dragged towards the middle of the view, turning as they go. Each cycles
 * through its own colours and goes out; the whole thing is over when the last
 * of them has.
 *
 * Positions are carried in sixty-fourths of a pixel, which is what makes the
 * drag readable as a curve rather than a stair: a spark moves by less than a
 * pixel most frames, and rounding each step instead of the whole would lose
 * the difference.
 */
data class AVortex(
    val sparks: List<Spark>,
    /** How many steps have been taken, so a frame can be asked for by number. */
    val frame: Int = 0,
) {
    /**
     * One spark. [x] and [y] are sixty-fourths of a pixel from the middle of
     * the view; [dx] and [dy] the same per step. [litFor] holds it dark for
     * its first few frames, which is what keeps the hundred and fifty from
     * arriving as one ring.
     */
    data class Spark(
        val x: Int,
        val y: Int,
        val dx: Int,
        val dy: Int,
        val colour: Int,
        val colourStep: Int,
        val litFor: Int,
    ) {
        /** Which of the colours it is showing, or none once it has gone out. */
        val showing: PaletteIndex?
            get() {
                if (litFor > 0) return null
                val at = (colour shr 8).coerceIn(0, COLOURS.lastIndex)
                return COLOURS[at].takeIf { it != 0 }?.let(::PaletteIndex)
            }
    }

    /** Whether anything is still alight. */
    val stillGoing: Boolean get() = sparks.any { it.showing != null }

    /** Where each lit spark falls in the view, clipped to it. */
    fun overTheView(): List<Pair<ScreenX, ScreenY>> = sparks.mapNotNull { spark ->
        if (spark.showing == null) return@mapNotNull null
        val px = ((spark.x shr FRACTION) + MIDDLE_X).coerceIn(0, ViewPort.COLS - 1)
        val py = ((spark.y shr FRACTION) + MIDDLE_Y).coerceIn(0, ViewPort.ROWS - 1)
        ScreenX(px) to ScreenY(py)
    }

    /**
     * The next frame.
     *
     * A spark is pulled back towards the middle on both axes every step —
     * harder when it is already moving that way than when it is being turned
     * around, which is the whole of why the paths curve instead of falling
     * straight in.
     */
    fun stepped(): AVortex = copy(
        frame = frame + 1,
        sparks = sparks.map { spark ->
            if (spark.litFor > 0) return@map spark.copy(litFor = spark.litFor - 1)

            val dx = if (spark.x > 0) {
                spark.dx - (if (spark.dx > 0) STEP else DRAG)
            } else {
                spark.dx + (if (spark.dx < 0) STEP else DRAG)
            }

            val dy = if (spark.y > 0) {
                spark.dy - (if (spark.dy > 0) STEP else DRAG)
            } else {
                spark.dy + (if (spark.dy < 0) STEP else DRAG)
            }

            spark.copy(
                x = spark.x + dx,
                y = spark.y + dy,
                dx = dx,
                dy = dy,
                colour = spark.colour + spark.colourStep,
            )
        },
    )

    companion object {
        /**
         * The colours a spark shows in turn, as palette entries: white, pale
         * blue, white, pale blue, then darker, then out. Zero ends it.
         */
        val COLOURS = listOf(0x0F, 0x09, 0x0F, 0x09, 0x02, 0x0A, 0x02, 0x00)

        const val SPARKS = 150

        /**
         * How long the whole swirl takes, start to last spark.
         *
         * It is fixed rather than a per-frame wait because how many frames it
         * takes is not decided here: every spark goes out on a count of its
         * own, and how long the slowest of them takes depends on the draw. A
         * wait per frame would make that difference visible as a swirl that
         * lasts a different length of time each casting — and at a tick a
         * frame, eleven seconds of one.
         */
        const val LASTS_MILLISECONDS = 750L

        /** Redrawn at about sixty a second, however many steps that is. */
        const val A_FRAME_MILLISECONDS = 16L

        /**
         * How many steps are taken between one drawing and the next.
         *
         * A swirl runs a little over two hundred steps before its last spark
         * goes out, and three quarters of a second at sixty frames is about
         * forty-five drawings — so five steps a frame brings the two together.
         * Stepping once a frame is what made it last eleven seconds.
         */
        const val STEPS_A_FRAME = 5

        /** Where the swirl is centred in the view, which is not its middle. */
        const val MIDDLE_X = 88
        const val MIDDLE_Y = 48

        /** Sixty-fourths of a pixel: six bits of fraction under every position. */
        const val FRACTION = 6

        private const val RADIUS = 50 shl FRACTION
        private const val STEP = 10

        /**
         * The weaker pull, used on a spark being turned around rather than one
         * already heading in: seven eighths of a step, as halves, quarters and
         * eighths of it.
         */
        private const val DRAG = (STEP shr 1) + (STEP shr 2) + (STEP shr 3)

        /** How ragged the arrival is: colours run slower and sparks wait longer. */
        private const val DISORDER = 100

        /**
         * A vortex thrown from [random], which every spark's edge, direction,
         * speed and wait is drawn from — so a seeded one gives the same swirl
         * every time and can be frozen as a picture.
         */
        fun thrown(random: Random = Random.Default): AVortex = AVortex(
            sparks = List(SPARKS) {
                // How far out it starts, and how fast it is going when it
                // gets there: speed grows a step at a time while the distance
                // grows by the running total of them, so a spark thrown
                // further out is already moving faster.
                val out = random.nextInt(RADIUS shr 2, RADIUS + 1)
                var speed = 0
                var distance = 0
                while (distance < out) {
                    speed += STEP
                    distance += speed
                }

                // Which of the four edges it comes in over, and which way
                // round it is going when it does.
                val spun = if (random.nextBoolean()) -1 else 1
                val (x, y, dx, dy) = when (random.nextInt(4)) {
                    0 -> Four(OFF_CENTRE, distance, speed * spun, 0)
                    1 -> Four(distance, OFF_CENTRE, 0, speed * spun)
                    2 -> Four(OFF_CENTRE, -distance, speed * spun, 0)
                    else -> Four(-distance, OFF_CENTRE, 0, speed * spun)
                }

                Spark(
                    x = x,
                    y = y,
                    dx = dx,
                    dy = dy,
                    colour = 0,
                    colourStep = random.nextInt(1024 / DISORDER, 2048 / DISORDER + 1),
                    litFor = random.nextInt(0, (DISORDER shr 2) + 1),
                )
            },
        )

        /**
         * Every spark starts this far off the middle on the axis it is not
         * thrown along, so none of them runs exactly through the centre.
         */
        private const val OFF_CENTRE = 32

        private data class Four(val a: Int, val b: Int, val c: Int, val d: Int)
    }
}
