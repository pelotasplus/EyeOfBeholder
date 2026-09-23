package pl.pelotasplus.eyeofbeholder.data.model

/**
 * Every spell there is, which is one list rather than two.
 *
 * A mage's and a cleric's are numbered in a single run — the mage's first, the
 * cleric's after a gap of one where nothing lives — so the number alone says
 * which spell it is and nothing has to carry the caster's calling beside it.
 * A scroll writes that number on itself and a wand is looked up to one.
 *
 * Five names appear twice, once in each half, and they are not the same spell:
 * a cleric's hold person is [A_CLERICS_HOLD_PERSON] and answers to a different
 * number from the mage's. The eleventh floor's carvings ask for either
 * [DISPEL_MAGIC] or [A_CLERICS_DISPEL_MAGIC], which is what makes that puzzle
 * open to a party with only one kind of caster in it.
 *
 * The last seven are nobody's to learn: they are what monsters throw, and are
 * here because they are cast by the same act and heard by the same table. What
 * one of those *does* is [MonsterSpell], which counts its own kinds separately
 * — that is a list of things that fly, and this is a list of spells.
 *
 * @property asWritten the number a scroll, a wand or a script names it by
 * @property calledIt what a player sees it called, capitals and all
 * @property heardAs what is heard when it is cast, or nothing for the silent
 * @property throwsSparks whether casting it scatters [SparksInTheRoom] across
 *   the view. Four of the seventy do, and the rest are either shown on the
 *   portrait of whoever cast them or not shown at all.
 * @property sparksOverTheParty whether casting it throws [SparksOverTheParty]
 *   over every portrait, which is what the spells laid on the whole party do.
 * @property throws what it sends down the corridor, for the few that send
 *   anything. Null is not "does nothing" — it is "does nothing that flies",
 *   which covers mending a champion as well as a spell nobody has written yet.
 */
enum class Spell(
    val asWritten: Int,
    val calledIt: String,
    val heardAs: TrackIndex? = null,
    val throwsSparks: Boolean = false,
    val throws: ThrownSpell? = null,
    val sparksOverTheParty: Boolean = false,

    /**
     * What it does to one of the party, for the ones cast on a person rather
     * than down a corridor — mending them, lifting something off them, or
     * bringing them back.
     *
     * Carrying one of these is what makes a spell ask which champion before it
     * does anything at all.
     */
    val laidOn: LaidOnAChampion? = null,

    /**
     * How long it goes on for after the words are said, for the spells that
     * do — see [SpellLasts]. A spell without one is over the moment it is
     * cast, however far what it threw still has to fly.
     *
     * Carrying one is what puts a spell on the party's list of running ones,
     * which is also what makes casting it a second time a refusal rather than
     * a fresh start.
     */
    val lasts: SpellLasts? = null,

    /**
     * Whom a spell that [lasts] settles on. Meaningless without one.
     *
     * A spell is over the party or on one champion, and the two are not the
     * same: a detect magic belongs to nobody and a blur moves nothing about
     * the other five.
     */
    val settlesOn: SettlesOn = SettlesOn.THE_PARTY,

    /**
     * How much it takes off a monster's roll to hit whoever it is on.
     *
     * Off the roll and not onto their armour, which matters at the top of the
     * die: a natural twenty lands on a blurred champion like anybody else.
     */
    val hindersStriking: Int = 0,

    /**
     * Whether it spreads over the squares in front of the caster the moment
     * it is cast, rather than throwing anything — see [AConeOfCold].
     */
    val spreadsAsACone: Boolean = false,

    /**
     * Whether it shuts off the square ahead of the party for a while — see
     * [WallsOfForce]. Refused where that square will not hold one.
     */
    val shutsOffTheSquareAhead: Boolean = false,

    /**
     * Whether it unmakes the square ahead — whatever stands on it, and its
     * walls either way. See [Disintegration].
     */
    val unmakesTheSquareAhead: Boolean = false,
) {
    ARMOUR(1, "armor", TrackIndex(92)),
    BURNING_HANDS(2, "burning hands", TrackIndex(87)),
    DETECT_MAGIC(
        3,
        "detect magic",
        TrackIndex(95),
        sparksOverTheParty = true,
        lasts = SpellLasts(base = 0, perLevel = 2),
    ),
    MAGIC_MISSILE(
        4,
        "magic missile",
        TrackIndex(85),
        throws = ThrownSpell(
            flies = MonsterSpell.MAGIC_MISSILE,
            dealing = DamageDice(times = 1, pips = 4, base = 1),
            counted = CountedBy.EVERY_SECOND_LEVEL,
            hurting = setOf(HarmKind.MAGIC),
        ),
    ),
    SHIELD(5, "shield", TrackIndex(92)),
    SHOCKING_GRASP(6, "shocking grasp", TrackIndex(88)),
    BLUR(
        7,
        "blur",
        TrackIndex(75),
        lasts = SpellLasts(base = 3, perLevel = 1),
        settlesOn = SettlesOn.WHOEVER_CAST_IT,
        hindersStriking = 2,
    ),
    DETECT_INVISIBILITY(8, "detect invisibility", TrackIndex(95), sparksOverTheParty = true),
    IMPROVED_IDENTIFY(9, "improved identify", TrackIndex(20)),
    INVISIBILITY(10, "invisibility", TrackIndex(94)),
    MELFS_ACID_ARROW(
        11,
        "melf's acid arrow",
        TrackIndex(96),
        throws = ThrownSpell(
            flies = MonsterSpell.MELFS_ACID_ARROW,
            dealing = DamageDice(times = 2, pips = 4, base = 0),
            counted = CountedBy.EVERY_THIRD_LEVEL,
            hurting = setOf(HarmKind.MAGIC, HarmKind.ACID),
            // Nothing is thrown against it. Acid does not care how nimble the
            // thing it lands on was.
            thrownOff = null,
        ),
    ),
    DISPEL_MAGIC(12, "dispel magic", TrackIndex(97), throwsSparks = true),
    FIREBALL(
        13,
        "fireball",
        TrackIndex(99),
        throws = ThrownSpell(
            flies = MonsterSpell.FIREBALL,
            dealing = DamageDice(times = 1, pips = 6, base = 0),
            counted = CountedBy.EVERY_LEVEL,
            hurting = setOf(HarmKind.MAGIC, HarmKind.FIRE),
            takesEitherSide = true,
            takesTheWholeSquare = true,
            thrownOff = SavingThrow.A_SPELL,
        ),
    ),
    HASTE(14, "haste", TrackIndex(100), sparksOverTheParty = true),
    HOLD_PERSON(
        15,
        "Hold Person",
        TrackIndex(101),
        throws = ThrownSpell.thatHolds(MonsterSpell.HOLD_PERSON, AHold.OF_A_PERSON),
    ),
    INVISIBILITY_TEN_FEET(16, "invisibility 10' radius", TrackIndex(94), sparksOverTheParty = true),
    LIGHTNING_BOLT(
        17,
        "lightning bolt",
        TrackIndex(71),
        throws = ThrownSpell(
            flies = MonsterSpell.LIGHTNING_BOLT,
            dealing = DamageDice(times = 1, pips = 6, base = 0),
            counted = CountedBy.EVERY_LEVEL,
            hurting = setOf(HarmKind.MAGIC, HarmKind.ELECTRICITY),
            takesEitherSide = true,
            carriesOn = true,
            thrownOff = SavingThrow.A_SPELL,
        ),
    ),
    VAMPIRIC_TOUCH(18, "vampiric touch", TrackIndex(102)),
    FEAR(19, "fear", TrackIndex(103)),
    ICE_STORM(
        20,
        "ice storm",
        TrackIndex(89),
        throws = ThrownSpell(
            flies = MonsterSpell.ICE_STORM,
            dealing = DamageDice(times = 1, pips = 6, base = 0),
            counted = CountedBy.EVERY_LEVEL,
            hurting = setOf(HarmKind.MAGIC, HarmKind.COLD),
            takesEitherSide = true,
            takesTheWholeSquare = true,
            spreads = true,
            thrownOff = SavingThrow.A_SPELL,
        ),
    ),
    IMPROVED_INVISIBILITY(21, "improved invisibility", TrackIndex(8)),
    REMOVE_CURSE(22, "remove curse", TrackIndex(83)),
    CONE_OF_COLD(23, "cone of cold", TrackIndex(118), spreadsAsACone = true),
    HOLD_MONSTER(
        24,
        "hold monster",
        TrackIndex(101),
        throws = ThrownSpell.thatHolds(MonsterSpell.HOLD_MONSTER, AHold.OF_A_MONSTER),
    ),
    WALL_OF_FORCE(25, "wall of force", TrackIndex(74), shutsOffTheSquareAhead = true),
    DISINTEGRATE(
        26,
        "disintegrate",
        TrackIndex(119),
        throwsSparks = true,
        unmakesTheSquareAhead = true,
    ),
    FLESH_TO_STONE(27, "flesh to stone", TrackIndex(68)),
    STONE_TO_FLESH(
        28,
        "stone to flesh",
        TrackIndex(69),
        laidOn = LaidOnAChampion.Lifts(Ailment.BEING_STONE),
    ),
    TRUE_SEEING(29, "true seeing", TrackIndex(73), sparksOverTheParty = true),
    FINGER_OF_DEATH(30, "finger of death"),
    POWER_WORD_STUN(31, "power word stun"),
    BIGBYS_CLENCHED_FIST(32, "bigby's clenched fist"),

    // Thirty-three is the gap the two halves are separated by, and is no
    // spell at all.

    BLESS(34, "bless", TrackIndex(91), sparksOverTheParty = true),
    CAUSE_LIGHT_WOUNDS(35, "cause light wounds", TrackIndex(107)),
    CURE_LIGHT_WOUNDS(
        36,
        "cure light wounds",
        TrackIndex(104),
        laidOn = LaidOnAChampion.Mends(Mending.Rolled(DamageDice(times = 1, pips = 8, base = 0))),
    ),
    A_CLERICS_DETECT_MAGIC(
        37,
        "detect magic",
        TrackIndex(95),
        sparksOverTheParty = true,
        lasts = SpellLasts(base = 0, perLevel = 2),
    ),
    PROTECTION_FROM_EVIL(38, "protection from evil", TrackIndex(110)),
    AID(39, "aid", TrackIndex(91)),
    FLAME_BLADE(40, "flame blade", TrackIndex(99)),
    A_CLERICS_HOLD_PERSON(
        41,
        "hold person",
        TrackIndex(101),
        throws = ThrownSpell.thatHolds(MonsterSpell.HOLD_PERSON, AHold.OF_A_PERSON),
    ),
    SLOW_POISON(42, "slow poison", TrackIndex(111)),
    CREATE_FOOD(43, "create food", TrackIndex(112), sparksOverTheParty = true),
    A_CLERICS_DISPEL_MAGIC(44, "dispel magic", TrackIndex(97), throwsSparks = true),
    MAGICAL_VESTMENT(45, "magical vestment", TrackIndex(113)),
    PRAYER(46, "prayer", TrackIndex(91), sparksOverTheParty = true),
    REMOVE_PARALYSIS(47, "remove paralysis", TrackIndex(114), sparksOverTheParty = true),
    CAUSE_SERIOUS_WOUNDS(48, "cause serious wounds", TrackIndex(108)),
    CURE_SERIOUS_WOUNDS(
        49,
        "cure serious wounds",
        TrackIndex(105),
        laidOn = LaidOnAChampion.Mends(Mending.Rolled(DamageDice(times = 2, pips = 8, base = 1))),
    ),
    NEUTRALIZE_POISON(
        50,
        "neutralize poison",
        TrackIndex(115),
        laidOn = LaidOnAChampion.Lifts(Ailment.POISON),
    ),
    PROTECTION_FROM_EVIL_TEN_FEET(
        51,
        "protection from evil 10' radius",
        TrackIndex(110),
        sparksOverTheParty = true,
    ),
    CAUSE_CRITICAL_WOUNDS(52, "cause critical wounds", TrackIndex(109)),
    CURE_CRITICAL_WOUNDS(
        53,
        "cure critical wounds",
        TrackIndex(106),
        laidOn = LaidOnAChampion.Mends(Mending.Rolled(DamageDice(times = 3, pips = 8, base = 3))),
    ),
    FLAME_STRIKE(
        54,
        "flame strike",
        TrackIndex(98),
        throws = ThrownSpell(
            flies = MonsterSpell.FLAME_STRIKE,
            dealing = DamageDice(times = 6, pips = 8, base = 0),
            counted = CountedBy.ONCE_HOWEVER_PRACTISED,
            hurting = setOf(HarmKind.MAGIC, HarmKind.FIRE),
            takesTheWholeSquare = true,
            thrownOff = SavingThrow.A_SPELL,
        ),
    ),
    RAISE_DEAD(55, "raise dead", TrackIndex(117), laidOn = LaidOnAChampion.Raises),
    SLAY_LIVING(56, "slay living", TrackIndex(72)),
    A_CLERICS_TRUE_SEEING(57, "true seeing", TrackIndex(73), sparksOverTheParty = true),
    HARM(58, "harm", TrackIndex(70)),
    HEAL(59, "heal", TrackIndex(84), laidOn = LaidOnAChampion.Mends(Mending.ToTheBrim)),
    RESURRECTION(60, "ressurection"),
    LAY_ON_HANDS(
        61,
        "lay on hands",
        TrackIndex(91),
        laidOn = LaidOnAChampion.Mends(Mending.TwiceWhatTheCasterHasLearnt),
    ),
    TURN_UNDEAD(62, "turn undead", TrackIndex(103), throwsSparks = true),

    // And the seven nobody learns. They have no names in the game because no
    // player is ever shown one.
    A_MONSTERS_FIREBALL(63, "fireball", TrackIndex(98)),
    MYSTIC_DEFENCE(
        64,
        "mystic defense",
        TrackIndex(91),
        sparksOverTheParty = true,
        lasts = SpellLasts(base = 1, perLevel = 0),
    ),
    A_LESSER_FIREBALL(65, "fireball", TrackIndex(98)),
    A_DEATH_SPELL(66, "death spell", TrackIndex(101)),
    A_DISINTEGRATION(67, "disintegrate", TrackIndex(101)),
    WOUNDS_CAUSED_AT_A_DISTANCE(68, "cause critical wounds", TrackIndex(101)),
    STONE_AT_A_DISTANCE(69, "flesh to stone", TrackIndex(101));

    companion object {
        /** Which spell that number is, or none for a number nothing uses. */
        fun of(asWritten: Int): Spell? = entries.firstOrNull { it.asWritten == asWritten }
    }
}

/**
 * The eight kinds of wand, which say which of eight they are rather than what
 * they do.
 *
 * A wand's own number is what is written on it; what it casts is looked up.
 * [SPENT] is one with nothing left in it, which says so and does nothing.
 */
enum class Wand(val asWritten: Int, val casts: Spell?) {
    SPENT(0, null),
    OF_LIGHTNING(1, Spell.LIGHTNING_BOLT),
    OF_COLD(2, Spell.CONE_OF_COLD),
    OF_CURING(3, Spell.CURE_SERIOUS_WOUNDS),
    OF_FIRE(4, Spell.FIREBALL),
    OF_DEFENCE(5, Spell.MYSTIC_DEFENCE),
    OF_MISSILES(6, Spell.MAGIC_MISSILE),

    /** The one the eleventh floor's carvings want. */
    OF_DISPELLING(7, Spell.DISPEL_MAGIC);

    companion object {
        /** Which wand that number is, or none for a number no wand carries. */
        fun of(asWritten: Int): Wand? = entries.firstOrNull { it.asWritten == asWritten }
    }
}
