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
}

/** A sound that is being heard, for as long as anyone still wants it. */
interface PlayingSound {
    fun stop()
}

/** What a sink returns when there was nothing to play, or nothing to play it. */
object NotPlaying : PlayingSound {
    override fun stop() = Unit
}
