package pl.pelotasplus.eyeofbeholder.data.repository

private external class AudioContext {
    val destination: dynamic
    fun createBuffer(channels: Int, frames: Int, sampleRate: Int): dynamic
    fun createBufferSource(): dynamic
    fun createGain(): dynamic
    fun resume()
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
