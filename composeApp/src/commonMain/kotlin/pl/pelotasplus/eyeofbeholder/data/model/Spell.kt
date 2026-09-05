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
) {
    ARMOUR(1, "armor", TrackIndex(92)),
    BURNING_HANDS(2, "burning hands", TrackIndex(87)),
    DETECT_MAGIC(3, "detect magic", TrackIndex(95)),
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
    BLUR(7, "blur", TrackIndex(75)),
    DETECT_INVISIBILITY(8, "detect invisibility", TrackIndex(95)),
    IMPROVED_IDENTIFY(9, "improved identify", TrackIndex(20)),
    INVISIBILITY(10, "invisibility", TrackIndex(94)),
    MELFS_ACID_ARROW(11, "melf's acid arrow", TrackIndex(96)),
    DISPEL_MAGIC(12, "dispel magic", TrackIndex(97), throwsSparks = true),
    FIREBALL(13, "fireball", TrackIndex(99)),
    HASTE(14, "haste", TrackIndex(100)),
    HOLD_PERSON(15, "Hold Person", TrackIndex(101)),
    INVISIBILITY_TEN_FEET(16, "invisibility 10' radius", TrackIndex(94)),
    LIGHTNING_BOLT(17, "lightning bolt", TrackIndex(71)),
    VAMPIRIC_TOUCH(18, "vampiric touch", TrackIndex(102)),
    FEAR(19, "fear", TrackIndex(103)),
    ICE_STORM(20, "ice storm", TrackIndex(89)),
    IMPROVED_INVISIBILITY(21, "improved invisibility", TrackIndex(8)),
    REMOVE_CURSE(22, "remove curse", TrackIndex(83)),
    CONE_OF_COLD(23, "cone of cold", TrackIndex(118)),
    HOLD_MONSTER(24, "hold monster", TrackIndex(101)),
    WALL_OF_FORCE(25, "wall of force", TrackIndex(74)),
    DISINTEGRATE(26, "disintegrate", TrackIndex(119), throwsSparks = true),
    FLESH_TO_STONE(27, "flesh to stone", TrackIndex(68)),
    STONE_TO_FLESH(28, "stone to flesh", TrackIndex(69)),
    TRUE_SEEING(29, "true seeing", TrackIndex(73)),
    FINGER_OF_DEATH(30, "finger of death"),
    POWER_WORD_STUN(31, "power word stun"),
    BIGBYS_CLENCHED_FIST(32, "bigby's clenched fist"),

    // Thirty-three is the gap the two halves are separated by, and is no
    // spell at all.

    BLESS(34, "bless", TrackIndex(91)),
    CAUSE_LIGHT_WOUNDS(35, "cause light wounds", TrackIndex(107)),
    CURE_LIGHT_WOUNDS(36, "cure light wounds", TrackIndex(104)),
    A_CLERICS_DETECT_MAGIC(37, "detect magic", TrackIndex(95)),
    PROTECTION_FROM_EVIL(38, "protection from evil", TrackIndex(110)),
    AID(39, "aid", TrackIndex(91)),
    FLAME_BLADE(40, "flame blade", TrackIndex(99)),
    A_CLERICS_HOLD_PERSON(41, "hold person", TrackIndex(101)),
    SLOW_POISON(42, "slow poison", TrackIndex(111)),
    CREATE_FOOD(43, "create food", TrackIndex(112)),
    A_CLERICS_DISPEL_MAGIC(44, "dispel magic", TrackIndex(97), throwsSparks = true),
    MAGICAL_VESTMENT(45, "magical vestment", TrackIndex(113)),
    PRAYER(46, "prayer", TrackIndex(91)),
    REMOVE_PARALYSIS(47, "remove paralysis", TrackIndex(114)),
    CAUSE_SERIOUS_WOUNDS(48, "cause serious wounds", TrackIndex(108)),
    CURE_SERIOUS_WOUNDS(49, "cure serious wounds", TrackIndex(105)),
    NEUTRALIZE_POISON(50, "neutralize poison", TrackIndex(115)),
    PROTECTION_FROM_EVIL_TEN_FEET(51, "protection from evil 10' radius", TrackIndex(110)),
    CAUSE_CRITICAL_WOUNDS(52, "cause critical wounds", TrackIndex(109)),
    CURE_CRITICAL_WOUNDS(53, "cure critical wounds", TrackIndex(106)),
    FLAME_STRIKE(54, "flame strike", TrackIndex(98)),
    RAISE_DEAD(55, "raise dead", TrackIndex(117)),
    SLAY_LIVING(56, "slay living", TrackIndex(72)),
    A_CLERICS_TRUE_SEEING(57, "true seeing", TrackIndex(73)),
    HARM(58, "harm", TrackIndex(70)),
    HEAL(59, "heal", TrackIndex(84)),
    RESURRECTION(60, "ressurection"),
    LAY_ON_HANDS(61, "lay on hands", TrackIndex(91)),
    TURN_UNDEAD(62, "turn undead", TrackIndex(103), throwsSparks = true),

    // And the seven nobody learns. They have no names in the game because no
    // player is ever shown one.
    A_MONSTERS_FIREBALL(63, "fireball", TrackIndex(98)),
    MYSTIC_DEFENCE(64, "mystic defense", TrackIndex(91)),
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
