package pl.pelotasplus.eyeofbeholder.data.model

import kotlin.jvm.JvmInline

/**
 * A sound ready to be heard: signed 16-bit samples, one channel.
 *
 * Everything the game plays is short enough to hold whole, so a clip is the
 * unit a platform is handed — there is no streaming anywhere. That is what
 * lets one shape of code serve a browser and a phone alike, and it is why
 * where the samples came from is nobody's business but the repository's.
 */
class PcmClip(
    val samples: ShortArray,
    val sampleRate: Int,
) {
    val seconds: Float get() = samples.size.toFloat() / sampleRate
}

/**
 * How loud, on the original's scale, where 255 is as loud as a sound goes.
 *
 * The game asks for effects at a volume rather than at a gain — a spell
 * fading with distance counts down towards 0 — so that is the number kept,
 * and turning it into whatever a platform wants is the platform's job.
 */
@JvmInline
value class Volume(val raw: Int) {

    /** Linear, 0f..1f; the scale a mixer usually wants. */
    val gain: Float get() = raw.coerceIn(0, FULL_RAW) / FULL_RAW.toFloat()

    operator fun times(other: Volume) = Volume(raw * other.raw / FULL_RAW)

    companion object {
        private const val FULL_RAW = 255

        val FULL = Volume(FULL_RAW)
        val SILENT = Volume(0)
    }
}
