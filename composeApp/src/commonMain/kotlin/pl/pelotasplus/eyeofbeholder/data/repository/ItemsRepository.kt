package pl.pelotasplus.eyeofbeholder.data.repository

import co.touchlab.kermit.Logger
import pl.pelotasplus.eyeofbeholder.data.ByteReader
import pl.pelotasplus.eyeofbeholder.data.model.Item
import pl.pelotasplus.eyeofbeholder.data.model.Location

interface ItemsRepository {
    suspend fun loadItems(): Result<List<Item>>
}

class ItemsRepositoryImpl(
    private val resourceRepository: ResourceRepository
) : ItemsRepository {

    override suspend fun loadItems(): Result<List<Item>> {
        return runCatching {
            val itemSmall = resourceRepository.decompressResource("files/ITEMS1.CPS")
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
                            icon = reader.readI8(), // icon index (→ ITEMICN + shape map)
                            type = reader.readI8(), // item type index (→ itemtype.dat)
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
