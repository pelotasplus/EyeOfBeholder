package pl.pelotasplus.eyeofbeholder.data.model.script

import pl.pelotasplus.eyeofbeholder.data.ByteReader
import pl.pelotasplus.eyeofbeholder.data.model.CharacterClass
import pl.pelotasplus.eyeofbeholder.data.model.FlagBit
import pl.pelotasplus.eyeofbeholder.data.model.Race
import pl.pelotasplus.eyeofbeholder.data.model.Location

/**
 * Conditional expression opcodes used by [Eval] for branching logic.
 *
 * The scripting engine uses a stack-based expression evaluator. Each [Eval]
 * token contains a sequence of Conditional opcodes that push values onto
 * the stack, then comparison/logic operators consume stack values and push
 * results. If the final stack value is true, execution continues; otherwise
 * it jumps to the [Eval.goto] offset.
 *
 * ## Expression evaluation model
 * - Value producers push onto the stack: GetWallNumber, GetLevelFlag, ImmediateShort, etc.
 * - Comparisons pop 2 values, push boolean: Equals, NotEquals, MoreThan, LessThan, etc.
 * - Logical operators combine booleans: And, Or
 * - Else (0xEE) terminates the condition expression
 *
 * ## Example: "if wall at (5,3) == 1, goto offset 0x42"
 * ```
 * Eval(tokens=[GetWallNumber(5,3), ImmediateShort(1), Equals], goto=0x42)
 * ```
 *
 * Reference: https://github.com/scummvm/scummvm/blob/master/engines/kyra/script/script_eob.cpp
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

    data object TestCharacters : Conditional {                        // 0xE8
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

    /** Whether [bit] of the flag word that outlives the level is set. */
    data class GetGlobalFlag(val bit: FlagBit) : Conditional {  // 0xF0
        override fun read(reader: ByteReader) = this

        companion object : Conditional {
            override fun read(reader: ByteReader) = GetGlobalFlag(FlagBit(reader.readU8()))
        }
    }

    /** Whether [bit] of this level's own flag word is set. */
    data class GetLevelFlag(val bit: FlagBit) : Conditional {   // 0xEF
        override fun read(reader: ByteReader) = this

        companion object : Conditional {
            override fun read(reader: ByteReader) = GetLevelFlag(FlagBit(reader.readU8()))
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

    /** Whether the party hold anybody of this race. */
    data class HasRace(val race: Race?) : Conditional {         // 0xDD
        override fun read(reader: ByteReader) = this

        companion object : Conditional {
            override fun read(reader: ByteReader) =
                HasRace(race = Race.entries.getOrNull(reader.readU8()))
        }
    }

    /**
     * Whether the party hold anybody of any of these classes. The file names
     * them as one number of bits, which is turned into the classes it means
     * here rather than carried on as a number.
     */
    data class HasClass(val classes: Set<CharacterClass>) : Conditional {   // 0xDC
        override fun read(reader: ByteReader) = this

        companion object : Conditional {
            override fun read(reader: ByteReader) =
                HasClass(classes = CharacterClass.setOf(reader.readU8()))
        }
    }

    /**
     * A throw of [rolls] dice of [size] sides each, plus [base] — which the
     * script then tests like any other number.
     *
     * All three are signed: the levels only ever add to a throw, but the
     * record they are read from does not say so, and a base of -1 read
     * unsigned would add 255.
     */
    data class RollDice(
        val rolls: Int,
        val size: Int,
        val base: Int
    ) : Conditional {                        // 0xDB

        override fun read(reader: ByteReader): Conditional = this

        companion object : Conditional {
            override fun read(reader: ByteReader): RollDice {
                return RollDice(
                    rolls = reader.readI8(),
                    size = reader.readI8(),
                    base = reader.readI8()
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
            0xF0 to GetGlobalFlag.Companion,
            0xEF to GetLevelFlag.Companion,
            0xEE to Else,
            0xED to GetPartyDirection,
            0xE9 to GetWallSide,
            0xE8 to TestCharacters,
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
