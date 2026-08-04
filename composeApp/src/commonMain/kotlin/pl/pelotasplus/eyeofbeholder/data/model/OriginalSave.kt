package pl.pelotasplus.eyeofbeholder.data.model

import pl.pelotasplus.eyeofbeholder.data.ByteReader

/**
 * A game as an original `EOBDATA*.SAV` describes it.
 *
 * The file holds more than a party: it is the whole world at a moment — where
 * the party stand, which flags have been set, every item in the dungeon, and
 * for each level the walls a script has changed and the monsters left alive.
 * That is the same ground [GameState] covers, which is why reading one is
 * worth doing even though our own saves will be written differently.
 *
 * What is parsed here is the party, the position, the flags and the items. The
 * per-level data is left where it is for now: wiring it in changes what a
 * level is loaded from, which is a change of its own.
 *
 * ## Layout
 * ```
 * 0      20   description, NUL padded
 * 20   2070   six champion records of 345 bytes
 * 2090    2   level
 * 2092    2   sublevel
 * 2094    2   block, which is y * 32 + x
 * 2096    2   facing
 * 2098    2   the item in hand
 * 2100    4   which levels have data saved further down
 * 2104    4   party effect flags
 * 2108    1   padding
 * 2109    1   whether resting is prevented
 * 2110   72   18 flag words: [0] global, [1..17] one per level
 * 2182 8400   600 item records of 14 bytes
 * 10582  ...  one block per level, of what a script has changed on it
 * ```
 */
data class OriginalSave(
    val description: String,
    /** Always six, in slot order; a slot nobody fills is [Champion.NOBODY]. */
    val party: List<Champion>,
    val level: Int,
    val subLevel: Int,
    val standing: PartyState,
    val flags: GameFlags,
    /** What was under the cursor, which belongs to nobody in the party. */
    val inHand: ItemIndex,
    /**
     * Every item in the game as this save has it — the dungeon's and the
     * party's in one table, which is what a champion's [Champion.carrying] and
     * a square's floor both name a slot of.
     *
     * It supersedes ITEM.DAT rather than adding to it: the file says where a
     * game begins, and the six hundred slots here say where this one is. The
     * quick start party's own gear lives past the end of ITEM.DAT, so a party
     * read without this table carries nothing that can be looked up.
     */
    val items: List<Item>,
) {
    val champions: List<Champion> get() = party.filter { it.inTheParty }

    companion object {
        fun read(bytes: UByteArray): OriginalSave {
            val reader = ByteReader(bytes)

            val description = reader.readString(DESCRIPTION_LENGTH)
            val party = List(PARTY_SLOTS) { readChampion(reader) }

            val level = reader.readU16LE()
            val subLevel = reader.readI16LE()
            val block = reader.readU16LE()
            val facing = reader.readU16LE()
            val inHand = ItemIndex(reader.readI16LE())
            reader.readU32LE()                  // which levels have data saved
            reader.readU32LE()                  // party effect flags
            reader.skip(1)
            reader.readU8()                     // whether resting is prevented

            return OriginalSave(
                description = description,
                party = party,
                level = level,
                subLevel = subLevel,
                standing = PartyState(
                    position = Location(block % MAZE_WIDTH, block / MAZE_WIDTH),
                    facing = Direction.entries[facing % Direction.entries.size],
                ),
                flags = readFlags(reader),
                inHand = inHand,
                items = List(ITEM_SLOTS) { Item.read(reader) },
            )
        }

        private fun readChampion(reader: ByteReader): Champion {
            reader.readU8()                                 // id, which is the slot
            val flags = ChampionFlags(reader.readU8())
            val name = reader.readString(NAME_LENGTH)

            val abilities = Abilities(
                strength = reader.ability(),
                strengthPercentile = reader.ability(),
                intelligence = reader.ability(),
                wisdom = reader.ability(),
                dexterity = reader.ability(),
                constitution = reader.ability(),
                charisma = reader.ability(),
            )

            val hitPoints = HitPoints(current = reader.readI16LE(), max = reader.readI16LE())
            val armorClass = ArmorClass(reader.readI8())
            reader.readU8()                                 // which slots are disabled
            val raceAndSex = reader.readU8()
            val characterClass = reader.readU8()
            val alignment = reader.readU8()
            val portrait = PortraitId(reader.readI8())
            val food = Food(reader.readU8())

            val levels = List(CLASSES_PER_CHAMPION) { reader.readU8() }
            val experience = List(CLASSES_PER_CHAMPION) { reader.readU32LE().toLong() and 0xFFFFFFFFL }
            reader.skip(4)

            reader.skip(MAGE_SPELLS + CLERIC_SPELLS + AVAILABLE_SPELL_FLAGS)

            val carrying = List(INVENTORY_SLOTS) { ItemIndex(reader.readI16LE()) }

            reader.skip(TIMERS + EVENTS + EFFECT_REMAINDERS + EFFECT_FLAGS)
            reader.readU8()                                 // damage taken, shown as a splat
            reader.skip(SLOT_STATUS + TRAILING_PADDING)

            return Champion(
                name = name,
                portrait = portrait,
                abilities = abilities,
                hitPoints = hitPoints,
                armorClass = armorClass,
                food = food,
                race = Race.of(raceAndSex),
                sex = Sex.of(raceAndSex),
                characterClass = CharacterClass.of(characterClass),
                alignment = Alignment.of(alignment),
                // a class the champion has no levels in is not a class they have
                levels = levels.zip(experience) { level, earned -> ClassLevel(level, earned) }
                    .filter { it.level > 0 },
                carrying = carrying,
                flags = flags,
            )
        }

        /**
         * The first word is the global flags and the rest are one level each,
         * counting from level 1 — the same shape [GameFlags] keeps them in.
         */
        private fun readFlags(reader: ByteReader): GameFlags {
            val global = reader.readU32LE()
            val perLevel = List(FLAG_WORDS - 1) { reader.readU32LE() }

            return GameFlags(
                global = FlagWord(global),
                // a level nobody has been to has nothing set, and carrying it
                // as an empty word would say we know something about it
                levels = perLevel
                    .mapIndexed { index, word -> (index + 1) to word }
                    .filter { (_, word) -> word != 0 }
                    .associate { (level, word) -> level to FlagWord(word) },
            )
        }

        private fun ByteReader.ability() = Ability(current = readI8(), max = readI8())

        private const val DESCRIPTION_LENGTH = 20
        private const val PARTY_SLOTS = 6
        private const val NAME_LENGTH = 11
        private const val CLASSES_PER_CHAMPION = 3
        private const val INVENTORY_SLOTS = 27
        private const val FLAG_WORDS = 18

        /** Room for every item in the game at once, most of it spare. */
        private const val ITEM_SLOTS = 600
        private const val MAZE_WIDTH = 32

        private const val MAGE_SPELLS = 80
        private const val CLERIC_SPELLS = 80
        private const val AVAILABLE_SPELL_FLAGS = 4
        private const val TIMERS = 40
        private const val EVENTS = 10
        private const val EFFECT_REMAINDERS = 4
        private const val EFFECT_FLAGS = 4
        private const val SLOT_STATUS = 5
        private const val TRAILING_PADDING = 6
    }
}
