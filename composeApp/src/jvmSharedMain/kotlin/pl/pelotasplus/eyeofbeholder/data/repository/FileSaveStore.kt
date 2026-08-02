package pl.pelotasplus.eyeofbeholder.data.repository

import java.io.File

/**
 * Saves as files in a directory of their own.
 *
 * Used by the desktop build and by tests, which point it at a temporary
 * directory and so need no store of their own.
 */
class FileSaveStore(private val directory: File) : SaveStore {

    override suspend fun written(): Set<SaveSlot> =
        SaveSlot.all.filterTo(mutableSetOf()) { fileFor(it).isFile }

    override suspend fun read(slot: SaveSlot): String? =
        fileFor(slot).takeIf { it.isFile }?.readText()

    override suspend fun write(slot: SaveSlot, contents: String) {
        directory.mkdirs()
        fileFor(slot).writeText(contents)
    }

    override suspend fun erase(slot: SaveSlot) {
        fileFor(slot).delete()
    }

    private fun fileFor(slot: SaveSlot) = File(directory, "${slot.name}.json")
}
