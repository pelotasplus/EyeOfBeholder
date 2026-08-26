package pl.pelotasplus.eyeofbeholder.data.repository

import kotlinx.browser.document

private external class AudioContext {
    val destination: dynamic
    val state: String
    fun createBuffer(channels: Int, frames: Int, sampleRate: Int): dynamic
    fun createBufferSource(): dynamic
    fun createGain(): dynamic
    fun resume()
}

/**
 * Listening on the way down rather than on the way up, so the page is heard
 * from whether or not the canvas goes on to swallow the event.
 */
internal actual fun whenTheUserTouchesThePage(what: () -> Unit) {
    listOf("pointerdown", "keydown", "touchend").forEach { gesture ->
        document.addEventListener(gesture, { what() }, true)
    }
}

internal actual fun WebAudioContext(): WebAudioContext = JsWebAudioContext()

private class JsWebAudioContext : WebAudioContext {

    private val context = AudioContext()

    override fun voice(
        samples: ShortArray,
        sampleRate: Int,
        gain: Float,
        loop: Boolean
    ): WebAudioVoice {
        val buffer = context.createBuffer(1, samples.size, sampleRate)
        val channel = buffer.getChannelData(0)
        for (at in samples.indices) {
            channel[at] = samples[at].toFloat() / FULL_SCALE
        }

        val source = context.createBufferSource()
        source.buffer = buffer
        source.loop = loop

        val volume = context.createGain()
        volume.gain.value = gain

        source.connect(volume)
        volume.connect(context.destination)

        return JsWebAudioVoice(source)
    }

    override fun resume() {
        context.resume()
    }

    override val isRunning: Boolean get() = context.state == "running"

    private companion object {
        const val FULL_SCALE = 32768f
    }
}

private class JsWebAudioVoice(private val source: dynamic) : WebAudioVoice {
    override fun start() {
        source.start()
    }

    override fun stop() {
        source.stop()
    }

    override fun onEnded(what: () -> Unit) {
        source.onended = { what() }
    }
}
