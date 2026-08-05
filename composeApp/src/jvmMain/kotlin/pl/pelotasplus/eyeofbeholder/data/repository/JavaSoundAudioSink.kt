package pl.pelotasplus.eyeofbeholder.data.repository

import co.touchlab.kermit.Logger
import pl.pelotasplus.eyeofbeholder.data.model.PcmClip
import pl.pelotasplus.eyeofbeholder.data.model.Volume
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Clip
import javax.sound.sampled.FloatControl
import javax.sound.sampled.LineEvent
import kotlin.math.log10

/**
 * The desktop's speaker, over `javax.sound.sampled`.
 *
 * A line is a scarce system resource rather than an object the collector will
 * tidy up, so every one opened here is closed again when its sound ends.
 */
class JavaSoundAudioSink : AudioSink {

    private val playing = mutableSetOf<Clip>()

    override fun play(clip: PcmClip, volume: Volume, loop: Boolean): PlayingSound {
        val line = runCatching {
            AudioSystem.getClip().apply {
                open(formatFor(clip), clip.samples.toLittleEndianBytes(), 0, clip.samples.size * 2)
            }
        }.getOrElse {
            // A machine with no sound card, or every line already in use. The
            // game is still playable, so this is not worth failing over.
            Logger.w("AudioSink", it) { "Could not open a line" }
            return NotPlaying
        }

        line.attenuate(volume)
        line.addLineListener { event ->
            if (event.type == LineEvent.Type.STOP) {
                synchronized(playing) { playing.remove(line) }
                line.close()
            }
        }
        synchronized(playing) { playing.add(line) }

        if (loop) line.loop(Clip.LOOP_CONTINUOUSLY) else line.start()

        return PlayingSound { line.stop() }
    }

    override fun stopAll() {
        synchronized(playing) { playing.toList() }.forEach { it.stop() }
    }

    private fun formatFor(clip: PcmClip) =
        AudioFormat(clip.sampleRate.toFloat(), BITS, CHANNELS, true, false)

    /**
     * Master gain is in decibels, and silence is not -80 dB but no sound at
     * all, which the control cannot say — so mute by muting.
     */
    private fun Clip.attenuate(volume: Volume) {
        val control = runCatching {
            getControl(FloatControl.Type.MASTER_GAIN) as FloatControl
        }.getOrNull() ?: return

        control.value = if (volume.gain <= 0f) {
            control.minimum
        } else {
            (20f * log10(volume.gain)).coerceIn(control.minimum, control.maximum)
        }
    }

    private companion object {
        const val BITS = 16
        const val CHANNELS = 1
    }
}

private fun ShortArray.toLittleEndianBytes(): ByteArray {
    val bytes = ByteArray(size * 2)
    forEachIndexed { at, sample ->
        bytes[at * 2] = (sample.toInt() and 0xFF).toByte()
        bytes[at * 2 + 1] = (sample.toInt() shr 8).toByte()
    }
    return bytes
}

private fun PlayingSound(stop: () -> Unit) = object : PlayingSound {
    override fun stop() = stop()
}
