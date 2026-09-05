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
 * How loud, on a scale where 255 is as loud as a sound goes.
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

        /**
         * How loud something happening [squares] away is heard, and whether it
         * is heard at all.
         *
         * Walls do not stop sound in this dungeon; distance does. Each square
         * takes a sixteenth of full volume off, so what is one away is nearly
         * as loud as being there and what is fifteen away is not heard at all.
         */
        fun asFarOffAs(squares: Int): Volume =
            Volume(QUIETER_PER_SQUARE * (AS_FAR_AS_IT_CARRIES - squares).coerceAtLeast(0))

        /** Which is as far as any of it carries. */
        const val AS_FAR_AS_IT_CARRIES = 15

        private const val QUIETER_PER_SQUARE = 16
    }
}
