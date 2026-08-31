package pl.pelotasplus.eyeofbeholder.data.model

/**
 * Everything in flight, moved on one square.
 *
 * A projectile crosses a square at a time rather than sliding between them,
 * so this is the whole of its travelling: it is asked for repeatedly by
 * whatever is running the clock, and each answer is one square further on.
 *
 * @param sublevel the walls as this floor draws them, which is what says
 *   whether a thing may pass a face at all.
 * @param level which floor it is, for the item it leaves lying when it stops.
 */
class Flight(
    private val sublevel: SubLevel,
    private val level: Int,
    private val itemTypes: ItemTypes? = null,
    private val kinds: List<MonsterProperty> = emptyList(),
    private val dice: Dice = Dice.random,
) {
    /**
     * What one step came to: the world after it, the squares something flew
     * onto, and whatever it hurt on the way.
     *
     * [flewOnto] is kept because a script has to be run for each of them, and
     * running a script is not this class's business — it moves things and says
     * where they went.
     */
    data class Moved(
        val world: GameState,
        val flewOnto: List<Location> = emptyList(),
        val hurt: List<Hurt> = emptyList(),
        /**
         * The wall faces something in the air stopped against, which are told
         * so for the same reason [flewOnto] is: working a wall is not this
         * class's business, and a wall struck is worked exactly as a wall
         * clicked is.
         */
        val struckWalls: List<StruckWall> = emptyList(),
        /**
         * The squares a real thing came to rest on, for the same reason again.
         *
         * A thing that lands has been put down, and the square is told so in
         * those words — which is how something heavy enough to hold a plate
         * down can be thrown onto one rather than carried to it.
         *
         * Only a thing that exists: a conjured bolt is on no floor and has
         * nothing to leave behind, so it lands on nothing.
         */
        val settled: List<Location> = emptyList(),
    )

    /**
 * The same side of a square, at one end of it or the other.
 *
 * A thing crossing a square is not over one spot for the whole of it: it comes
 * in at the end nearest where it came from, passes the middle, and leaves by
 * the far end. Which side it is going down does not change — only which end of
 * that side it is over.
 *
 * @param coming true for the end it arrives at, false for the end it leaves by
 */
private fun SquarePlace.endTowards(going: Direction, coming: Boolean): SquarePlace {
    val onTheWay = asSeenFacing(going) ?: return this

    val end = when (onTheWay) {
        ViewPlace.FAR_LEFT, ViewPlace.NEAR_LEFT ->
            if (coming) ViewPlace.NEAR_LEFT else ViewPlace.FAR_LEFT

        ViewPlace.FAR_RIGHT, ViewPlace.NEAR_RIGHT ->
            if (coming) ViewPlace.NEAR_RIGHT else ViewPlace.FAR_RIGHT

        ViewPlace.MIDDLE -> return this
    }

    return end.onASquareFacing(going)
}

/**
 * Who stands under each quarter of a square, in the order a thing coming
 * through it finds them.
 *
 * A quarter is a side of the party rather than one place in it, so each names
 * the whole file standing down that side: left is the first, third and fifth
 * of them, right the second, fourth and sixth. A thing comes through the file
 * and the first of them still on their feet takes it.
 *
 * The first two of every row are the game's. Where it runs out this does not:
 * the game names one champion for each quarter ahead and nobody behind them,
 * so something through the front left with that champion down passes through
 * the whole party and hurts none of them, however many are standing. Carrying
 * on down the file is the sensible reading of the same idea, and it only ever
 * decides cases the game left to fall on the floor.
 */
private val atEachQuarter = listOf(
    listOf(PartySlot(0), PartySlot(2), PartySlot(4)), // ahead and to the left
    listOf(PartySlot(1), PartySlot(3), PartySlot(5)), // ahead and to the right
    listOf(PartySlot(2), PartySlot(4), PartySlot(0)), // at the left shoulder
    listOf(PartySlot(3), PartySlot(5), PartySlot(1)), // at the right shoulder
)

/** A wall face something ran into, named as the maze names it. */
    data class StruckWall(val at: Location, val side: WallSide)

    sealed interface Hurt {
        data class AChampion(val slot: PartySlot, val by: Damage) : Hurt
        data class AMonster(val slot: MonsterSlot, val by: Damage) : Hurt
    }

    /**
     * Everything in the air, [ticks] further on.
     *
     * Most turns of the clock move nothing: a thing takes several of them to
     * cross one square, and the ones in between are only it getting there.
     */
    fun onward(world: GameState, ticks: Int = GameState.CLOCK_STEP.value): Moved {
        if (world.inFlight.isEmpty()) return Moved(world)

        var carried = world
        val flewOnto = mutableListOf<Location>()
        val hurt = mutableListOf<Hurt>()
        val struckWalls = mutableListOf<StruckWall>()
        val settled = mutableListOf<Location>()
        val stillGoing = mutableListOf<Projectile>()

        // Whatever stopped it, it has stopped: it lies where it is, goes off
        // if it was going to, and the square is told something has been put
        // down on it — unless there was nothing there to put down.
        fun cameToRest(world: GameState, flying: Projectile): GameState {
            if (flying.what != null) settled += flying.at
            return burstIfItWould(landed(world, flying), flying)
        }

        world.inFlight.forEach { flying ->
            val waited = flying.copy(untilNextSquare = flying.untilNextSquare - ticks)

            // Half way across is where it passes the middle of the square and
            // takes up the far end of it, which is the end it will leave by.
            // A thing stopped before that lies at the near end, in front of
            // whatever stopped it rather than beyond it.
            val crossing = if (waited.untilNextSquare * 2 > Projectile.ACROSS_A_SQUARE) {
                waited
            } else {
                waited.copy(place = waited.place.endTowards(waited.going, coming = false))
            }

            if (crossing.untilNextSquare > 0) {
                // What it is over is asked each time it moves, and it moves
                // twice across a square: in at the near end, and on to the far
                // one. That is what lets somebody who walks into the path of a
                // thing already going be found by it, and it is also why this
                // is not asked on every turn of the clock — a square takes
                // several of those, and asking each time would roll to hit the
                // same monster over and over on the way past.
                //
                // Not while it is still leaving, either: that is the square it
                // was thrown from, and nobody hits themselves letting go.
                // Asked on every turn of the clock, because anything may walk
                // under it between one of its own steps and the next — the
                // party by walking, a monster by taking its turn.
                val met = if (waited.leaving) emptyList() else whatItHit(carried, crossing)

                if (met.isEmpty()) {
                    stillGoing += crossing.copy(alreadyTried = triedHere(carried, crossing))
                } else {
                    hurt += met
                    met.forEach { carried = struckDown(carried, it) }
                    carried = cameToRest(carried, crossing)
                }
                return@forEach
            }

            val next = stepped(carried, waited)

            if (next == null) {
                blockedBy(carried, waited)?.let { struckWalls += it }
                carried = cameToRest(carried, waited)
                return@forEach
            }

            flewOnto += next.at
            carried = carriedAlong(carried, next)

            val struck = whatItHit(carried, next)
            if (struck.isNotEmpty()) {
                hurt += struck
                struck.forEach { carried = struckDown(carried, it) }
                carried = cameToRest(carried, next)
                return@forEach
            }

            if (next.squaresLeft <= 0) {
                carried = cameToRest(carried, next)
            } else {
                stillGoing += next
            }
        }

        return Moved(carried.copy(inFlight = stillGoing), flewOnto, hurt, struckWalls, settled)
    }

    /**
     * The wall face that stopped [flying], or null where nothing did — the
     * edge of the map stops a thing as surely as masonry, and there is no
     * wall there to be worked.
     */
    private fun blockedBy(world: GameState, flying: Projectile): StruckWall? {
        val onto = flying.going.oneStepFrom(flying.at)
        if (onto.x !in 0 until sublevel.maz.width || onto.y !in 0 until sublevel.maz.height) {
            return null
        }

        val facing = flying.going.wallSideFacingBack
        if (sublevel.canBeReachedOnto(world.wall(level, onto, facing))) return null

        return StruckWall(onto, facing)
    }

    /**
     * The projectile one square along, or null where the wall stops it.
     *
     * Every crossing is asked, the first one included: a thing thrown at the
     * wall in front of the party strikes it and drops at their feet. Being
     * newly loosed buys it nothing here — that only spares it the square it
     * is still standing on, which it has not tried to leave yet.
     */
    private fun stepped(world: GameState, flying: Projectile): Projectile? {
        val onto = flying.going.oneStepFrom(flying.at)

        if (onto.x !in 0 until sublevel.maz.width || onto.y !in 0 until sublevel.maz.height) {
            return null
        }

        val face = world.wall(level, onto, flying.going.wallSideFacingBack)
        if (!sublevel.canBeReachedOnto(face)) return null

        return flying.copy(
            at = onto,
            // it comes in at the end of the square nearest where it came from
            place = flying.place.endTowards(flying.going, coming = true),
            squaresLeft = flying.squaresLeft - 1,
            untilNextSquare = Projectile.ACROSS_A_SQUARE,
            leaving = false,
            // a new square is a new set of things to be asked about
            alreadyTried = emptySet(),
        )
    }

    /**
     * Whoever the projectile ran into on the square it has just crossed onto,
     * which is nobody on an empty one.
     *
     * The monsters on the square take it; failing them the party do, if they
     * are standing there. Nothing aims — the thing is already in the air, and
     * who it finds is where it went.
     */
    private fun whatItHit(world: GameState, flying: Projectile): List<Hurt> {
        val monsters = monstersUnder(world, flying)
            .filterNot { it.index in flying.alreadyTried }

        if (monsters.isNotEmpty()) {
            val struck = if (flying.harm.everybody) monsters else monsters.take(1)

            return struck.mapNotNull {
                val kind = kinds.getOrNull(it.type.value)
                if (!lands(world, flying, kind)) null
                else Hurt.AMonster(it.index, damageOf(world, flying, kind))
            }
        }

        return whoTheyWalkedInto(world, flying)
    }

    /**
     * Whatever is under [flying] and could be hurt by it.
     *
     * A thing in the air is over one quarter of a square rather than the
     * whole of it, and crossing takes it down one side: the near corner of
     * that side, then the far one. So it meets what is standing in the corner
     * it is over and nothing standing across the way — which is what lets a
     * stone be thrown past somebody, and what makes which side it was thrown
     * from worth anything.
     *
     * Whatever stands in the middle is in the way of all of them, having
     * spread over the square rather than keeping to a corner.
     *
     * A burst is the exception. It takes the square whole.
     */
    private fun monstersUnder(world: GameState, flying: Projectile) = world.monsters
        .filter { it.x == flying.at.x && it.y == flying.at.y && it.couldBeHurt }
        .filter {
            flying.harm.everybody ||
                it.place == flying.place ||
                it.place == SquarePlace.MIDDLE
        }

    /**
     * Everything under it that has now had its chance, which is everything
     * under it: one that was missed is not asked again for standing there, and
     * one that walks up afterwards has not been asked yet.
     */
    private fun triedHere(world: GameState, flying: Projectile): Set<MonsterSlot> =
        flying.alreadyTried + monstersUnder(world, flying).map { it.index }

    /**
     * Whoever of the party is under [flying], which is nobody unless they are
     * standing on the square it is over.
     *
     * This half is asked on its own as well, and often: the party are the one
     * thing that can arrive under something already in the air, and nothing
     * rolls to hit them, so there is no harm in asking as often as they might.
     */
    private fun whoTheyWalkedInto(world: GameState, flying: Projectile): List<Hurt> {
        if (flying.at != world.party.position) return emptyList()

        // Being down is not being out of the way. A burst takes everybody in
        // the party, awake or not, and is what finishes off somebody already
        // lying there — only being past raising puts a champion beyond it,
        // and a burst does not even ask that much.
        val there = world.champions.indices
            .map(::PartySlot)
            .filter { world.championIn(it)?.inTheParty == true }

        val canBeHurt = if (flying.harm.everybody) {
            there
        } else {
            there.filter { world.championIn(it)?.deadForGood == false }
        }

        if (canBeHurt.isEmpty()) return emptyList()

        // A burst takes the whole square and is rolled for each of them; a
        // thrown thing finds whoever is standing where it came through.
        val struck = if (flying.harm.everybody) canBeHurt else whoItFound(world, flying, canBeHurt)

        return struck.map { Hurt.AChampion(it, damageOf(world, flying, null)) }
    }

    /**
     * Which of the party a thing in the air comes through.
     *
     * Where on the square it is decides it, read as the party see it: the two
     * quarters ahead of them are the front rank's, one each, and the two
     * behind belong to the pairs standing at each shoulder. Nothing aims and
     * nothing rolls to hit — walking under something is walking under it.
     *
     * Whoever is first down that file and still on their feet takes it — see
     * [atEachQuarter], which is where this parts company with the game.
     *
     * Two of them are hurt only by the near right, and only by something
     * travelling along the way they face. That the near left has no answering
     * pair is the game's, not a slip here.
     */
    private fun whoItFound(
        world: GameState,
        flying: Projectile,
        canBeHurt: List<PartySlot>,
    ): List<PartySlot> {
        val quarter = flying.place.asSeenFacing(world.party.facing) ?: return emptyList()
        val file = atEachQuarter.getOrNull(quarter.ordinal) ?: return emptyList()

        // which of the near right two is in front of the other is a toss, and
        // only those two: whoever is behind them both stays behind them both
        val order = if (quarter == ViewPlace.NEAR_RIGHT && dice.roll(1, 2, -1) == 1) {
            listOf(file[1], file[0]) + file.drop(2)
        } else {
            file
        }

        val standing = order.filter { it in canBeHurt }
        val alongTheirAxis = world.party.facing == flying.going ||
            world.party.facing == flying.going.turnedBy(2)

        return if (quarter == ViewPlace.NEAR_RIGHT && alongTheirAxis) {
            standing.take(2)
        } else {
            standing.take(1)
        }
    }

    /**
     * Whether a thing in the air finds the monster it has come to, which only
     * something a champion threw ever fails to do.
     *
     * Nobody threw a trap's bolt, so there is nobody to have aimed it badly
     * and it always lands. A champion's throw is rolled for the way a blow of
     * theirs is, save that it is their dexterity that carries it rather than
     * their strength — a throw is aimed and not shoved.
     *
     * A miss is not the end of it. The thing is still in the air and still
     * going, so it carries on over the monster it missed to whatever is behind
     * them, which is the whole reason a miss is worth telling from a hit.
     */
    private fun lands(world: GameState, flying: Projectile, kind: MonsterProperty?): Boolean {
        val threw = (flying.thrownBy as? Projectile.Thrower.AChampion) ?: return true
        val champion = world.championIn(threw.slot) ?: return true

        val weapon = flying.what?.let { world.item(it) }
        val bonus = champion.abilities.dexterityToHitBonus + (weapon?.value ?: 0)
        val needed = champion.needsToHit(kind?.armorClass ?: 0) - bonus

        return dice.roll(1, 20, 0).coerceIn(1, 20) >= needed
    }

    /** What the thing does where it lands, which is what kind of thing it is. */
    private fun damageOf(world: GameState, flying: Projectile, kind: MonsterProperty?): Damage {
        flying.harm.dice?.let { own ->
            val rolled = dice.roll(own.times, own.pips, own.base) * flying.harm.times
            return Damage(rolled.coerceAtLeast(0))
        }

        val what = flying.what?.let { world.item(it) }
        val rolled = what?.let { itemTypes?.get(it.type)?.damageAgainst(kind, dice) }
            ?: dice.roll(1, 6, 0)

        return Damage((rolled * flying.harm.times).coerceAtLeast(0))
    }

    private fun struckDown(world: GameState, hurt: Hurt): GameState = when (hurt) {
        is Hurt.AMonster -> world.monsterHurt(hurt.slot, hurt.by, kinds, itemTypes, dice)
        is Hurt.AChampion -> world.championHurt(hurt.slot, hurt.by)
    }

    /**
     * A thing that goes off, going off where it stopped.
     *
     * It bursts whether it was stopped by somebody or by a wall — a fireball
     * that reaches the end of a corridor still explodes against it.
     */
    private fun burstIfItWould(world: GameState, flying: Projectile): GameState =
        if (!flying.harm.everybody) world
        else world.copy(
            bursting = world.bursting + Burst.of(
                at = flying.at,
                dice = dice,
                inYourFace = flying.at == world.party.position,
            ),
        )

    /**
     * Where a projectile is now, as far as the floor is concerned. A conjured
     * bolt is on no floor at all and leaves nothing behind it.
     */
    /**
     * A thing still going is over its square rather than on it: it is in the
     * air, and what is in the air is drawn at the height of it rather than
     * lying among whatever else is on that floor.
     *
     * Which corner it is over is kept on the projectile and not on the item,
     * so that the corner is still there to be put back when it comes down.
     */
    private fun carriedAlong(world: GameState, flying: Projectile): GameState =
        flying.what?.let { world.itemLandedAt(it, level, flying.at, SquarePlace.MIDDLE) } ?: world

    /** And one that has stopped lies in the corner it was over when it did. */
    private fun landed(world: GameState, flying: Projectile): GameState =
        flying.what?.let { world.itemLandedAt(it, level, flying.at, flying.place) } ?: world
}
