package pl.pelotasplus.eyeofbeholder.data.model

import kotlin.jvm.JvmInline

/**
 * What a decorated wall does, as against what it looks like.
 *
 * A level is free to draw a solid wall the party walk straight through, and to
 * stop them at a gap that shows nothing at all, so none of these can be
 * answered by what is on screen. The strange bushes on level 4 are the first
 * kind and the way to the hidden level is behind them.
 *
 * A level writes the third bit the other way round from the way the rest of
 * them read, which is turned back here so that nothing outside has to know.
 */
@JvmInline
value class WallFlags(private val written: Int) {

    private val bits: Int get() = written xor MONSTER

    /** The party walk through a wall that is marked for them. */
    val letThePartyThrough: Boolean get() = bits and PARTY != 0

    /**
     * A monster carries its own mark, and it is not the party's: a level can
     * draw a wall the party walk through and a monster cannot, which is how the
     * two guards on level 1 are kept behind their wall until the script that
     * lets them speak takes it down.
     */
    val letAMonsterThrough: Boolean get() = bits and MONSTER != 0

    /**
     * Putting something down beyond a wall, or taking it back, asks for any of
     * three marks rather than the party's one. So a grating stops the party
     * and still passes what can go between its bars.
     */
    val letAThingThrough: Boolean get() = bits and (PARTY or SMALL_THINGS or A_GAP) != 0

    /** A gap rather than a face: what is behind it is seen through it. */
    val seenThrough: Boolean get() = bits and A_GAP != 0

    /**
     * Whether what lies on the square is shown. An open alcove carries this
     * and shows what is shelved in it; a shelf that locks does not, and keeps
     * its scrolls until something unlocks it.
     */
    val showsItsContents: Boolean get() = bits and SHOWS_ITS_CONTENTS != 0

    private companion object {
        const val PARTY = 0x01

        /** Arrows and the like, which pass a grating the party cannot. */
        const val SMALL_THINGS = 0x02

        /**
         * What a monster may walk through. A level writes this bit the other
         * way round from the rest, so it is turned back before anything reads
         * it — set means passable, as the others do.
         */
        const val MONSTER = 0x04

        const val A_GAP = 0x08
        const val SHOWS_ITS_CONTENTS = 0x80
    }
}
