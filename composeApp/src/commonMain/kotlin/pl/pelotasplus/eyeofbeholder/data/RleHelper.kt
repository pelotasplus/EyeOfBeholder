package pl.pelotasplus.eyeofbeholder.data

/**
 * Run-length decompression, the other way a Westwood file can be packed.
 *
 * Almost everything in the game is LCW; this is what a handful of files use
 * instead, and which one a file is is the compression type in its header
 * rather than anything about its contents.
 *
 * ## The command stream
 * One signed byte says what comes next:
 *
 * 1. **Positive** — that many bytes follow and are copied as they are.
 * 2. **Negative** — the next byte, repeated that many times.
 * 3. **Zero** — a run too long to count in a byte: a 16-bit length, high byte
 *    first, and then the byte to fill it with.
 *
 * There is no end marker; the stream is finished when the image is full. Every
 * count is clamped to the room left, so a stream that overruns leaves a
 * partial image rather than failing, which is what [LCWHelper] does with the
 * shipped files that overrun.
 */
object RleHelper {

    fun decompress(source: UByteArray, dest: UByteArray) {
        var sp = 0
        var dp = 0

        while (dp < dest.size && sp < source.size) {
            val command = source[sp++].toByte().toInt()

            when {
                command > 0 -> {
                    val run = minOf(command, dest.size - dp, source.size - sp)
                    repeat(run) { dest[dp++] = source[sp++] }
                }

                command < 0 -> {
                    if (sp >= source.size) return
                    val value = source[sp++]
                    repeat(minOf(-command, dest.size - dp)) { dest[dp++] = value }
                }

                else -> {
                    if (sp + 2 >= source.size) return
                    val run = (source[sp].toInt() shl 8) or source[sp + 1].toInt()
                    sp += 2
                    val value = source[sp++]
                    repeat(minOf(run, dest.size - dp)) { dest[dp++] = value }
                }
            }
        }
    }
}
