package pl.pelotasplus.eyeofbeholder.data.repository

import co.touchlab.kermit.Logger
import pl.pelotasplus.eyeofbeholder.data.ByteReader
import pl.pelotasplus.eyeofbeholder.data.model.ItemType
import pl.pelotasplus.eyeofbeholder.data.model.ItemTypes

/**
 * Reads ITEMTYPE.DAT: what each kind of item is, as against ITEM.DAT's
 * which ones there are and where.
 *
 * ## Binary format
 * - numTypes (u16)
 * - that many 16-byte records:
 * ```
 * 0  2  which inventory slots one may go in
 * 2  2  which hand
 * 4  1  what it does to armour class
 * 5  1  which classes may use it
 * 6  1  how many hands it wants
 * 7  3  damage against a small creature: dice, pips, bonus
 * 10 3  the same against a large one
 * 13 1  unknown
 * 14 2  extra properties
 * ```
 */
interface ItemTypesRepository {
    suspend fun loadItemTypes(): Result<ItemTypes>
}

class ItemTypesRepositoryImpl(
    private val resourceRepository: ResourceRepository
) : ItemTypesRepository {

    override suspend fun loadItemTypes(): Result<ItemTypes> = runCatching {
        val reader = ByteReader(resourceRepository.readResource("files/ITEMTYPE.DAT"))

        val types = List(reader.readU16LE()) {
            ItemType(
                invFlags = reader.readU16LE(),
                handFlags = reader.readU16LE(),
                armorClass = reader.readI8(),
                allowedClasses = reader.readI8(),
                requiredHands = reader.readI8(),
                dmgNumDiceS = reader.readI8(),
                dmgNumPipsS = reader.readI8(),
                dmgIncS = reader.readI8(),
                dmgNumDiceL = reader.readI8(),
                dmgNumPipsL = reader.readI8(),
                dmgIncL = reader.readI8(),
                unk1 = reader.readU8(),
                extraProperties = reader.readU16LE(),
            )
        }

        check(reader.remaining == 0) {
            "Unexpected ${reader.remaining} bytes after reading item types"
        }

        Logger.d(TAG) { "ITEMTYPE.DAT holds ${types.size} kinds of item" }

        ItemTypes(types)
    }

    companion object {
        private const val TAG = "ItemTypesRepository"
    }
}
