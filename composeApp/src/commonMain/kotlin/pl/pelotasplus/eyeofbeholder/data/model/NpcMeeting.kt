package pl.pelotasplus.eyeofbeholder.data.model

/**
 * Somebody the party meet, and the words the meeting is made of.
 *
 * A meeting is a set piece: a script hands over, the person says their piece,
 * and the party answer — one of the answers being to let them come along. The
 * speeches are TEXT.DAT's, which is where every other spoken thing comes from;
 * the words on the buttons belong to no level and are English here, the way
 * [DialogueScene.MORE] and [DialogueScene.OK] are.
 *
 * The dungeon's other two set pieces — the portal, and being told the party
 * have died — go through the same opcode and are not these.
 */
data class NpcMeeting(
    val npc: NpcId,

    /** What is heard as they step up. */
    val heardAs: TrackIndex,

    /** Their piece, which ends in asking to come along. */
    val asks: DialogueTextId,

    /** What they say to being let along. */
    val agrees: DialogueTextId,

    /** And to being turned down, where they have anything to say to it. */
    val refused: DialogueTextId?,

    /** Their picture, and where they stand in the view while they speak. */
    val standing: Standing,

    /** Who they are, if they are let along. */
    val joiningAs: Champion,

    /** What they ask first, where coming along is not the first question. */
    val freeing: Freeing? = null,

    /** The bit the meeting writes, and what has to happen for it to be written. */
    val remembers: Remembers? = null,
) {
    /**
     * The question one of them opens with, before joining is ever raised.
     *
     * Insal's whole meeting is the offer to come along. Shorn is shut in a
     * cell and asks only to be let out of it; whether he then goes along is
     * decided by a toss once he is free, and half the time he thanks the party
     * and leaves. Turning him down is answered by nobody — he simply stays
     * where he is.
     */
    data class Freeing(
        /** Their piece, which ends in the question. */
        val asks: DialogueTextId,

        val doIt: String,
        val leaveIt: String,

        /**
         * What is said on the half of the tosses where they go their own way,
         * where a toss is taken at all. Null where saying yes always leads to
         * being asked along.
         */
        val goesInstead: DialogueTextId? = null,

        /** Which way the party are left facing by declining. */
        val leftFacing: Direction? = null,
    )

    /**
     * When a meeting writes the bit that says it happened.
     *
     * The three of them differ, and the difference is the whole of what the
     * bit means: one remembers that somebody came along, one that the party
     * walked away, one that the cell was dealt with either way. A bit set on
     * the wrong branch is a meeting that comes round again, or one that never
     * does.
     */
    data class Remembers(val bit: FlagBit, val on: When) {
        enum class When {
            /** Only where they actually joined. */
            THEY_JOINED,

            /** Only where the first question was answered by walking away. */
            THEY_WERE_LEFT,

            /** However the first question went, so long as it was answered. */
            THEY_WERE_DEALT_WITH,
        }
    }

    /**
     * Where somebody's picture is cut from the sheet the meetings share, and
     * how big they are. Transcribed from the game rather than measured off the
     * sheet, and every one of them is at its left edge.
     *
     * Where they stand falls out of their size: they are centred on the view
     * and stand on the floor of it, so a taller person's head is higher up
     * rather than their feet being lower down.
     */
    data class Standing(val sourceTop: Int, val width: Int, val height: Int) {
        val left: Int get() = MIDDLE - width / 2
        val top: Int get() = FLOOR - height

        /** The same, as the dialogue box's own way of placing a picture. */
        fun inTheView() = DialogueScene.PictureFrame.Standing(
            left = left,
            top = top,
            width = width,
            height = height,
        )

        private companion object {
            const val MIDDLE = 88
            const val FLOOR = 104
        }
    }
    /**
     * [state] with this meeting's bit written, where [became] is what it waits
     * for. A meeting whose bit is set on some other branch is handed back
     * unchanged, so every branch may ask without checking first.
     */
    fun remembering(state: GameState, became: Remembers.When): GameState =
        if (remembers?.on == became) state.globalFlagSet(remembers.bit) else state

    companion object {
        /** The three the dungeon has, by the number a script calls for. */
        fun called(npc: NpcId): NpcMeeting? = when (npc.value) {
            0 -> IN_THE_CRYPT
            1 -> IN_THE_CATACOMBS
            2 -> IN_THE_CELL
            else -> null
        }

        /** The one bit of a champion's flag word that says they are here. */
        private const val IN_THE_PARTY = 0x01

        /**
         * Who the first of them is. Every number here is transcribed from the
         * game's own table of the six it holds, the name along with them.
         *
         * Three hit points of thirty-nine is not a mistake to be tidied up: he
         * is nearly dead when the party find him, and that is the whole of why
         * the offer is worth anything to him.
         */
        private val INSAL = Champion(
            name = "Insal",
            portrait = PortraitId(-1),
            abilities = Abilities(
                strength = Ability(current = 15, max = 15),
                strengthPercentile = Ability(current = 0, max = 0),
                intelligence = Ability(current = 13, max = 13),
                wisdom = Ability(current = 11, max = 11),
                dexterity = Ability(current = 17, max = 17),
                constitution = Ability(current = 16, max = 16),
                charisma = Ability(current = 9, max = 9),
            ),
            hitPoints = HitPoints(current = 3, max = 39),
            armorClass = ArmorClass(10),
            food = Food(8),
            race = Race.HALFLING,
            sex = Sex.MALE,
            characterClass = CharacterClass.THIEF,
            alignment = Alignment.CHAOTIC_NEUTRAL,
            levels = listOf(ClassLevel(level = 6, experience = XpPoints(27354))),
            // he comes with nothing, which is not the same as coming with
            // nowhere to put anything
            carrying = CarrySlot.NOTHING_IN_ANY,
            flags = ChampionFlags(IN_THE_PARTY),
        )

        /**
         * The one waiting on the first floor, whose whole meeting is the
         * question.
         */
        private val IN_THE_CRYPT = NpcMeeting(
            npc = NpcId(0),
            heardAs = TrackIndex(57),
            asks = DialogueTextId(1),
            agrees = DialogueTextId(3),
            refused = DialogueTextId(2),
            standing = Standing(sourceTop = 0, width = 40, height = 57),
            joiningAs = INSAL,
            remembers = Remembers(FlagBit(6), Remembers.When.THEY_JOINED),
        )

        /**
         * Four hit points of seventy-six is not a mistake either: like Insal
         * she is found nearly dead, and unlike him she is a fighter of the
         * ninth level, which is what the party are being offered.
         */
        private val CALANDRA = Champion(
            name = "Calandra",
            portrait = PortraitId(-2),
            abilities = Abilities(
                strength = Ability(current = 18, max = 18),
                strengthPercentile = Ability(current = 36, max = 36),
                intelligence = Ability(current = 13, max = 13),
                wisdom = Ability(current = 8, max = 8),
                dexterity = Ability(current = 15, max = 15),
                constitution = Ability(current = 16, max = 16),
                charisma = Ability(current = 14, max = 14),
            ),
            hitPoints = HitPoints(current = 4, max = 76),
            armorClass = ArmorClass(10),
            food = Food(12),
            race = Race.HUMAN,
            sex = Sex.FEMALE,
            characterClass = CharacterClass.FIGHTER,
            alignment = Alignment.CHAOTIC_GOOD,
            levels = listOf(ClassLevel(level = 9, experience = XpPoints(253749))),
            carrying = CarrySlot.NOTHING_IN_ANY,
            flags = ChampionFlags(IN_THE_PARTY),
        )

        /**
         * The one on the second floor, who is spoken to before she is asked
         * along: walking away from her is remembered and she is not met again,
         * while talking to her leads straight into the other question.
         */
        private val IN_THE_CATACOMBS = NpcMeeting(
            npc = NpcId(1),
            heardAs = TrackIndex(53),
            asks = DialogueTextId(5),
            agrees = DialogueTextId(6),
            refused = DialogueTextId(7),
            standing = Standing(sourceTop = 100, width = 40, height = 79),
            joiningAs = CALANDRA,
            freeing = Freeing(
                asks = DialogueTextId(4),
                doIt = "talk",
                leaveIt = "leave",
            ),
            remembers = Remembers(FlagBit(5), Remembers.When.THEY_WERE_LEFT),
        )

        private val SHORN = Champion(
            name = "Shorn",
            portrait = PortraitId(-3),
            abilities = Abilities(
                strength = Ability(current = 15, max = 15),
                strengthPercentile = Ability(current = 0, max = 0),
                intelligence = Ability(current = 14, max = 14),
                wisdom = Ability(current = 13, max = 13),
                dexterity = Ability(current = 14, max = 14),
                constitution = Ability(current = 13, max = 13),
                charisma = Ability(current = 16, max = 16),
            ),
            hitPoints = HitPoints(current = 40, max = 40),
            armorClass = ArmorClass(10),
            food = Food(100),
            race = Race.DWARF,
            sex = Sex.MALE,
            characterClass = CharacterClass.CLERIC,
            alignment = Alignment.LAWFUL_NEUTRAL,
            levels = listOf(ClassLevel(level = 8, experience = XpPoints(137008))),
            carrying = CarrySlot.NOTHING_IN_ANY,
            flags = ChampionFlags(IN_THE_PARTY),
        )

        private val IN_THE_CELL = NpcMeeting(
            npc = NpcId(2),
            heardAs = TrackIndex(55),
            asks = DialogueTextId(102),
            agrees = DialogueTextId(103),
            refused = DialogueTextId(104),
            standing = Standing(sourceTop = 57, width = 48, height = 43),
            joiningAs = SHORN,
            freeing = Freeing(
                asks = DialogueTextId(8),
                doIt = "release him",
                leaveIt = "leave",
                goesInstead = DialogueTextId(9),
                leftFacing = Direction.NORTH,
            ),
            remembers = Remembers(FlagBit(3), Remembers.When.THEY_WERE_DEALT_WITH),
        )

        private const val A_HAND = 0
        private const val THE_OTHER_HAND = 1

        /**
         * What the one below comes carrying. The numbers are where these sit
         * in the game's own table of items, which is the table the party's
         * belongings are numbered in too.
         */
        private val A_DAGGER = ItemIndex(4)
        private val A_HOLY_SYMBOL = ItemIndex(8)
        private val PLATE_MAIL = ItemIndex(36)

        /**
         * The one the seventh floor brings along without a meeting.
         *
         * He is not an [NpcMeeting] and needs none: the script does all of it
         * — his picture, his piece, the question and the answer — and asks
         * only that he be put in the party at the end. So there is nothing of
         * him here but who he is, and he is the first of the six to come with
         * anything of his own.
         */
        val TANGLOR = Champion(
            name = "Tanglor",
            portrait = PortraitId(-5),
            abilities = Abilities(
                strength = Ability(current = 16, max = 16),
                strengthPercentile = Ability(current = 0, max = 0),
                intelligence = Ability(current = 13, max = 13),
                wisdom = Ability(current = 16, max = 16),
                dexterity = Ability(current = 15, max = 15),
                constitution = Ability(current = 11, max = 11),
                charisma = Ability(current = 12, max = 12),
            ),
            hitPoints = HitPoints(current = 53, max = 53),
            armorClass = ArmorClass(9),
            food = Food(100),
            race = Race.HALF_ELF,
            sex = Sex.MALE,
            characterClass = CharacterClass.FIGHTER_CLERIC,
            alignment = Alignment.NEUTRAL_GOOD,
            // one level and one purse of experience per class he is levelled in
            levels = listOf(
                ClassLevel(level = 7, experience = XpPoints(69570)),
                ClassLevel(level = 7, experience = XpPoints(69570)),
            ),
            carrying = CarrySlot.NOTHING_IN_ANY.toMutableList().also {
                it[A_HAND] = A_DAGGER
                it[THE_OTHER_HAND] = A_HOLY_SYMBOL
                it[CarrySlot.WORN_ARMOUR.index] = PLATE_MAIL
            },
            flags = ChampionFlags(IN_THE_PARTY),
        )

        /** Which of the six he is, which is how the opcode names him. */
        val TANGLOR_IS = NpcId(4)

        /** The sheet every one of them is cut from while they speak. */
        const val SHEET = "OUTTAKE.CPS"

        /** And the one their face is cut from once they are in the party. */
        const val FACES = "OUTPORTS.CPS"

        /** The two answers, which belong to no level either. */
        const val YES = "yes"
        const val NO = "no"

        /** What a party of six are told, and the way out of being asked. */
        const val ONLY_SIX =
            "You may only have six characters in your party.  " +
                "Select the one you wish to drop."
        const val ABORT = "ABORT"
    }
}
