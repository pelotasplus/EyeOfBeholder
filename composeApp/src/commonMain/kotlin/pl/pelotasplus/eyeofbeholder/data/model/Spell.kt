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
 *   the view. Six of the seventy do, and the rest are either shown on the
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
     * do — see [HowLongASpellRuns]. A spell without one is over the moment it
     * is cast, however far what it threw still has to fly.
     *
     * Carrying one is what puts a spell on the party's list of running ones,
     * which is also what makes casting it a second time a refusal rather than
     * a fresh start.
     */
    val lasts: HowLongASpellRuns? = null,

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
     * Whether it shortens the wait after a swing — see
     * [HandRecovering.AFTER_THE_REPORT_HASTENED].
     */
    val hastens: Boolean = false,

    /**
     * What it adds to the rolls of whoever it is on, as against what
     * [hindersStriking] takes off the rolls of whoever strikes at them.
     */
    val helpsStriking: Int = 0,

    /**
     * Hit points it lends for as long as it runs, and takes back at the end.
     *
     * Lent rather than given: they go onto what the champion has without
     * raising what they can hold, so an aided champion stands above their own
     * maximum, and the same number comes off again when it ends — which can
     * put them on the floor if they spent the loan.
     */
    val lends: DamageDice? = null,

    /** Whether it fills every champion's stomach, which one spell does. */
    val feedsThemAll: Boolean = false,

    /**
     * Whether it frames the box of whoever is under it in yellow.
     *
     * Not worked out from what the spell does, because the four that do it
     * have nothing else in common: two help a champion strike, one hurries
     * their hand and one lends them hit points. It is a list, so it is said
     * here rather than guessed at the point of drawing.
     */
    val framesTheBoxYellow: Boolean = false,

    /**
     * And whether it frames the box the other colour, which is the one the
     * defensive spells share.
     *
     * Two frames rather than one scale of them, and a champion can want both
     * at once: the border alternates where they do. Which spell draws which
     * is a list and not a rule — a blur and a shield are the same frame, and
     * a haste is the other.
     */
    val framesTheBoxRed: Boolean = false,

    /**
     * Whether it spreads over the squares in front of the caster the moment
     * it is cast, rather than throwing anything — see [AConeOfCold].
     */
    val spreadsAsACone: Boolean = false,

    /**
     * Whether it burns the one square in front of the party the moment it is
     * cast — see [BurningHands].
     */
    val burnsTheSquareAhead: Boolean = false,

    /**
     * What it holds whoever is under it at, for the spells that armour
     * somebody rather than hurting something — see [ArmourHeldAt].
     */
    val holdsArmourClassAt: ArmourHeldAt? = null,

    /**
     * Whether it stops a magic missile dead — no roll, no harm, and the bolt
     * spent on arriving.
     *
     * One spell does this and one bolt is stopped. A missile is otherwise
     * the one thing in the game with no throw against it and nothing to
     * shrug it off with, which is what makes the pair worth naming.
     */
    val stopsAMagicMissile: Boolean = false,

    /**
     * Whether it is over the moment it stops being worth anything — its
     * wearer having put on better armour, or having been better armoured all
     * along.
     *
     * Only the spell that conjures a suit, because the suit is what the
     * spell is: there is nothing left of it once there is better. A shield
     * is not armour and does not care what is worn under it.
     */
    val endsWhereItWouldNotHelp: Boolean = false,

    /**
     * Whether reading it again over one already running starts it afresh
     * rather than being refused.
     *
     * The refusal is the usual answer and is what keeps a scroll from being
     * spent on something already in force. Two spells are written without
     * the check that produces it, so reading one again simply winds its
     * clock back up — and a party who know that will do it before a door
     * rather than after it.
     */
    val castAgainRatherThanRefused: Boolean = false,

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

    /**
     * Whether it puts whoever is under it out of sight, so that a monster
     * choosing whom to hit mostly passes over them.
     *
     * Mostly and not always — see [GameState.whoAMonsterCanSee]. It is not
     * armour: nothing about a blow that does land is softened by it.
     */
    val hidesThem: Boolean = false,

    /**
     * Whether that hiding is lost the moment its champion does anything.
     *
     * The difference between the two kinds. A plain invisibility has no clock
     * and is spent on the first swing, throw, shot or aimed spell; an improved
     * one is given a clock instead and survives all of it. So the plain one is
     * a way of crossing a room and the improved one is a way of fighting in it.
     */
    val hidingBreaksOnActing: Boolean = false,

    /**
     * Whether, while it runs, the party can see monsters of a kind nobody
     * otherwise can — see [MonsterProperty.unseen].
     *
     * Monsters only. A champion hidden by one of the party's own spells is
     * hidden from what attacks them, and the party never needed a spell to
     * see their own.
     */
    val seesTheHidden: Boolean = false,

    /**
     * Whether it sparks over the box of whoever cast it, as against
     * [sparksOverTheParty]'s six. A spell laid on somebody else sparks over
     * their box instead, which is decided where it is laid.
     */
    val sparksOverTheCaster: Boolean = false,

    /**
     * Whether it tells the caster what they are holding — both hands, and
     * nothing in the pack. Read off a scroll, one hand is the scroll, so what
     * it names is whatever is held in the other.
     */
    val namesWhatIsHeld: Boolean = false,

    /**
     * Whether it turns the creature on the square ahead nearest its caster to
     * stone, for good — see [FleshToStone].
     */
    val turnsTheNearestAheadToStone: Boolean = false,

    /** Whether it frightens off everything on the square ahead — see [Frightening]. */
    val frightensWhatIsAhead: Boolean = false,

    /**
     * What it puts into a hand, where it puts anything — see [HandCharge].
     * Casting it wants the caster's other hand empty, and only one hand of
     * theirs charged at a time.
     */
    val chargesAHand: HandCharge? = null,

    /** What it does to the creature ahead it is laid on or loosed at — see [Wounds]. */
    val wounds: Wounding? = null,
) {
    /**
     * A suit of nothing, laid on whichever champion is pointed at. It holds
     * them at six over their own nimbleness and is worn through by their
     * being hit rather than by any clock, so it is eight points and another
     * for every level of whoever read it — and nothing at all to somebody
     * already better armoured than six.
     */
    ARMOUR(
        1,
        "armor",
        TrackIndex(92),
        settlesOn = SettlesOn.WHOEVER_IS_POINTED_AT,
        lasts = HowLongASpellRuns.WornThroughByHarm(base = 8, perLevel = 1),
        holdsArmourClassAt = ArmourHeldAt.OverTheirNimbleness(points = 6),
        endsWhereItWouldNotHelp = true,
        framesTheBoxRed = true,
    ),
    BURNING_HANDS(2, "burning hands", TrackIndex(87), burnsTheSquareAhead = true),
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
    /**
     * A wall in front of the reader alone, holding them at four for two and
     * a half minutes a level — and stopping a magic missile dead, which is
     * the only thing in the game that stops one at all.
     */
    SHIELD(
        5,
        "shield",
        TrackIndex(92),
        sparksOverTheCaster = true,
        settlesOn = SettlesOn.WHOEVER_CAST_IT,
        lasts = SpellLasts(base = 0, perLevel = 5),
        holdsArmourClassAt = ArmourHeldAt.At(ArmorClass(4)),
        stopsAMagicMissile = true,
        castAgainRatherThanRefused = true,
        framesTheBoxRed = true,
    ),
    SHOCKING_GRASP(
        6,
        "shocking grasp",
        TrackIndex(88),
        sparksOverTheCaster = true,
        lasts = SpellLasts(base = 0, perLevel = 1),
        settlesOn = SettlesOn.WHOEVER_CAST_IT,
        chargesAHand = HandCharge(
            strikesFor = DamageDice(times = 1, pips = 8, base = 0),
            looksLike = ItemIconId(82),
            grownBy = ChargeGrownBy.A_POINT_A_LEVEL,
            goesIntoOneBlow = true,
        ),
    ),
    BLUR(
        7,
        "blur",
        TrackIndex(75),
        sparksOverTheCaster = true,
        lasts = SpellLasts(base = 3, perLevel = 1),
        settlesOn = SettlesOn.WHOEVER_CAST_IT,
        hindersStriking = 2,
        framesTheBoxRed = true,
    ),
    DETECT_INVISIBILITY(
        8,
        "detect invisibility",
        TrackIndex(95),
        sparksOverTheParty = true,
        lasts = SpellLasts(base = 0, perLevel = 5),
        seesTheHidden = true,
    ),
    IMPROVED_IDENTIFY(
        9,
        "improved identify",
        TrackIndex(20),
        sparksOverTheCaster = true,
        namesWhatIsHeld = true,
    ),
    INVISIBILITY(
        10,
        "invisibility",
        TrackIndex(94),
        lasts = HowLongASpellRuns.UntilSomethingBreaksIt,
        settlesOn = SettlesOn.WHOEVER_IS_POINTED_AT,
        hidesThem = true,
        hidingBreaksOnActing = true,
    ),
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
    HASTE(
        14,
        "haste",
        TrackIndex(100),
        sparksOverTheParty = true,
        lasts = SpellLasts(base = 3, perLevel = 1),
        settlesOn = SettlesOn.EVERY_CHAMPION,
        hastens = true,
        framesTheBoxYellow = true,
    ),
    HOLD_PERSON(
        15,
        "Hold Person",
        TrackIndex(101),
        throws = ThrownSpell.thatHolds(MonsterSpell.HOLD_PERSON, AHold.OF_A_PERSON),
    ),
    INVISIBILITY_TEN_FEET(
        16,
        "invisibility 10' radius",
        TrackIndex(94),
        sparksOverTheParty = true,
        lasts = HowLongASpellRuns.UntilSomethingBreaksIt,
        settlesOn = SettlesOn.EVERY_CHAMPION,
        hidesThem = true,
        hidingBreaksOnActing = true,
    ),
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
    /**
     * The third of the hands a spell charges, and the one that takes rather
     * than burns: whatever its blow costs the creature it found is given to
     * whoever struck, over their own maximum and all.
     *
     * A die of six for every two levels of the caster, which is the only one
     * of the three whose *dice* grow rather than its flat part — so it is
     * nothing at all below the second level, and is learnt late enough that
     * nobody who can read it is that new.
     */
    VAMPIRIC_TOUCH(
        18,
        "vampiric touch",
        TrackIndex(102),
        sparksOverTheCaster = true,
        lasts = SpellLasts(base = 0, perLevel = 1),
        settlesOn = SettlesOn.WHOEVER_CAST_IT,
        chargesAHand = HandCharge(
            strikesFor = DamageDice(times = 0, pips = 6, base = 0),
            looksLike = ItemIconId(83),
            grownBy = ChargeGrownBy.A_DIE_EVERY_TWO_LEVELS,
            goesIntoOneBlow = true,
            givesWhatItTakes = true,
        ),
    ),
    FEAR(19, "fear", TrackIndex(103), throwsSparks = true, frightensWhatIsAhead = true),
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
    IMPROVED_INVISIBILITY(
        21,
        "improved invisibility",
        TrackIndex(8),
        lasts = SpellLasts(base = 5, perLevel = 1),
        settlesOn = SettlesOn.WHOEVER_IS_POINTED_AT,
        hidesThem = true,
    ),
    REMOVE_CURSE(22, "remove curse", TrackIndex(83), laidOn = LaidOnAChampion.Uncurses),
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
    FLESH_TO_STONE(
        27,
        "flesh to stone",
        TrackIndex(68),
        throwsSparks = true,
        turnsTheNearestAheadToStone = true,
    ),
    STONE_TO_FLESH(
        28,
        "stone to flesh",
        TrackIndex(69),
        laidOn = LaidOnAChampion.Lifts(Ailment.BEING_STONE),
    ),
    TRUE_SEEING(
        29,
        "true seeing",
        TrackIndex(73),
        sparksOverTheParty = true,
        lasts = SpellLasts(base = 0, perLevel = 1),
        seesTheHidden = true,
    ),
    FINGER_OF_DEATH(30, "finger of death"),
    POWER_WORD_STUN(31, "power word stun"),
    BIGBYS_CLENCHED_FIST(32, "bigby's clenched fist"),

    // Thirty-three is the gap the two halves are separated by, and is no
    // spell at all.

    BLESS(
        34,
        "bless",
        TrackIndex(91),
        sparksOverTheParty = true,
        // The one spell whose time does not grow with its caster.
        lasts = SpellLasts(base = 6, perLevel = 0),
        helpsStriking = 1,
        framesTheBoxYellow = true,
    ),
    CAUSE_LIGHT_WOUNDS(
        35,
        "cause light wounds",
        TrackIndex(107),
        wounds = Wounding.ByTouch(DamageDice(times = 1, pips = 8, base = 0)),
    ),
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
    /**
     * Laid on whichever champion is pointed at, and worth the same two off a
     * monster's roll that a blur is worth — not armour, though its name and
     * its place among the cleric's first-level spells both suggest otherwise.
     *
     * Nothing about it asks what is attacking. Everything in the dungeon is
     * evil enough for it, so it is two off every roll and not two off some.
     */
    PROTECTION_FROM_EVIL(
        38,
        "protection from evil",
        TrackIndex(110),
        lasts = SpellLasts(base = 0, perLevel = 3),
        settlesOn = SettlesOn.WHOEVER_IS_POINTED_AT,
        hindersStriking = 2,
        framesTheBoxRed = true,
    ),
    AID(
        39,
        "aid",
        TrackIndex(91),
        lasts = SpellLasts(base = 1, perLevel = 1),
        settlesOn = SettlesOn.WHOEVER_IS_POINTED_AT,
        helpsStriking = 1,
        lends = DamageDice(times = 1, pips = 8, base = 0),
        framesTheBoxYellow = true,
    ),
    FLAME_BLADE(
        40,
        "flame blade",
        TrackIndex(99),
        lasts = SpellLasts(base = 4, perLevel = 1),
        settlesOn = SettlesOn.WHOEVER_CAST_IT,
        chargesAHand = HandCharge(
            strikesFor = DamageDice(times = 1, pips = 4, base = 4),
            looksLike = ItemIconId(84),
        ),
    ),
    A_CLERICS_HOLD_PERSON(
        41,
        "hold person",
        TrackIndex(101),
        throws = ThrownSpell.thatHolds(MonsterSpell.HOLD_PERSON, AHold.OF_A_PERSON),
    ),
    SLOW_POISON(42, "slow poison", TrackIndex(111)),
    CREATE_FOOD(
        43,
        "create food",
        TrackIndex(112),
        sparksOverTheParty = true,
        feedsThemAll = true,
    ),
    A_CLERICS_DISPEL_MAGIC(44, "dispel magic", TrackIndex(97), throwsSparks = true),
    /**
     * A vestment woven over whoever prayed for it, for two and a half
     * minutes a level — the longest of the three that hold an armour class,
     * and the only one that asks how practised its wearer is.
     *
     * What it holds them at is [ArmourHeldAt.AVestment], which is worth
     * reading: it is worth less to a better cleric, not more.
     */
    MAGICAL_VESTMENT(
        45,
        "magical vestment",
        TrackIndex(113),
        sparksOverTheCaster = true,
        lasts = SpellLasts(base = 0, perLevel = 5),
        settlesOn = SettlesOn.WHOEVER_CAST_IT,
        holdsArmourClassAt = ArmourHeldAt.AVestment,
        framesTheBoxRed = true,
    ),
    PRAYER(
        46,
        "prayer",
        TrackIndex(91),
        sparksOverTheParty = true,
        lasts = SpellLasts(base = 0, perLevel = 1),
        helpsStriking = 1,
        hindersStriking = 1,
        framesTheBoxYellow = true,
    ),
    REMOVE_PARALYSIS(47, "remove paralysis", TrackIndex(114), sparksOverTheParty = true),
    CAUSE_SERIOUS_WOUNDS(
        48,
        "cause serious wounds",
        TrackIndex(108),
        wounds = Wounding.ByTouch(DamageDice(times = 2, pips = 8, base = 1)),
    ),
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
    /**
     * The same two off a monster's roll as [PROTECTION_FROM_EVIL], for the
     * same three half-minutes a level, over all six at once.
     *
     * Six spells rather than one over the party, which is the game's own
     * choice and shows: each can be dispelled on its own, and somebody who
     * joins afterwards is not under it. It passes over anybody there is
     * nothing left of — past raising, or turned to stone — and reaches a
     * champion merely lying at nothing, who is as worth protecting as any.
     */
    PROTECTION_FROM_EVIL_TEN_FEET(
        51,
        "protection from evil 10' radius",
        TrackIndex(110),
        sparksOverTheParty = true,
        lasts = SpellLasts(base = 0, perLevel = 3),
        settlesOn = SettlesOn.EVERY_CHAMPION,
        hindersStriking = 2,
        framesTheBoxRed = true,
    ),
    CAUSE_CRITICAL_WOUNDS(
        52,
        "cause critical wounds",
        TrackIndex(109),
        wounds = Wounding.ByTouch(DamageDice(times = 3, pips = 8, base = 3)),
    ),
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
    SLAY_LIVING(56, "slay living", TrackIndex(72), wounds = Wounding.Slays),
    A_CLERICS_TRUE_SEEING(
        57,
        "true seeing",
        TrackIndex(73),
        sparksOverTheParty = true,
        lasts = SpellLasts(base = 0, perLevel = 1),
        seesTheHidden = true,
    ),
    HARM(58, "harm", TrackIndex(70), wounds = Wounding.AllButAFew),
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

    /**
     * Whether casting this is enough to give away somebody hiding.
     *
     * Roughly the aimed ones, and only roughly, which is why it is a list and
     * not a rule: a shocking grasp or a vampiric touch is left out because
     * what it does is charge a hand, and the blow that hand then strikes
     * gives the caster away by itself.
     */
    val givesTheCasterAway: Boolean get() = this in GIVE_THE_CASTER_AWAY

    companion object {
        /** Which spell that number is, or none for a number nothing uses. */
        fun of(asWritten: Int): Spell? = entries.firstOrNull { it.asWritten == asWritten }

        /**
         * The spells whose casting costs the caster their hiding.
         *
         * A list in the game's data rather than anything about what the
         * spells do, so reasoning will not check it and a name dropped by
         * eye has nothing to catch it: a missing one would only show as a
         * champion who stayed hidden through a fireball, which looks like
         * the spell working. A test naming them is what notices.
         */
        private val GIVE_THE_CASTER_AWAY = setOf(
            BURNING_HANDS,
            MAGIC_MISSILE,
            MELFS_ACID_ARROW,
            FIREBALL,
            HOLD_PERSON,
            LIGHTNING_BOLT,
            FEAR,
            ICE_STORM,
            CONE_OF_COLD,
            HOLD_MONSTER,
            DISINTEGRATE,
            FLESH_TO_STONE,
            CAUSE_LIGHT_WOUNDS,
            A_CLERICS_HOLD_PERSON,
            CAUSE_SERIOUS_WOUNDS,
            CAUSE_CRITICAL_WOUNDS,
            FLAME_STRIKE,
            SLAY_LIVING,
            HARM,
        )
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
