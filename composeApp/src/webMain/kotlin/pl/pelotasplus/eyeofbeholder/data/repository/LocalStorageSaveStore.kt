package pl.pelotasplus.eyeofbeholder.data.repository

import kotlinx.browser.localStorage

/**
 * Saves in the browser's local storage, under one key per slot.
 *
 * This survives closing the tab and coming back, but it is the browser's to
 * throw away: it may be evicted when the disk is short, and Safari clears it
 * after a week of not visiting the site. Exporting a save to a file is what
 * makes a copy that outlives the browser.
 */
class LocalStorageSaveStore : SaveStore {

    override suspend fun written(): Set<SaveSlot> =
        SaveSlot.all.filterTo(mutableSetOf()) { localStorage.getItem(keyFor(it)) != null }

    override suspend fun read(slot: SaveSlot): String? = localStorage.getItem(keyFor(slot))

    override suspend fun write(slot: SaveSlot, contents: String) {
        localStorage.setItem(keyFor(slot), contents)
    }

    override suspend fun erase(slot: SaveSlot) {
        localStorage.removeItem(keyFor(slot))
    }

    /** Namespaced, because the origin may be shared with whatever else is hosted there. */
    private fun keyFor(slot: SaveSlot) = "eob.save.${slot.name}"
}
