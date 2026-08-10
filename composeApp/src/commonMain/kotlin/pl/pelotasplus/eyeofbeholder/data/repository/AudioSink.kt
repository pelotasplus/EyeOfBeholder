package pl.pelotasplus.eyeofbeholder.data.repository

import pl.pelotasplus.eyeofbeholder.data.model.PcmClip
import pl.pelotasplus.eyeofbeholder.data.model.Volume

/**
 * The one thing about sound that no shared code can do: put samples out of a
 * speaker. Bound once per platform, the same way [SaveStore] is.
 *
 * The seam deals in samples rather than in files on purpose. It costs each
 * platform a dozen lines and no library, since none of them has to be told
 * what a `.WAV` or an `.OGG` is; and it leaves what fills a [PcmClip] free to
 * change — rendered ahead of time today, synthesised from the AdLib banks
 * later — without a single platform noticing.
 */
interface AudioSink {

    /**
     * Starts [clip] and returns at once. A looping sound plays until it is
     * stopped; a one-shot until it ends.
     */
    fun play(clip: PcmClip, volume: Volume = Volume.FULL, loop: Boolean = false): PlayingSound

    /** Silence, immediately: leaving a level, or turning sound off. */
    fun stopAll()

    /**
     * Told that the player has just done something, which is the only moment
     * some speakers may be started from.
     *
     * A browser will not make a sound until the page has been interacted with,
     * and refuses by staying quiet rather than by failing — so a page loaded
     * and left to sit has a speaker that never comes up, and nothing to say
     * so. Everywhere else there is nothing to do, which is why this does
     * nothing unless a platform says otherwise.
     */
    fun wake() = Unit
}

/** A sound that is being heard, for as long as anyone still wants it. */
interface PlayingSound {
    fun stop()
}

/** What a sink returns when there was nothing to play, or nothing to play it. */
object NotPlaying : PlayingSound {
    override fun stop() = Unit
}
