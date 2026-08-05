package pl.pelotasplus.eyeofbeholder.data.repository

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import co.touchlab.kermit.Logger
import pl.pelotasplus.eyeofbeholder.data.model.PcmClip
import pl.pelotasplus.eyeofbeholder.data.model.Volume

/**
 * Android's speaker, over `AudioTrack`.
 *
 * Every clip is held whole rather than fed in as it plays, which is what
 * `MODE_STATIC` is for. A track holds a hardware voice until it is released,
 * so a one-shot releases itself the moment its last frame has been reached.
 */
class AudioTrackAudioSink : AudioSink {

    private val playing = mutableSetOf<AudioTrack>()

    override fun play(clip: PcmClip, volume: Volume, loop: Boolean): PlayingSound {
        val frames = clip.samples.size

        val track = runCatching {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(clip.sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(frames * BYTES_A_FRAME)
                .build()
        }.getOrElse {
            Logger.w(TAG, it) { "Could not open a track" }
            return NotPlaying
        }

        track.write(clip.samples, 0, frames)
        track.setVolume(volume.gain)

        if (loop) {
            track.setLoopPoints(0, frames, LOOP_FOREVER)
        } else {
            track.notificationMarkerPosition = frames
            track.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
                override fun onMarkerReached(reached: AudioTrack) = release(reached)
                override fun onPeriodicNotification(ignored: AudioTrack) = Unit
            })
        }

        synchronized(playing) { playing.add(track) }
        track.play()

        return PlayingSound { release(track) }
    }

    override fun stopAll() {
        synchronized(playing) { playing.toList() }.forEach(::release)
    }

    private fun release(track: AudioTrack) {
        val wasPlaying = synchronized(playing) { playing.remove(track) }
        if (!wasPlaying) return

        runCatching {
            track.pause()
            track.flush()
            track.release()
        }.onFailure { Logger.w(TAG, it) { "Could not release a track" } }
    }

    private companion object {
        const val TAG = "AudioSink"
        const val BYTES_A_FRAME = 2
        const val LOOP_FOREVER = -1
    }
}

private fun PlayingSound(stop: () -> Unit) = object : PlayingSound {
    override fun stop() = stop()
}
