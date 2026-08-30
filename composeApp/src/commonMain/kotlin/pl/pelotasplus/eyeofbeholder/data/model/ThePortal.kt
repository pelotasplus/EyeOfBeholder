package pl.pelotasplus.eyeofbeholder.data.model

/**
 * The portal opening, which is what a stone gem taken to one of the temple's
 * arches gets you before it puts the party down somewhere else.
 *
 * Two pictures make it. PORTALA holds the arch itself in five stages of
 * opening — a lintel and two pillars, each drawn from its own row — and
 * PORTALB holds ten tiles of what shows through the middle once it is open,
 * five to a row. A step names one stage of the arch and one of those tiles,
 * and the opening is a list of such steps.
 *
 * The numbers here are transcribed rather than worked out, so they are not to
 * be re-derived or nudged by eye.
 */
object ThePortal {

    /** Where a piece goes, in the screen's own 320x200. */
    data class At(val x: Int, val y: Int)

    /** And what is cut out of a picture to put there. */
    data class Cut(val x: Int, val y: Int, val wide: Int, val high: Int)

    val LINTEL_AT = At(28, 9)
    val LEFT_PILLAR_AT = At(34, 28)
    val RIGHT_PILLAR_AT = At(120, 28)

    /** Where what shows through it goes, which is one size for all of them. */
    val MIDDLE_AT = At(56, 27)
    const val MIDDLE_WIDE = 64
    const val MIDDLE_HIGH = 77

    /** Cut from PORTALA: the arch in its five stages. */
    fun lintel(stage: Int) = Cut(120, stage * 18, 120, 18)
    fun leftPillar(stage: Int) = Cut(stage * 24, 0, 24, 75)
    fun rightPillar(stage: Int) = Cut(stage * 24, 80, 24, 75)

    /** And the shut doorway, which is what the middle shows before it opens. */
    val SHUT = Cut(240, 0, MIDDLE_WIDE, MIDDLE_HIGH)

    /** Cut from PORTALB: the tiles, five to a row. */
    fun showingThrough(tile: Int) =
        Cut((tile % 5) * MIDDLE_WIDE, (tile / 5) * MIDDLE_HIGH, MIDDLE_WIDE, MIDDLE_HIGH)

    /** Heard as the arch is first put up, before any of it moves. */
    val AS_IT_APPEARS = listOf(TrackIndex(33), TrackIndex(19))

    /** How long the arch stands before it begins to open. */
    val BEFORE_IT_STIRS = Ticks(30)

    /** And how long each step of the opening is held. */
    val A_STEP = Ticks(2)

    /**
     * A step of the opening with the two pictures it is cut from, which is
     * everything needed to draw it.
     *
     * @property arch PORTALA, which holds the archway itself.
     * @property through PORTALB, which holds what shows once it is open.
     */
    data class Showing(val step: Step, val arch: Cps, val through: Cps)

    /**
     * One step: which stage the arch is in, what shows through the middle, and
     * what is heard as it is drawn.
     *
     * @property showing null for the shut doorway, otherwise which tile of
     *   PORTALB shows through.
     */
    data class Step(
        val arch: Int,
        val showing: Int?,
        val sounds: List<TrackIndex> = emptyList(),
    )

    /**
     * The whole opening, as pairs of an arch stage and a middle, ending in a
     * pair of 0xFF.
     *
     * The middle counts from one so that zero can mean the shut doorway.
     */
    private val WRITTEN_AS = listOf(
        0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x01, 0x00, 0x02, 0x00, 0x01, 0x00,
        0x00, 0x00, 0x01, 0x00, 0x02, 0x00, 0x03, 0x00,
        0x02, 0x00, 0x01, 0x00, 0x02, 0x00, 0x03, 0x00,
        0x04, 0x00, 0x03, 0x00, 0x02, 0x00, 0x01, 0x00,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00,
        0x00, 0x01, 0x00, 0x02, 0x00, 0x01, 0x00, 0x00,
        0x00, 0x01, 0x00, 0x02, 0x00, 0x03, 0x00, 0x04,
        0x00, 0x01, 0x01, 0x00, 0x02, 0x01, 0x03, 0x02,
        0x02, 0x03, 0x01, 0x04, 0x00, 0x02, 0x01, 0x03,
        0x02, 0x04, 0x03, 0x02, 0x04, 0x03, 0x03, 0x04,
        0x02, 0x02, 0x01, 0x01, 0x00, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x01, 0x01, 0x02, 0x02, 0x03, 0x03,
        0x04, 0x04, 0x04, 0x05, 0x04, 0x06, 0x03, 0x07,
        0x02, 0x08, 0x02, 0x08, 0x01, 0x09, 0x01, 0x09,
        0x01, 0x09, 0x00, 0x0A, 0xFF, 0xFF,
    )

    /**
     * What is heard at a step, which depends on the step before it.
     *
     * The arch groans as it starts to move again, so the sound belongs to the
     * step that leaves stage nought rather than to any stage on its own. The
     * other two go with what shows through.
     */
    private fun heardAt(arch: Int, middle: Int, archBefore: Int?): List<TrackIndex> {
        val sounds = mutableListOf<TrackIndex>()

        if (arch == 1 && archBefore == 0) {
            sounds += TrackIndex(24)
            sounds += TrackIndex(86)
        }

        if (middle != 0) {
            when (middle - 1) {
                1 -> sounds += TrackIndex(31)
                3 -> if (arch == 3) sounds += TrackIndex(90)
            }
        }

        return sounds
    }

    val OPENS: List<Step> = buildList {
        var archBefore: Int? = null

        WRITTEN_AS.chunked(2)
            .takeWhile { (arch, _) -> arch != 0xFF }
            .forEach { (arch, middle) ->
                add(
                    Step(
                        arch = arch,
                        showing = if (middle == 0) null else middle - 1,
                        sounds = heardAt(arch, middle, archBefore),
                    ),
                )
                archBefore = arch
            }
    }
}
