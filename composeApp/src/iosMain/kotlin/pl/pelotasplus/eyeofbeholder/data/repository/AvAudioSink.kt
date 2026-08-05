package pl.pelotasplus.eyeofbeholder.data.repository

import co.touchlab.kermit.Logger
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.get
import kotlinx.cinterop.set
import pl.pelotasplus.eyeofbeholder.data.model.PcmClip
import pl.pelotasplus.eyeofbeholder.data.model.Volume
import platform.AVFAudio.AVAudioEngine
import platform.AVFAudio.AVAudioFormat
import platform.AVFAudio.AVAudioPCMBuffer
import platform.AVFAudio.AVAudioPCMFormatFloat32
import platform.AVFAudio.AVAudioPlayerNode
import platform.AVFAudio.AVAudioPlayerNodeBufferLoops
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryAmbient
import platform.AVFAudio.setActive

/**
 * The iOS speaker, over `AVAudioEngine`.
 *
 * The session is asked for the ambient category, which is what says this is a
 * game and not a music player: the sound stops when the phone is silenced and
 * it does not interrupt whatever the player was already listening to.
 *
 * One engine is shared, and a voice is a player node attached to it for as
 * long as its sound lasts.
 */
@OptIn(ExperimentalForeignApi::class)
class AvAudioSink : AudioSink {

    private val engine = AVAudioEngine()
    private val playing = mutableSetOf<AVAudioPlayerNode>()

    override fun play(clip: PcmClip, volume: Volume, loop: Boolean): PlayingSound {
        val format = AVAudioFormat(
            commonFormat = AVAudioPCMFormatFloat32,
            sampleRate = clip.sampleRate.toDouble(),
            channels = 1u,
            interleaved = false,
        )

        val buffer = AVAudioPCMBuffer(format, clip.samples.size.toUInt())
            ?: return NotPlaying.also { Logger.w(TAG) { "Could not make a buffer" } }

        val channel = buffer.floatChannelData?.get(0)
            ?: return NotPlaying.also { Logger.w(TAG) { "Buffer has no channel to fill" } }

        clip.samples.forEachIndexed { at, sample -> channel[at] = sample.toFloat() / FULL_SCALE }
        buffer.frameLength = clip.samples.size.toUInt()

        val node = AVAudioPlayerNode()

        return runCatching {
            engine.attachNode(node)
            engine.connect(node, engine.mainMixerNode, format)

            awake()
            if (!engine.isRunning()) engine.startAndReturnError(null)

            node.volume = volume.gain
            node.scheduleBuffer(
                buffer,
                null,
                if (loop) AVAudioPlayerNodeBufferLoops else 0u,
                if (loop) null else ({ detach(node) }),
            )

            playing.add(node)
            node.play()

            PlayingSound { detach(node) }
        }.getOrElse {
            Logger.w(TAG, it) { "Could not start a sound" }
            NotPlaying
        }
    }

    override fun stopAll() {
        playing.toList().forEach(::detach)
    }

    private fun detach(node: AVAudioPlayerNode) {
        if (!playing.remove(node)) return

        runCatching {
            node.stop()
            engine.detachNode(node)
        }.onFailure { Logger.w(TAG, it) { "Could not stop a sound" } }
    }

    private fun awake() {
        runCatching {
            AVAudioSession.sharedInstance().apply {
                setCategory(AVAudioSessionCategoryAmbient, null)
                setActive(true, null)
            }
        }.onFailure { Logger.w(TAG, it) { "Could not wake the audio session" } }
    }

    private companion object {
        const val TAG = "AudioSink"

        /** What a full scale 16-bit sample is, as a float between -1 and 1. */
        const val FULL_SCALE = 32768f
    }
}

private fun PlayingSound(stop: () -> Unit) = object : PlayingSound {
    override fun stop() = stop()
}
