package pl.pelotasplus.eyeofbeholder.data.model

/** As far as the corridor goes: it is stopped by hitting something. */
private const val UNTIL_IT_HITS = Projectile.UNTIL_IT_HITS

/**
 * Six squares, which is the other distance the table uses. Nothing is ever
 * shot at from more than three, so this only matters past the party.
 */
private const val DOWN_A_CORRIDOR = 6

/**
 * A spell a monster can loose down a corridor.
 *
 * The twenty numbers a monster's kind may carry as a weapon are these, and a
 * kind names them by number: a dark moon cleric carries `3, 7, 7, 3`, and a
 * beholder `10` through `13`. Fourteen are defined and the rest are nothing.
 *
 * Every value below is data rather than judgement: nothing here follows from
 * anything else, so a number changed by eye is simply a different spell.
 *
 * The one thing worth knowing before reading it: **four of them share one
 * picture and one sound.** [MONSTER_DEATH_SPELL], [MONSTER_DISINTEGRATE],
 * [MONSTER_CAUSE_CRITICAL_WOUNDS] and [MONSTER_FLESH_TO_STONE] all cross the
 * view as the same scatter of blue motes and are cast with the same noise, so
 * a beholder gives no warning of which of the four is coming. Nothing but the
 * message it prints on arrival tells them apart.
 */
enum class MonsterSpell(
    /** The number a monster's kind carries to mean this one. */
    val asWritten: Int,

    /**
     * How it is drawn, or null for the one shape not cut yet.
     *
     * Only [MELFS_ACID_ARROW] is null: it is drawn as one of the thrown
     * weapons rather than as a bolt, and nothing fires it — no monster carries
     * it, and the party do not cast yet.
     */
    val looksLike: ConjuredBolt?,

    /**
     * How many squares it crosses before it gives out. Two thirds of them go
     * until they hit something instead, which down a corridor is the same
     * thing and against an open hall is not.
     */
    val reach: Int,

    /** Whether it goes off where it stops rather than simply stopping. */
    val bursts: Boolean = false,

    /** And in what colours, for the ones that do. */
    val burstsLike: List<Int> = Burst.LIKE_FIRE,

    /**
     * Whether it is drawn down the middle of the view rather than over the
     * quarter of the square it is actually crossing.
     *
     * Nearly all of them are, and it is a flag of its own rather than anything
     * that follows from the rest: a conjured thing is centred and a thrown one
     * is not, so a mage on the left of the party still sends a fireball down
     * the middle of the corridor. It changes nothing about what the spell
     * meets — that is still decided by the quarter it is on.
     *
     * The two that keep their quarter are the two that look like a line rather
     * than a ball, which is the only sense anybody has ever made of it.
     */
    val downTheMiddle: Boolean = true,

    /**
     * What is heard as it is cast, which is the spell's own sound and not the
     * monster's — so two kinds casting the same thing sound alike.
     */
    val heardAs: TrackIndex = TrackIndex(0),

    /**
     * Whether it touches what it crosses on the way, rather than only what is
     * on the square it comes down on.
     *
     * The three that go off are the three that do: a fireball takes whatever
     * it passes through because it is already burning. The rest reach the
     * party and nothing else, so a beholder's ray goes clean over the head of
     * anything standing between — which is worth knowing before wondering why
     * monsters never kill one another with them.
     */
    val hurtsWhatItPasses: Boolean = false,
) {
    MAGIC_MISSILE(0, ConjuredBolt.LIKE_A_MISSILE, UNTIL_IT_HITS, heardAs = TrackIndex(85)),

    MELFS_ACID_ARROW(
        1,
        null,
        UNTIL_IT_HITS,
        heardAs = TrackIndex(96),
        downTheMiddle = false,
    ),

    FIREBALL(
        2,
        ConjuredBolt.LIKE_FIRE,
        UNTIL_IT_HITS,
        bursts = true,
        heardAs = TrackIndex(99),
        hurtsWhatItPasses = true,
    ),

    HOLD_PERSON(3, ConjuredBolt.LIKE_MOTES, UNTIL_IT_HITS, heardAs = TrackIndex(101)),

    LIGHTNING_BOLT(
        4,
        ConjuredBolt.LIKE_LIGHTNING,
        DOWN_A_CORRIDOR,
        bursts = true,
        burstsLike = Burst.LIKE_LIGHTNING,
        heardAs = TrackIndex(71),
        downTheMiddle = false,
        hurtsWhatItPasses = true,
    ),

    ICE_STORM(
        5,
        ConjuredBolt.LIKE_ICE,
        DOWN_A_CORRIDOR,
        bursts = true,
        burstsLike = Burst.LIKE_LIGHTNING,
        heardAs = TrackIndex(89),
        hurtsWhatItPasses = true,
    ),

    HOLD_MONSTER(6, ConjuredBolt.LIKE_MOTES, UNTIL_IT_HITS, heardAs = TrackIndex(101)),

    FLAME_STRIKE(7, ConjuredBolt.LIKE_FIRE, UNTIL_IT_HITS, heardAs = TrackIndex(98)),

    /** The one the dragon breathes, and the heaviest thing in the game. */
    MONSTER_FIREBALL(8, ConjuredBolt.LIKE_FIRE, UNTIL_IT_HITS, heardAs = TrackIndex(98)),

    /** A lesser one, and the hell hounds' whole repertoire. */
    MONSTER_LESSER_FIREBALL(
        9,
        ConjuredBolt.LIKE_FIRE,
        DOWN_A_CORRIDOR,
        heardAs = TrackIndex(98),
    ),

    MONSTER_DEATH_SPELL(10, ConjuredBolt.LIKE_MOTES, DOWN_A_CORRIDOR, heardAs = TrackIndex(101)),

    MONSTER_DISINTEGRATE(11, ConjuredBolt.LIKE_MOTES, DOWN_A_CORRIDOR, heardAs = TrackIndex(101)),

    MONSTER_CAUSE_CRITICAL_WOUNDS(
        12,
        ConjuredBolt.LIKE_MOTES,
        DOWN_A_CORRIDOR,
        heardAs = TrackIndex(101),
    ),

    MONSTER_FLESH_TO_STONE(
        13,
        ConjuredBolt.LIKE_MOTES,
        DOWN_A_CORRIDOR,
        heardAs = TrackIndex(101),
    );

    companion object {
        fun of(asWritten: Int) = entries.firstOrNull { it.asWritten == asWritten }
    }
}
