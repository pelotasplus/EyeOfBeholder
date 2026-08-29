package pl.pelotasplus.eyeofbeholder.data.repository

import co.touchlab.kermit.Logger
import pl.pelotasplus.eyeofbeholder.data.model.PcmClip
import pl.pelotasplus.eyeofbeholder.data.model.Volume

/**
 * The browser's speaker, over the Web Audio API.
 *
 * A browser will not make a sound until the player has done something to the
 * page, and refuses quietly rather than by failing — so the context is only
 * built when the first sound is asked for, by which time a key has been
 * pressed or the screen touched, and [wake] exists for a page that was loaded
 * and left to sit. Nothing here should throw at a caller who only wanted a
 * door to creak.
 *
 * Browsers do not agree on what counts as the player having done something.
 * One takes the first click as permission for everything after it, whenever it
 * comes; another only allows the speaker to be started from inside the event
 * itself, and the toolkit's own events are dispatched too late for that. So
 * the page is listened to directly as well, which is the one place both
 * readings hold.
 */
class WebAudioSink : AudioSink {

    private var context: WebAudioContext? = null
    private val playing = mutableSetOf<WebAudioVoice>()

    /**
     * Whether anything has been played from inside one of the player's own
     * events yet.
     *
     * Kept here rather than read back off the context, because the context
     * saying it is running is not the same as it having sounded: one built
     * inside the event says so from the moment it exists, which would skip the
     * very thing that has to happen.
     */
    private var hasSounded = false

    init {
        whenTheUserTouchesThePage { wake() }
    }

    override fun play(clip: PcmClip, volume: Volume, loop: Boolean): PlayingSound {
        val audio = context() ?: return NotPlaying

        // A clip started into a sleeping speaker is not heard and does not
        // fail, which is the whole difficulty here. Every clip says what state
        // the speaker was in, so three different silences can be told apart:
        // nothing asked for, asked for into a speaker that was asleep, and
        // asked for and started with nothing coming out.
        Logger.d(TAG) { "Playing ${clip.samples.size} samples into a ${audio.state} speaker" }
        if (!audio.isRunning) wake()

        return runCatching {
            val voice = audio.voice(clip.samples, clip.sampleRate, volume.gain, loop)
            playing.add(voice)
            voice.onEnded { playing.remove(voice) }
            voice.start()

            object : PlayingSound {
                override fun stop() {
                    if (playing.remove(voice)) voice.stop()
                }
            }
        }.getOrElse {
            Logger.w(TAG, it) { "Could not start a sound" }
            NotPlaying
        }
    }

    override fun stopAll() {
        playing.toList().forEach { it.stop() }
        playing.clear()
    }

    /**
     * A context built before any interaction starts suspended and stays that
     * way until it is resumed from inside a real event, so this has to be
     * reached from the player's own doing and not from a clock or a load.
     *
     * Asking it to resume is not always enough. A speaker can also want
     * something actually played before it counts as started, so a single
     * silent sample goes down the same path a clip does. Once the context is
     * running there is nothing left to do, and this costs nothing to call.
     */
    override fun wake() {
        val audio = context() ?: return

        // A speaker left alone is put back to sleep, so this asks every time
        // rather than settling the question once: what was true when the level
        // started is not what is true ten minutes into it. Finding it asleep
        // means the waking has to be done again from the top.
        if (!audio.isRunning) hasSounded = false
        if (hasSounded) return

        runCatching {
            audio.resume()
            audio.voice(SILENCE, SILENCE_RATE, gain = 0f, loop = false).start()
            hasSounded = true
        }.onFailure { Logger.w(TAG, it) { "Could not wake the speaker" } }
    }

    private fun context(): WebAudioContext? {
        context?.let { return it }

        return runCatching { WebAudioContext() }
            .onFailure { Logger.w(TAG, it) { "This browser has no audio" } }
            .getOrNull()
            ?.also { context = it }
    }

    private companion object {
        const val TAG = "AudioSink"

        /** One sample of nothing, which is enough to have played something. */
        val SILENCE = ShortArray(1)
        const val SILENCE_RATE = 22050
    }
}

/**
 * Runs [what] inside the browser's own event, every time the page is clicked,
 * typed at or touched.
 *
 * The listeners are never taken off again. Handing the same function back to
 * the browser to remove is not something the interop promises across both web
 * targets, and there is nothing to gain: once the speaker is running this is a
 * comparison and a return.
 */
internal expect fun whenTheUserTouchesThePage(what: () -> Unit)

/**
 * What the two web targets have to supply, because JavaScript interop is not
 * written the same way for each: `js` passes Kotlin values straight through,
 * `wasmJs` may only hand across what it can represent as a JavaScript value.
 */
internal interface WebAudioContext {
    fun voice(samples: ShortArray, sampleRate: Int, gain: Float, loop: Boolean): WebAudioVoice
    fun resume()

    /** What the browser calls the speaker's state, for saying so in a log. */
    val state: String

    /** Whether the speaker is up, as opposed to built but still held shut. */
    val isRunning: Boolean get() = state == "running"
}

internal interface WebAudioVoice {
    fun start()
    fun stop()
    fun onEnded(what: () -> Unit)
}

internal expect fun WebAudioContext(): WebAudioContext
