package pl.pelotasplus.eyeofbeholder.data.repository

private external class Float32Array : JsAny

private external class AudioBuffer : JsAny {
    fun getChannelData(channel: Int): Float32Array
}

private external class AudioBufferSourceNode : JsAny {
    var buffer: AudioBuffer?
    var loop: Boolean
    var onended: (() -> Unit)?
    fun connect(destination: JsAny): JsAny
    fun start()
    fun stop()
}

private external class AudioParam : JsAny {
    var value: Float
}

private external class GainNode : JsAny {
    val gain: AudioParam
    fun connect(destination: JsAny): JsAny
}

private external class AudioContext : JsAny {
    val destination: JsAny
    val state: String
    fun createBuffer(channels: Int, frames: Int, sampleRate: Int): AudioBuffer
    fun createBufferSource(): AudioBufferSourceNode
    fun createGain(): GainNode
    fun resume()
}

private external class EventTarget : JsAny {
    fun addEventListener(type: String, listener: (JsAny) -> Unit, useCapture: Boolean)
}

private external val document: EventTarget

/**
 * Listening on the way down rather than on the way up, so the page is heard
 * from whether or not the canvas goes on to swallow the event.
 */
internal actual fun whenTheUserTouchesThePage(what: () -> Unit) {
    val fire: (JsAny) -> Unit = { what() }

    listOf("pointerdown", "keydown", "touchend").forEach { gesture ->
        document.addEventListener(gesture, fire, true)
    }
}

/**
 * Wasm cannot hand a Kotlin array over to JavaScript, so samples are copied
 * one at a time into the buffer the browser owns. It is a loop over a few tens
 * of thousands of numbers, once per clip and never once per frame.
 */
private fun writeSample(into: Float32Array, at: Int, value: Float) {
    js("into[at] = value")
}

internal actual fun WebAudioContext(): WebAudioContext = WasmWebAudioContext()

private class WasmWebAudioContext : WebAudioContext {

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
            writeSample(channel, at, samples[at].toFloat() / FULL_SCALE)
        }

        val source = context.createBufferSource()
        source.buffer = buffer
        source.loop = loop

        val volume = context.createGain()
        volume.gain.value = gain

        source.connect(volume)
        volume.connect(context.destination)

        return WasmWebAudioVoice(source)
    }

    override fun resume() {
        context.resume()
    }

    override val isRunning: Boolean get() = context.state == "running"

    private companion object {
        const val FULL_SCALE = 32768f
    }
}

private class WasmWebAudioVoice(private val source: AudioBufferSourceNode) : WebAudioVoice {
    override fun start() {
        source.start()
    }

    override fun stop() {
        source.stop()
    }

    override fun onEnded(what: () -> Unit) {
        source.onended = what
    }
}
