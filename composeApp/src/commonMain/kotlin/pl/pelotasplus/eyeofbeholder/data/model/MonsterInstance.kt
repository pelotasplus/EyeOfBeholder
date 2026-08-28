package pl.pelotasplus.eyeofbeholder.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import pl.pelotasplus.eyeofbeholder.data.model.script.CreateMonster

/**
 * One live monster placed on a level, parsed from the INF file's Block B
 * (up to 30 records of 14 bytes each; records starting with 0xFF are empty).
 *
 * @property index Monster slot index from the file (first byte of the record)
 * @property unit Which group the monster is updated with. NOT a sublevel — see
 *   [subLevel] for that.
 * @property subLevel Which sublevel this monster belongs to, and so which
 *   sublevel's tables say what it is. The file does not record it: a monster
 *   takes the sublevel that was being loaded when it was placed, which for the
 *   ones the file lists is whichever the party entered by. The same record is
 *   therefore a different creature in each — level 3's type 0 is a gelatinous
 *   cube read by its first sublevel and a worker ant by its second.
 * @property block Packed maze square: x = block and 0x1F, y = block shr 5
 * @property direction Which way the monster faces
 * @property type Index into [SubLevel.monsters] (the species' stats)
 * @property gfxIndex Index into [SubLevel.monsterGfx] (which sprite sheet)
 * @property mode Behavior mode at spawn
 * @property pause Movement pause counter
 * @property weapon Item type id of the held weapon (0 = none)
 * @property pocketItem Item type id carried as loot (0 = none)
 * @property hitPoints What it can take before it dies, rolled from its kind's
 *   dice when it is placed — so two of the same creature are not equally hard
 *   to kill. Zero for one nobody has rolled for; see [rolledFor].
 */
@Serializable
data class MonsterInstance(
    val index: Int,
    val unit: Int,
    val block: Int,
    @SerialName("pos")
    @Serializable(with = SquarePlaceAsTheGameWritesIt::class)
    val place: SquarePlace,
    val direction: Direction,
    val type: MonsterTypeId,
    val gfxIndex: Int,
    val mode: Int,
    val pause: Int,
    val weapon: Int,
    val pocketItem: Int,
    val subLevel: Int = 0,
    val hitPoints: HitPoints = UNROLLED,
    /** Hit this instant, and so drawn as a silhouette until the moment passes. */
    val struck: Boolean = false,
    /**
     * Whether it has been roused. The pair on level 5 stand talking until
     * somebody hits one of them; a monster nobody has provoked is scenery.
     */
    val provoked: Boolean = false,
    /**
     * How far through its own swing it is, if it is swinging: the arm goes
     * back and then comes down, and the sprites for both are cut already.
     */
    val striking: MonsterPose? = null,
    /**
     * Whether this turn is one it swings on. A monster in reach of the party
     * strikes every other turn rather than every one — a bit flips each time
     * its turn comes round, and nothing happens on the turns the bit lands
     * clear, which halves how hard a fight comes at the party and is most of
     * what makes one survivable.
     */
    val readyToStrike: Boolean = false,
    /** Where it is in its looking-about, for the two modes that stray. */
    val straying: Straying = Straying.TURNED_AWAY,
) {
    /** What it does with a turn nobody has provoked it into taking. */
    val whatItDoes: MonsterMode get() = MonsterMode.of(mode)
    val x: Int get() = block and 0x1F
    val y: Int get() = block shr 5

    /**
     * Which of the four groups takes its turn with this one.
     *
     * Monsters do not all move at once: the four groups are spread across a
     * turn's length, so two of different groups act a beat apart rather than as
     * one body. The group is the [unit] a level puts a monster in, so a pack
     * placed as one unit moves together, corner for corner, and two units on
     * the same square keep the stagger their level chose.
     */
    val turnGroup: Int get() = unit and 3

    /** Which of its sheet's color schemes this monster is painted in. */
    val colors: MonsterColors get() = MonsterColors.forSlot(index)

    /**
     * Whether anybody has said how much this one can take. One that has not
     * been rolled for cannot be hurt at all, rather than dying to the first
     * blow because its hit points happen to read as none.
     */
    val couldBeHurt: Boolean get() = hitPoints != UNROLLED

    /**
     * The same monster with what it can take rolled from its kind's dice.
     *
     * Every monster is rolled for as it is placed, whether the level's file
     * listed it or a script conjured it, so a pair of the same creature take
     * different numbers of blows.
     */
    fun rolledFor(kind: MonsterProperty, dice: Dice): MonsterInstance {
        val rolled = dice.roll(kind.hpDcTimes, kind.hpDcPips, kind.hpDcBase)
        return copy(hitPoints = HitPoints(current = rolled, max = rolled))
    }

    /**
     * The same monster [by] hit points worse off, showing it, and no longer
     * willing to be talked to.
     */
    fun hurt(by: Int) = copy(
        hitPoints = hitPoints.copy(current = hitPoints.current - by),
        struck = true,
        provoked = true,
    )

    /**
     * Whether it is waiting to see what the party do rather than doing
     * anything: the mode a level gives something that talks before it fights.
     */
    val standingBy: Boolean get() = whatItDoes == MonsterMode.WAITING_TO_SEE

    /** The same monster set after the party, whatever it was doing before. */
    fun takingUpTheHunt() = copy(mode = MonsterMode.HUNTING.asWritten, provoked = true)

    /**
     * Whether its arm gets to the party from where it stands: it must face
     * their square, and — if it is one of the small ones that go four to a
     * square — stand on the half of its own square that reaches. Anything
     * bigger reaches from wherever on the square it is, which is what lets
     * both of a pair fight from the two corners they share it on.
     */
    fun canReach(party: PartyState, size: MonsterSize): Boolean =
        facesTheSquareOf(party) &&
            (size.reachesFromAnywhere || !place.onTheFloor ||
                WhoTheMonsterReaches.armIsLongEnough(direction, place))

    /**
     * Whether the party's square is the one it is looking at, whatever corner
     * of its own it happens to be standing on.
     *
     * Half of reaching them, and the half that says a monster has arrived: one
     * that faces them and cannot touch them has only to shift its feet, where
     * one facing elsewhere has further to go.
     */
    fun facesTheSquareOf(party: PartyState): Boolean {
        val (dx, dy) = direction.transformCoordinates(0, -1)
        return x + dx == party.position.x && y + dy == party.position.y
    }

    /**
     * Whether the party are near enough, and far enough in front, to be worth
     * setting off after.
     *
     * Three squares is as far as anything sees. Within that, the party are
     * still missed if they are behind it and not right beside it, which is
     * what lets a party creep past something's back — and what makes walking
     * round one worth doing.
     */
    fun notices(party: PartyState): Boolean {
        val away = Location(x, y).squaresFrom(party.position)
        if (away >= OUT_OF_SIGHT) return false
        if (away < CLOSE_ENOUGH_TO_FEEL) return true

        val over = Bearing.of(direction).clockwiseTo(
            Bearing.from(Location(x, y), party.position) ?: return true,
        )
        return over !in BEHIND_IT
    }

    /**
     * Which way it would have to turn to face the party, or null if they are
     * not on one of the four squares around it — a monster turns towards
     * something it can reach next turn, and nothing else.
     */
    fun facingThe(party: PartyState): Direction? = Direction.entries.firstOrNull { way ->
        val (dx, dy) = way.transformCoordinates(0, -1)
        x + dx == party.position.x && y + dy == party.position.y
    }

    /** The same monster with its turn come round, ready or not. */
    fun turnCameRound() = copy(readyToStrike = !readyToStrike)

    /** The next frame of its swing, or none once the arm has come down. */
    fun swingingOn() = copy(
        striking = when (striking) {
            null -> MonsterPose.ATTACK_A
            MonsterPose.ATTACK_A -> MonsterPose.ATTACK_B
            else -> null
        },
    )

    companion object {
        /** The mode a level gives something that talks before it fights. */
        const val WAITING_TO_SEE = 8

        /** Past this many squares nothing notices the party at all. */
        private const val OUT_OF_SIGHT = 4

        /** Within this, the party are noticed from any side. */
        private const val CLOSE_ENOUGH_TO_FEEL = 2

        /** The wedge of the compass a monster has its back to. */
        private val BEHIND_IT = 3..5

        /** What a monster nobody has rolled for carries instead of hit points. */
        val UNROLLED = HitPoints(current = 0, max = 0)

        /**
         * The monster a script's [CreateMonster] asks for, in a free [slot],
         * belonging to the sublevel it was conjured in.
         */
        fun spawnedBy(spawn: CreateMonster, slot: Int, subLevel: Int = 0) = MonsterInstance(
            subLevel = subLevel,
            index = slot,
            unit = spawn.unit,
            block = (spawn.location.y shl 5) or spawn.location.x,
            place = spawn.place,
            direction = spawn.direction,
            type = spawn.type,
            gfxIndex = spawn.gfxIndex,
            mode = spawn.mode,
            pause = spawn.pause,
            weapon = spawn.weapon,
            pocketItem = spawn.pocketItem,
        )
    }
}

/**
 * A monster the way a log line should name it: the sprite sheet it is drawn
 * from, the species of that sheet it is, the slot it lives in, and the square
 * it stands on.
 *
 * The first three are needed to tell one from another — a sheet holds several
 * species, a species is drawn on one sheet, and two of the same species stand
 * apart only by their slot — and the square is what a reader has in front of
 * them on screen.
 */
fun MonsterInstance.named(on: SubLevel): String {
    val sprite = on.monsterGfx.getOrNull(gfxIndex)?.name ?: "no sprite $gfxIndex"
    return "$sprite kind ${type.value} m$index on ${x}x$y"
}
