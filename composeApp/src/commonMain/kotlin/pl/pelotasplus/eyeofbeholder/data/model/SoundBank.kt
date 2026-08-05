package pl.pelotasplus.eyeofbeholder.data.model

import kotlin.jvm.JvmInline

/**
 * Which bank of sounds a sublevel plays out of.
 *
 * A bank is not one tune. It is up to 120 numbered tracks, most of them
 * effects a few tenths of a second long, and a level refers to them by number
 * throughout: the script's Sound instruction, a monster's attack and its
 * footsteps, a door being pulled open.
 *
 * The same bank is spelled differently depending on who is naming it — a
 * level says `catacomb`, the file on disk says `CATACOMB.ADL` — so a name is
 * put into one shape on the way in and two banks are the same bank whenever
 * they are the same bank.
 */
@JvmInline
value class SoundBank private constructor(val name: String) {

    override fun toString() = name

    companion object {
        operator fun invoke(named: String) =
            SoundBank(named.substringBeforeLast('.').uppercase())
    }
}

/**
 * Which track of a bank, as the game counts them.
 *
 * Track 0 is the driver's own silence — asking for it is how the original
 * stops what is playing — so it is never a sound anyone means to hear.
 */
@JvmInline
value class TrackIndex(val value: Int) {

    val audible: Boolean get() = value in FIRST..LAST

    override fun toString() = value.toString()

    companion object {
        const val FIRST = 1
        const val LAST = 119
    }
}
