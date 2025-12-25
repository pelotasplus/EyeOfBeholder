package pl.pelotasplus.eyeofbeholder.data.model.script

import pl.pelotasplus.eyeofbeholder.data.ByteReader
import pl.pelotasplus.eyeofbeholder.data.model.Location

/**
 * Conditional opcodes for scripting system.
 * See https://github.com/scummvm/scummvm/blob/master/engines/kyra/script/script_eob.cpp
 */
sealed interface Conditional {

    fun read(reader: ByteReader): Conditional

    data object Equals : Conditional {                          // 0xFF
        override fun read(reader: ByteReader) = this
    }

    data object NotEquals : Conditional {                       // 0xFE (Differents)
        override fun read(reader: ByteReader) = this
    }

    data object MoreThan : Conditional {                        // 0xFD
        override fun read(reader: ByteReader) = this
    }

    data object MoreEqualsThan : Conditional {                  // 0xFC
        override fun read(reader: ByteReader) = this
    }

    data object LessThan : Conditional {                        // 0xFB
        override fun read(reader: ByteReader) = this
    }

    data object LessEqualsThan : Conditional {                  // 0xFA
        override fun read(reader: ByteReader) = this
    }

    data object And : Conditional {                             // 0xF9
        override fun read(reader: ByteReader) = this
    }

    data object Or : Conditional {                              // 0xF8
        override fun read(reader: ByteReader) = this
    }

    data class GetWallNumber(val location: Location) : Conditional { // 0xF7
        override fun read(reader: ByteReader) = this

        companion object : Conditional {
            override fun read(reader: ByteReader) = GetWallNumber(
                location = Location.read(reader)
            )
        }
    }

    data class ItemCountAtLocation(                              // 0xF5
        val a: Int,
        val b: Int,
        val location: Location
    ) : Conditional {
        override fun read(reader: ByteReader) = this

        companion object : Conditional {
            override fun read(reader: ByteReader) = ItemCountAtLocation(
                a = reader.readU8(),
                b = reader.readU8(),
                location = Location.read(reader)
            )
        }
    }

    sealed class IsMonsterAtLocation : Conditional {             // 0xF3 (TestBlockFlag)
        override fun read(reader: ByteReader): Conditional = read(reader)

        /**
         * Check block flags at a location.
         * First byte is -1 (0xFF), then reads u16 location.
         */
        data class BlockFlags(val location: Location) : IsMonsterAtLocation()

        /**
         * Count specific monsters.
         * Reads monster IDs until -1 (0xFF) terminator.
         */
        data class CountMonsters(val monsterIds: List<Int>) : IsMonsterAtLocation()

        companion object : Conditional {
            override fun read(reader: ByteReader): IsMonsterAtLocation {
                val firstByte = reader.readI8()
                return if (firstByte == -1) {
                    // Check block flags
                    BlockFlags(location = Location.read(reader))
                } else {
                    // Count specific monsters
                    val monsterIds = mutableListOf(firstByte)
                    while (true) {
                        val id = reader.readI8()
                        if (id == -1) break
                        monsterIds.add(id)
                    }
                    reader.readI8() // extra skip
                    CountMonsters(monsterIds = monsterIds)
                }
            }
        }
    }

    data object IsItemAtLocation : Conditional {                // 0xF2
        override fun read(reader: ByteReader) = this
    }

    sealed class IsPartyAtLocation : Conditional {               // 0xF1
        override fun read(reader: ByteReader): Conditional = read(reader)

        /** Count characters with specific items. first byte = -11 (0xF5) */
        data class CountCharactersWithItems(
            val a: Int,
            val b: Int
        ) : IsPartyAtLocation()

        /** Check if party is at current block. first byte != -11 */
        data class CheckCurrentBlock(
            val location: Location
        ) : IsPartyAtLocation()

        companion object : Conditional {
            override fun read(reader: ByteReader): IsPartyAtLocation {
                val firstByte = reader.readI8()
                return if (firstByte == -11) { // 0xF5
                    CountCharactersWithItems(
                        a = reader.readI16LE(),
                        b = reader.readI16LE()
                    )
                } else {
                    CheckCurrentBlock(
                        location = Location.read(reader)
                    )
                }
            }
        }
    }

    data object GetGlobalFlag : Conditional {
        // 			_stack[_stackIndex++] = (_flagTable[17] & (1 << (*pos++))) ? 1 : 0;
        override fun read(reader: ByteReader) = GetGlobalFlag.also {
            reader.readU8()
        }
    }

    data object GetLevelFlag : Conditional {                    // 0xEF
        override fun read(reader: ByteReader) =
            // 			_stack[_stackIndex++] = (_flagTable[_vm->_currentLevel] & (1 << (*pos++))) ? 1 : 0;
            GetLevelFlag.also {
                reader.readU8()
            }
    }

    data object Else : Conditional {                            // 0xEE
        override fun read(reader: ByteReader) = this
    }

    data object GetPartyDirection : Conditional {               // 0xED
        override fun read(reader: ByteReader) = this
    }

    data class GetWallSide(val wallIndex: Int, val location: Location) :
        Conditional {                     // 0xE9
        override fun read(reader: ByteReader) = this

        companion object : Conditional {
            override fun read(reader: ByteReader) =
                GetWallSide(wallIndex = reader.readU8(), location = Location.read(reader))
        }
    }

    sealed class GetPointerItem : Conditional {                  // 0xE7
        override fun read(reader: ByteReader): Conditional = read(reader)

        /** Check if item name contains string. -49 (0xCF) */
        data class NameContains(val searchString: String) : GetPointerItem()

        /** Check if unidentified item name contains string. -48 (0xD0) */
        data class UnidNameContains(val searchString: String) : GetPointerItem()

        /** Get item type. -31 (0xE1) */
        data object ItemType : GetPointerItem()

        /** Get item in hand. -11 (0xF5) */
        data object ItemInHand : GetPointerItem()

        /** Get item value. -10 (0xF6) */
        data object ItemValue : GetPointerItem()

        data class Unknown(val subCmd: Int) : GetPointerItem()

        companion object : Conditional {
            override fun read(reader: ByteReader): GetPointerItem {
                return when (val subCmd = reader.readU8()) {
                    0xCF -> {                                   // -49
                        val length = reader.readU8()
                        NameContains(searchString = reader.readString(length, nullTerminated = false))
                    }
                    0xD0 -> {                                   // -48
                        val length = reader.readU8()
                        UnidNameContains(searchString = reader.readString(length, nullTerminated = false))
                    }
                    0xE1 -> ItemType                            // -31
                    0xF5 -> ItemInHand                          // -11
                    0xF6 -> ItemValue                           // -10
                    else -> Unknown(subCmd)
                }
            }
        }
    }

    data object DialogResult : Conditional {                    // 0xE4
        override fun read(reader: ByteReader) = this
    }

    data object GetTriggerFlag : Conditional {                  // 0xE0
        override fun read(reader: ByteReader) = this
    }

    data object OnSpell : Conditional {                         // 0xDF
        override fun read(reader: ByteReader) = this
    }

    data class HasRace(val race: Int) : Conditional {           // 0xDD
        override fun read(reader: ByteReader) = this

        companion object : Conditional {
            override fun read(reader: ByteReader) = HasRace(race = reader.readU8())
        }
    }

    data class HasClass(val classFlags: Int) : Conditional {    // 0xDC
        override fun read(reader: ByteReader) = this

        companion object : Conditional {
            override fun read(reader: ByteReader) = HasClass(classFlags = reader.readU8())
        }
    }

    data class RollDice(
        val rolls: Int,
        val size: Int,
        val base: Int
    ) : Conditional {                        //

        override fun read(reader: ByteReader): Conditional = this

        // 0xDB
        companion object : Conditional {
            override fun read(reader: ByteReader): RollDice {
                return RollDice(
                    rolls = reader.readU8(),
                    size = reader.readU8(),
                    base = reader.readU8()
                )
            }
        }
    }

    data object IsPartyVisible : Conditional {                  // 0xDA
        override fun read(reader: ByteReader) = this
    }

    sealed class OnBash : Conditional {                          // 0xD7
        override fun read(reader: ByteReader): Conditional = read(reader)

        data object ItemExtraProperties : OnBash()              // -36 (0xDC)
        data object ItemType : OnBash()                         // -31 (0xE1)
        data object LastUsedItem : OnBash()                     // -11 (0xF5)
        data object ItemValue : OnBash()                        // -10 (0xF6)
        data class Unknown(val subCmd: Int) : OnBash()

        companion object : Conditional {
            override fun read(reader: ByteReader): OnBash {
                return when (val subCmd = reader.readU8()) {
                    0xDC -> ItemExtraProperties                 // -36
                    0xE1 -> ItemType                            // -31
                    0xF5 -> LastUsedItem                        // -11
                    0xF6 -> ItemValue                           // -10
                    else -> error("Unknown OnBas sub command $subCmd")
                }
            }
        }
    }

    data class ImmediateShort(val value: Int) : Conditional {   // 0xD2
        override fun read(reader: ByteReader) = this

        companion object : Conditional {
            override fun read(reader: ByteReader) = ImmediateShort(reader.readU16LE())
        }
    }

    data object HasAlignment : Conditional {                    // 0xCE
        override fun read(reader: ByteReader) = this
    }

    data object Condition68 : Conditional {                     // 0x68
        override fun read(reader: ByteReader) = this
    }

    data object Condition02 : Conditional {                     // 0x02
        override fun read(reader: ByteReader) = this
    }

    data object PushTrue : Conditional {                        // 0x01
        override fun read(reader: ByteReader) = this
    }

    data object PushFalse : Conditional {                       // 0x00
        override fun read(reader: ByteReader) = this
    }

    data class Unknown(val opcode: Int) : Conditional {
        override fun read(reader: ByteReader) = this
    }

    companion object {
        private val conditions: Map<Int, Conditional> = mapOf(
            0xFF to Equals,
            0xFE to NotEquals,
            0xFD to MoreThan,
            0xFC to MoreEqualsThan,
            0xFB to LessThan,
            0xFA to LessEqualsThan,
            0xF9 to And,
            0xF8 to Or,
            0xF7 to GetWallNumber.Companion,
            0xF5 to ItemCountAtLocation.Companion,
            0xF3 to IsMonsterAtLocation,
//            0xF2 to IsItemAtLocation,
            0xF1 to IsPartyAtLocation.Companion,
            0xF0 to GetGlobalFlag,
            0xEF to GetLevelFlag,
            0xEE to Else,
            0xED to GetPartyDirection,
            0xE9 to GetWallSide,
            0xE7 to GetPointerItem.Companion,
            0xE4 to DialogResult,
            0xE0 to GetTriggerFlag,
            0xDF to OnSpell,
            0xDD to HasRace.Companion,
            0xDC to HasClass.Companion,
            0xDB to RollDice,
//            0xDA to IsPartyVisible,
            0xD7 to OnBash.Companion,
            0xD2 to ImmediateShort.Companion,
//            0xCE to HasAlignment,
//            0x68 to Condition68,
//            0x02 to Condition02,
//            0x01 to PushTrue,
//            0x00 to PushFalse,
        )

        fun fromOpcode(opcode: Int, reader: ByteReader): Conditional {
            val condition = conditions[opcode]
            return condition?.read(reader) ?: error("Unknown condition opcode: ${opcode.toHexString()}")
        }
    }
}
