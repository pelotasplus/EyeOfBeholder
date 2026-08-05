package pl.pelotasplus.eyeofbeholder.data

import pl.pelotasplus.eyeofbeholder.data.model.PcmClip

/**
 * Reads the clips the AdLib banks were rendered into.
 *
 * ## Binary format
 * A RIFF file: a twelve byte header, then chunks, each a four byte name and a
 * little-endian length followed by that many bytes, padded to an even size.
 * ```
 * 0  4  "RIFF"
 * 4  4  bytes following, which nothing here needs
 * 8  4  "WAVE"
 * ```
 * Two chunks matter. `fmt ` says how to read the samples:
 * ```
 * 0  2  encoding, 1 for uncompressed samples
 * 2  2  channels
 * 4  4  samples per second
 * 8  4  bytes per second
 * 12 2  bytes per frame
 * 14 2  bits per sample
 * ```
 * and `data` is the samples themselves, in order, little-endian.
 *
 * Only what the game's own clips are is accepted — one channel of 16-bit
 * samples — because a file that is anything else is a mistake in the asset
 * pipeline, and reading it half-right would hide that.
 */
object WavReader {

    fun read(bytes: UByteArray): PcmClip {
        val reader = ByteReader(bytes)

        check(reader.readString(4, nullTerminated = false) == "RIFF") { "Not a RIFF file" }
        reader.readU32LE()
        check(reader.readString(4, nullTerminated = false) == "WAVE") { "Not a WAVE file" }

        var sampleRate = 0
        var samples: UByteArray? = null

        while (reader.remaining >= CHUNK_HEADER_SIZE) {
            val name = reader.readString(4, nullTerminated = false)
            val length = reader.readU32LE()

            when (name) {
                "fmt " -> {
                    val encoding = reader.readU16LE()
                    val channels = reader.readU16LE()
                    sampleRate = reader.readU32LE()
                    reader.readU32LE()
                    reader.readU16LE()
                    val bits = reader.readU16LE()

                    check(encoding == UNCOMPRESSED) { "Clip is encoding $encoding, not samples" }
                    check(channels == 1) { "Clip has $channels channels, expected one" }
                    check(bits == 16) { "Clip is $bits bits a sample, expected 16" }

                    reader.skip(length - FMT_SIZE)
                }

                "data" -> samples = reader.readBytes(length)

                else -> reader.skip(length)
            }

            // Chunks sit on even boundaries, so an odd one is followed by a
            // padding byte that is no part of its length.
            if (length % 2 == 1 && reader.remaining > 0) reader.skip(1)
        }

        val data = checkNotNull(samples) { "Clip has no samples" }
        check(sampleRate > 0) { "Clip does not say how fast to play" }

        return PcmClip(samples = data.toShortsLittleEndian(), sampleRate = sampleRate)
    }

    private const val CHUNK_HEADER_SIZE = 8
    private const val FMT_SIZE = 16
    private const val UNCOMPRESSED = 1
}

private fun UByteArray.toShortsLittleEndian(): ShortArray {
    val samples = ShortArray(size / 2)
    for (at in samples.indices) {
        val low = this[at * 2].toInt()
        val high = this[at * 2 + 1].toInt()
        samples[at] = ((high shl 8) or low).toShort()
    }
    return samples
}
