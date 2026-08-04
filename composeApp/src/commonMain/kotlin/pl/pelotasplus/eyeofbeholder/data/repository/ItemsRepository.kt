package pl.pelotasplus.eyeofbeholder.data.repository

import co.touchlab.kermit.Logger
import pl.pelotasplus.eyeofbeholder.data.ByteReader
import pl.pelotasplus.eyeofbeholder.data.model.Item
import pl.pelotasplus.eyeofbeholder.data.model.ItemDefinitions
import pl.pelotasplus.eyeofbeholder.data.model.ItemNames

/**
 * Reads ITEM.DAT: the items the dungeon starts out holding.
 *
 * ## Binary format
 * - itemsCount (u16)
 * - that many 12-byte item records; see [Item] for the layout
 * - itemNamesCount (u16)
 * - that many 35-character names
 *
 * The items here are only where a game begins: a save carries its own, longer
 * table — the party having made, moved and used things since — and reading
 * one replaces this list rather than adding to it. The names are not like
 * that. They are the same whoever wrote the save, which is why an item
 * carries the number of its name and not the name.
 */
interface ItemsRepository {
    suspend fun loadItems(): Result<ItemDefinitions>
}

class ItemsRepositoryImpl(
    private val resourceRepository: ResourceRepository
) : ItemsRepository {

    override suspend fun loadItems(): Result<ItemDefinitions> {
        return runCatching {
            val bytes = resourceRepository.readResource("files/ITEM.DAT")
            val reader = ByteReader(bytes)

            val items = List(reader.readU16LE()) { Item.read(reader) }
            val names = List(reader.readU16LE()) { reader.readString(NAME_LENGTH) }

            check(reader.remaining == 0) {
                "Unexpected ${reader.remaining} bytes after reading items data"
            }

            Logger.d(TAG) { "ITEM.DAT holds ${items.size} items and ${names.size} names" }

            ItemDefinitions(items, ItemNames(names))
        }
    }

    companion object {
        private const val TAG = "ItemsRepository"
        private const val NAME_LENGTH = 35
    }
}
