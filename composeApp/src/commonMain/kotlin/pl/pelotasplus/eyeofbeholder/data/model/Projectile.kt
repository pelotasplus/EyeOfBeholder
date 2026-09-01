package pl.pelotasplus.eyeofbeholder.data.model

/**
 * Something in flight down a corridor.
 *
 * One shape covers every way a thing comes to be flying: a trap in the wall
 * spitting a fireball, a monster loosing an arrow, a champion throwing a
 * dagger. What differs between them is who is blamed for it, which is
 * [thrownBy], and none of the flying is written twice.
 *
 * A projectile is an item like any other. It lies on the square it is passing
 * over, so a party looking down the corridor see it on the floor the way they
 * would see anything dropped there, and it stops being in flight by simply
 * being left where it landed.
 */
data class Projectile(
    /**
     * The thing itself, where there is one.
     *
     * An arrow or a thrown dagger is a real item and lands as one. A bolt a
     * trap conjures is nothing you could pick up afterwards, and has none.
     */
    val what: ItemIndex? = null,

    /** The square it is over now. */
    val at: Location,

    /** And whereabouts on that square, which decides who it can hit. */
    val place: SquarePlace,

    val going: Direction,

    /**
     * How many more squares it has in it.
     *
     * Twelve is the game's own number and is a long corridor, not a limit
     * anybody meets by accident. It is here so that a thing loosed down an
     * empty passage stops rather than flying to the edge of the map and back
     * on the next level.
     */
    val squaresLeft: Int = REACH,

    val thrownBy: Thrower,

    /** What it does where it arrives. */
    val harm: Harm = Harm.ofAThrownThing,

    /**
     * Ticks still to run before it is over the next square.
     *
     * A thing in flight is not on the clock the rest of the world is: it
     * crosses a square in its own time, and counting down to the next one is
     * what keeps it from crossing a corridor faster than the eye follows it.
     */
    val untilNextSquare: Int = ACROSS_A_SQUARE,

    /**
     * Whether it is still on the square it was loosed from.
     *
     * What this spares it is being asked what it has hit: a thing is not
     * thrown into the thrower, and something loosed on a square the party are
     * standing on would otherwise strike them where it lay. It is dropped the
     * moment the thing tries to leave, so it says nothing about the wall it
     * is thrown at — that stops it like any other.
     */
    val leaving: Boolean = true,

    /**
     * Which spell this is, where somebody cast one.
     *
     * A trap's bolt is nobody's spell and has none: it is conjured by the
     * level itself, which knows no spells. What this is for is the things a
     * spell is that its picture is not — the sound of the casting, and a name
     * to say it by.
     */
    val spell: MonsterSpell? = null,

    /**
     * Which of the conjured bolts this is drawn as, where it is one of them
     * rather than a thing somebody threw.
     *
     * Ignored entirely for anything with a [what]: a thrown hammer is drawn
     * as a hammer.
     */
    val looksLike: ConjuredBolt = spell?.looksLike ?: ConjuredBolt.LIKE_FIRE,

    /**
     * And in what colours it goes off, for the ones that do.
     *
     * A separate choice from [looksLike] rather than the same one twice: a
     * bolt of lightning and a storm of ice cross the view as different things
     * and burst as the same one.
     */
    val burstsLike: List<Int> = spell?.burstsLike ?: Burst.LIKE_FIRE,

    /**
     * The monsters already rolled against on the square it is over now.
     *
     * A thing in the air asks what it has come to on every turn of the clock,
     * because anything can walk under it between one of its own steps and the
     * next. Asking is a roll, though, and rolling twice at the same monster
     * over the same square would be two chances at it for standing still. So
     * each is asked once a square, and the list is dropped on crossing to the
     * next one.
     */
    val alreadyTried: Set<MonsterSlot> = emptySet(),
) {
    /** Who answers for the damage, which decides whether anybody aims. */
    sealed interface Thrower {
        data class AChampion(val slot: PartySlot) : Thrower

        data class AMonster(val slot: MonsterSlot) : Thrower

        /**
         * The level itself: a trap, loosed by a script.
         *
         * Nobody aims a trap, so nothing rolls to hit — what it strikes, it
         * strikes. It is the only one of the three that can hit the party
         * without a monster being in the room.
         */
        data object TheLevel : Thrower
    }

    /**
     * What a thing in flight costs whoever it reaches.
     *
     * A thrown weapon hurts the one it hits and rolls its own damage, the way
     * it would in a hand. A burst does not: it goes off where it arrives and
     * everybody standing there takes it, each rolled for separately, and there
     * is nothing to duck behind on your own square.
     */
    data class Harm(
        /** Rolled once per victim, or null to let a thrown item roll its own. */
        val dice: DamageDice? = null,

        /**
         * What the roll is multiplied by, which for a spell is the strength of
         * whoever cast it. A trap is nobody, and the game gives it a flat
         * five below the seventh floor and nine from there down.
         */
        val times: Int = 1,

        /** Whether it takes the whole square rather than one of the people on it. */
        val everybody: Boolean = false,
    ) {
        companion object {
            /** A dart or a rock: it hurts what it hits, and rolls as itself. */
            val ofAThrownThing = Harm()

            /**
             * A trap's fireball: a die of six for each level of the thing that
             * cast it, taken by everyone on the square it bursts on.
             */
            fun ofABurst(times: Int) = Harm(
                dice = DamageDice(times = 1, pips = 6, base = 0),
                times = times,
                everybody = true,
            )

            /**
             * A monster's spell, which costs nothing yet: what each of the
             * fourteen does where it arrives is a table of its own and none of
             * it is written. Rolling a die in the meantime would be a number
             * from nowhere, so it rolls none.
             *
             * One that bursts still takes the square whole, because that is
             * what makes it go off at all rather than merely stop.
             */
            fun ofASpell(bursts: Boolean) = Harm(
                dice = DamageDice(times = 0, pips = 0, base = 0),
                everybody = bursts,
            )
        }
    }

    companion object {
        /** How many squares anything loosed will cross before it drops. */
        const val REACH = 12

        /**
         * How long a thing takes to cross one square.
         *
         * The game moves what is in the air every three ticks and walks it
         * through a square in two of those moves, so a square is six.
         */
        const val ACROSS_A_SQUARE = 6

        /**
         * How far a burst carries, which is as far as the corridor goes: it is
         * stopped by hitting something rather than by tiring.
         */
        const val UNTIL_IT_HITS = 255

        /**
         * What a trap counts as, having nobody behind it. The game reads its
         * own depth: five down to the sixth floor, nine below that.
         */
        fun trapStrength(level: Int) = if (level < DEEP) 5 else 9

        private const val DEEP = 7
    }
}
