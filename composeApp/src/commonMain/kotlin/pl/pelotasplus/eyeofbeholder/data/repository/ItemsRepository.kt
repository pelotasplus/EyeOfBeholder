package pl.pelotasplus.eyeofbeholder.data.repository

import co.touchlab.kermit.Logger
import pl.pelotasplus.eyeofbeholder.data.ByteReader
import pl.pelotasplus.eyeofbeholder.data.model.Item

/**
 * Reads ITEM.DAT: the items the dungeon starts out holding.
 *
 * ## Binary format
 * - itemsCount (u16)
 * - that many 12-byte item records; see [Item] for the layout
 * - itemNamesCount (u16)
 * - that many 35-character names
 *
 * The names are read past. An item carries the number of its name rather than
 * the name itself, and nothing asks for one yet.
 *
 * This is only where a game begins. A save carries its own, longer table —
 * the party having made, moved and used things since — and reading one
 * replaces this list rather than adding to it.
 */
interface ItemsRepository {
    suspend fun loadItems(): Result<List<Item>>
}

class ItemsRepositoryImpl(
    private val resourceRepository: ResourceRepository
) : ItemsRepository {

    override suspend fun loadItems(): Result<List<Item>> {
        return runCatching {
            val bytes = resourceRepository.readResource("files/ITEM.DAT")
            val reader = ByteReader(bytes)

            val items = List(reader.readU16LE()) { Item.read(reader) }

            val itemNamesCount = reader.readU16LE()
            reader.skip(itemNamesCount * NAME_LENGTH)

            check(reader.remaining == 0) {
                "Unexpected ${reader.remaining} bytes after reading items data"
            }

            Logger.d(TAG) { "ITEM.DAT holds ${items.size} items and $itemNamesCount names" }

            items
        }
    }

    companion object {
        private const val TAG = "ItemsRepository"
        private const val NAME_LENGTH = 35
    }
}
