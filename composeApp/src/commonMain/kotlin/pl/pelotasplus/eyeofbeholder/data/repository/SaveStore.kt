package pl.pelotasplus.eyeofbeholder.data.repository

import kotlin.jvm.JvmInline

/**
 * Where saved games are kept, whatever "kept" means on this platform.
 *
 * Files on a desktop or a phone; the browser's local storage on the web, which
 * survives closing the tab but is the browser's to evict. Somewhere behind an
 * API one day, which is why this is an interface and not a file path: nothing
 * above it knows or cares.
 *
 * It deals in text rather than in a game, because what a save means is
 * [SavedGame]'s business and where it lives is this one's.
 */
interface SaveStore {
    suspend fun written(): Set<SaveSlot>

    suspend fun read(slot: SaveSlot): String?

    suspend fun write(slot: SaveSlot, contents: String)

    suspend fun erase(slot: SaveSlot)
}

/**
 * One of the six a player is offered, or the autosave alongside them.
 *
 * The autosave is deliberately not a seventh numbered slot: the six are the
 * player's to name and overwrite, and the game writing over one of them
 * because it happened to be next would be the game losing their work.
 */
@JvmInline
value class SaveSlot private constructor(val name: String) {

    companion object {
        /** As many as the Load Game list shows. */
        const val COUNT = 6

        val AUTOSAVE = SaveSlot("autosave")

        /** The player's own slots, in the order the list shows them. */
        val numbered: List<SaveSlot> = (1..COUNT).map { SaveSlot("slot$it") }

        val all: List<SaveSlot> = numbered + AUTOSAVE
    }
}
