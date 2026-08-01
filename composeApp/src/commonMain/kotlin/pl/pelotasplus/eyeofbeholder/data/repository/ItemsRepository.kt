package pl.pelotasplus.eyeofbeholder.data.repository

import co.touchlab.kermit.Logger
import pl.pelotasplus.eyeofbeholder.data.ByteReader
import pl.pelotasplus.eyeofbeholder.data.model.Item
import pl.pelotasplus.eyeofbeholder.data.model.ItemIconId
import pl.pelotasplus.eyeofbeholder.data.model.ItemTypeId
import pl.pelotasplus.eyeofbeholder.data.model.Location

/**
 * Parses the global ITEM.DAT file containing all item instances in the game.
 *
 * ## ITEM.DAT binary format
 * - itemsCount (u16) — total number of items
 * - For each item (12 bytes):
 *   - nameUnidentifiedId (u8) — index into name table
 *   - nameIdentifiedId (u8) — index into name table
 *   - flags (u8) — bit 7 = identified, others = cursed/magical/etc.
 *   - icon (i8) — index into ITEMS1.CPS shape map for rendering
 *   - type (i8) — item category (references ITEMTYPE.DAT)
 *   - pos (i8) — position within a square: 0-3 = floor quadrant, 8 = wall niche
 *   - location (u16 packed) — maze grid position (x in bits 0-4, y = value/32)
 *   - next (i16LE) — linked-list pointer to next item at same location (-1 = end)
 *   - prev (i16LE) — linked-list pointer to previous item (-1 = start)
 *   - level (u8) — which dungeon level this item belongs to
 *   - value (i8) — context-dependent: magical bonus, charges, key ID, etc.
 * - itemNamesCount (u16)
 * - For each name: 35-char fixed-length null-terminated string
 *
 * ## Item linked lists
 * Items at the same location form a doubly-linked list via next/prev indices.
 * This mirrors the original game's memory-efficient storage where each maze
 * square only stores the head item index, and you follow the chain to find all items.
 *
 * ## Item positions (pos field)
 * ```
 * 0-3 = floor quadrants (NW=0, NE=1, SW=2, SE=3) — items lying on the ground
 * 8   = wall niche/alcove — items placed in a wall recess (visible in viewport)
 * ```
 */
interface ItemsRepository {
    suspend fun loadItems(): Result<List<Item>>
}

class ItemsRepositoryImpl(
    private val resourceRepository: ResourceRepository
) : ItemsRepository {

    override suspend fun loadItems(): Result<List<Item>> {
        return runCatching {
            val itemSmall = resourceRepository.decompressResource("files/ITEMS1.CPS").bytes
            Logger.d(TAG) { "XXX Item small size ${itemSmall.size}" }

            val bytes = resourceRepository.readResource("files/ITEM.DAT")
            val reader = ByteReader(bytes)

            val itemsCount = reader.readU16LE()

            val items = buildList {
                repeat(itemsCount) {
                    add(
                        Item(
                            nameUnidentifiedId = reader.readU8(),
                            nameIdentifiedId = reader.readU8(),
                            flags = reader.readU8(), // flags (e.g. 128 = identified)
                            icon = ItemIconId(reader.readI8()), // → ITEMICN + shape map
                            type = ItemTypeId(reader.readI8()), // → itemtype.dat
                            pos = reader.readI8(), // position (0-3 floor, 8 niche)
                            location = Location.read(reader),
                            next = reader.readI16LE(),
                            prev = reader.readI16LE(),
                            level = reader.readU8(),
                            value = reader.readI8() // magical bonus value
                        )
                    )
                }
            }

            Logger.d(TAG) { "Left bytes after reading items ${reader.remaining}" }

            val itemNamesCount = reader.readU16LE()
            val names = buildList {
                repeat(itemNamesCount) {
                    add(reader.readString(35))
                }
            }

            val ret = items.map {
                it.copy(
                    nameIdentified = names[it.nameIdentifiedId],
                    nameUnidentified = names[it.nameUnidentifiedId]
                )
            }

            check(reader.remaining == 0) {
                "Unexpected ${reader.remaining} bytes after reading items data"
            }

            ret
        }
    }

    companion object {
        private const val TAG = "ItemsRepository"
    }
}
