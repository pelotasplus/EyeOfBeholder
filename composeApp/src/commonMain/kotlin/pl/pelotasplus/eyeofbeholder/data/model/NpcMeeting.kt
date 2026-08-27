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
) {
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
    companion object {
        /**
         * The three the dungeon has, by the number a script calls for.
         *
         * Only the first is written out: the other two open with a speech of
         * their own and a choice that is not about joining at all — one of
         * them decides it at random — and neither is a meeting of this shape.
         */
        fun called(npc: NpcId): NpcMeeting? = if (npc.value == 0) IN_THE_CRYPT else null

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
        )

        /** The sheet every one of them is cut from while they speak. */
        const val SHEET = "OUTTAKE.CPS"

        /** And the one their face is cut from once they are in the party. */
        const val FACES = "OUTPORTS.CPS"

        /** The two answers, which belong to no level either. */
        const val YES = "yes"
        const val NO = "no"
    }
}
