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
     * strikes every other turn rather than every one — the original flips a
     * bit each time it comes round and does nothing on the turns the bit lands
     * clear, which halves how hard a fight comes at the party and is most of
     * what makes one survivable.
     */
    val readyToStrike: Boolean = false,
    /**
     * Whether it spent its last turn turning round. Turning costs a monster
     * the turn after it as well, so it cannot spin to face the party and swing
     * in the same breath — which is what gives a party who step round one the
     * time to do it.
     */
    val justTurned: Boolean = false,
) {
    val x: Int get() = block and 0x1F
    val y: Int get() = block shr 5

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
    val standingBy: Boolean get() = mode == WAITING_TO_SEE

    /**
     * Whether its arm gets to the party from where it stands: it must face
     * their square, and stand on the half of its own square that reaches.
     * Something filling a square reaches from anywhere on it.
     */
    fun canReach(party: PartyState): Boolean {
        val (dx, dy) = direction.transformCoordinates(0, -1)
        if (x + dx != party.position.x || y + dy != party.position.y) return false

        return !place.onTheFloor || WhoTheMonsterReaches.armIsLongEnough(direction, place)
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
