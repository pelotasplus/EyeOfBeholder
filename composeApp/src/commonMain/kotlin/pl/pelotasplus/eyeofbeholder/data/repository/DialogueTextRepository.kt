package pl.pelotasplus.eyeofbeholder.data.repository

import co.touchlab.kermit.Logger

/**
 * The dialogue strings in TEXT.DAT — what the characters the party meets
 * actually say.
 *
 * A level's own messages are the short lines it prints ("you can't go that
 * way", and the words on the dialogue buttons); the speeches belong to this
 * shared file. The Darkmoon priest's warning is number 28.
 *
 * A table of 16-bit offsets, one per string, followed by the strings
 * themselves, NUL terminated. Ids are 1-based, so string `n` starts at the
 * offset stored at `(n - 1) * 2`.
 */
interface DialogueTextRepository {
    suspend fun text(id: Int): Result<String>
}

class DialogueTextRepositoryImpl(
    private val resourceRepository: ResourceRepository,
) : DialogueTextRepository {

    private var file: UByteArray? = null

    override suspend fun text(id: Int): Result<String> = runCatching {
        val bytes = file ?: resourceRepository.readResource("files/$FILE").also { file = it }

        require(id >= 1) { "Dialogue text ids are 1 based, got $id" }

        val offsetAt = (id - 1) * 2
        require(offsetAt + 1 < bytes.size) { "No dialogue text $id in $FILE" }

        val start = bytes[offsetAt].toInt() or (bytes[offsetAt + 1].toInt() shl 8)
        require(start < bytes.size) { "Dialogue text $id points past the end of $FILE" }

        val end = (start until bytes.size).firstOrNull { bytes[it].toInt() == 0 } ?: bytes.size

        // trimmed at the end only: the four leading spaces indent the speech's
        // first line, and the doubled ones space out its sentences
        buildString {
            for (i in start until end) {
                val byte = bytes[i].toInt()
                // the originals carry colour and page-break codes inline
                when {
                    byte >= 0x20 -> append(byte.toChar())
                    byte == 0x0D -> append('\n')
                    else -> Unit
                }
            }
        }.trimEnd().also {
            Logger.d(TAG) { "Dialogue text $id: $it" }
        }
    }

    private companion object {
        const val TAG = "DialogueTextRepository"
        const val FILE = "TEXT.DAT"
    }
}
