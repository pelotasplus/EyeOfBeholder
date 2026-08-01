package pl.pelotasplus.eyeofbeholder.data.repository

import co.touchlab.kermit.Logger
import pl.pelotasplus.eyeofbeholder.data.model.DialogueText
import pl.pelotasplus.eyeofbeholder.data.model.DialogueTextId

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
 *
 * ## Codes inside a string
 * ```
 * 0x01           stop here and wait to be read; what follows is the next page
 * 0x02, 0x06     set a colour; the byte after it is the colour
 * 0x0D           line break
 * ```
 * The colour byte is why the codes cannot simply be skipped over: it is an
 * argument, not a character, and reading it as one leaves litter in the speech.
 */
interface DialogueTextRepository {
    suspend fun text(id: DialogueTextId): Result<DialogueText>
}

class DialogueTextRepositoryImpl(
    private val resourceRepository: ResourceRepository,
) : DialogueTextRepository {

    private var file: UByteArray? = null

    override suspend fun text(id: DialogueTextId): Result<DialogueText> = runCatching {
        val bytes = file ?: resourceRepository.readResource("files/$FILE").also { file = it }

        require(id.number >= 1) { "Dialogue text ids are 1 based, got $id" }

        val offsetAt = (id.number - 1) * 2
        require(offsetAt + 1 < bytes.size) { "No dialogue text $id in $FILE" }

        val start = bytes[offsetAt].toInt() or (bytes[offsetAt + 1].toInt() shl 8)
        require(start < bytes.size) { "Dialogue text $id points past the end of $FILE" }

        val end = (start until bytes.size).firstOrNull { bytes[it].toInt() == 0 } ?: bytes.size

        val pages = mutableListOf<String>()
        val page = StringBuilder()

        var i = start
        while (i < end) {
            val byte = bytes[i].toInt()
            i++
            when {
                byte >= FIRST_PRINTABLE -> page.append(byte.toChar())
                byte == LINE_BREAK -> page.append('\n')
                byte == PAGE_BREAK -> {
                    pages += page.toString()
                    page.clear()
                }

                byte == SET_COLOR_1 || byte == SET_COLOR_2 -> i++
                else -> Unit
            }
        }
        pages += page.toString()

        // trimmed at the ends only: the four leading spaces indent a page's
        // first line, and the doubled ones space out its sentences
        DialogueText(pages.map { it.trim() }).also {
            Logger.d(TAG) { "Dialogue text $id: ${it.pages}" }
        }
    }

    private companion object {
        const val TAG = "DialogueTextRepository"
        const val FILE = "TEXT.DAT"

        const val PAGE_BREAK = 0x01
        const val SET_COLOR_2 = 0x02
        const val SET_COLOR_1 = 0x06
        const val LINE_BREAK = 0x0D
        const val FIRST_PRINTABLE = 0x20
    }
}
