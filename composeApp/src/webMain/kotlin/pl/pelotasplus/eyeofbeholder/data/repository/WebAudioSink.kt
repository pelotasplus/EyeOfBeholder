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
 */
class WebAudioSink : AudioSink {

    private var context: WebAudioContext? = null
    private val playing = mutableSetOf<WebAudioVoice>()

    override fun play(clip: PcmClip, volume: Volume, loop: Boolean): PlayingSound {
        val audio = context() ?: return NotPlaying

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
     * To be called from something the player did. A context built before any
     * interaction starts suspended and stays that way until it is resumed
     * from inside a real event.
     */
    fun wake() {
        context()?.resume()
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
    }
}

/**
 * What the two web targets have to supply, because JavaScript interop is not
 * written the same way for each: `js` passes Kotlin values straight through,
 * `wasmJs` may only hand across what it can represent as a JavaScript value.
 */
internal interface WebAudioContext {
    fun voice(samples: ShortArray, sampleRate: Int, gain: Float, loop: Boolean): WebAudioVoice
    fun resume()
}

internal interface WebAudioVoice {
    fun start()
    fun stop()
    fun onEnded(what: () -> Unit)
}

internal expect fun WebAudioContext(): WebAudioContext
