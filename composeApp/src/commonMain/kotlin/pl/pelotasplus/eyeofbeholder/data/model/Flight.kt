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
    // What a spell does where it arrives. Without it a spell crosses the room
    // and stops, which is every spell's picture and none of its point.
    private val landing: WhereASpellLands? = null,
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
        /**
         * What a spell coming down on the party left on them, which is not
         * damage and so is not in [hurt]: a paralysis has no number to put on
         * a portrait, and something has to say it out loud.
         */
        val left: List<WhereASpellLands.Left> = emptyList(),
        /**
         * Every spell that got as far as the party, and what it did there.
         *
         * Worth reporting separately from the damage because four of them are
         * one picture and one sound: nothing on screen says which of a
         * beholder's rays arrived, so this is the only place it is named.
         */
        val landed: List<Landing> = emptyList(),
    )

    /** One spell arriving, and what it cost. */
    data class Landing(
        val spell: MonsterSpell,
        val hurt: List<PartySlot>,
        val left: List<WhereASpellLands.Left>,
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
    val onTheWay = onAQuarter().asSeenFacing(going) ?: return this

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
 * Whether it has already reached the end of this square it will leave by.
 *
 * The whole of how a thing crosses a square, and the reason it is asked
 * rather than counted: a step takes it to the far end, and a step made from
 * the far end takes it off the square altogether. So something loosed at the
 * far end to begin with — which is what a monster shooting the way it faces
 * often is — leaves on its first step and is never drawn twice over its own
 * square.
 */
private fun SquarePlace.atTheFarEndGoing(going: Direction) =
    endTowards(going, coming = false) == onAQuarter()


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
        val left = mutableListOf<WhereASpellLands.Left>()
        val landed = mutableListOf<Landing>()
        val stillGoing = mutableListOf<Projectile>()

        // Whatever stopped it, it has stopped: it lies where it is, goes off
        // if it was going to, and the square is told something has been put
        // down on it — unless there was nothing there to put down.
        fun cameToRest(world: GameState, flying: Projectile): GameState {
            if (flying.what != null) settled += flying.at

            // A spell does whatever it does here and nowhere else. One that
            // stopped short — against a wall, or on a monster it was allowed
            // to touch — has simply failed to arrive.
            //
            // What arrives is what somebody else sent. This table is how hard
            // the floor throws, so running it on the party's own spell would
            // hurt them by the depth they are at rather than by anything their
            // caster did.
            val arrived = flying.spell != null &&
                flying.at == world.party.position &&
                !sparesThem(flying)

            val after = if (arrived && flying.spell != null) {
                landing?.of(flying.spell, world, flying.place)?.also {
                    left += it.left
                    landed += Landing(flying.spell, it.hurt, it.left)
                }?.world ?: world
            } else {
                world
            }

            return burstIfItWould(landed(after, flying), flying)
        }

        // What a thing over a square meets there, which is asked whether or
        // not it has just moved: anything can walk under a thing in the air
        // between one of its own steps and the next, the party by walking and
        // a monster by taking its turn.
        //
        // Not while it is still leaving, though — that is the square it was
        // thrown from, and nobody hits themselves letting go.
        fun overItsSquare(now: Projectile) {
            val met = if (now.leaving) emptyList() else whatItHit(carried, now)

            if (met.isEmpty()) {
                stillGoing += now.copy(alreadyTried = triedHere(carried, now))
            } else {
                hurt += met
                met.forEach { carried = struckDown(carried, it) }
                carried = cameToRest(carried, now)
            }
        }

        world.inFlight.forEach { flying ->
            var now = flying.copy(untilItSteps = flying.untilItSteps - ticks)
            var stopped = false
            var steps = 0

            // As many steps as the ticks have paid for, which is nearly always
            // none or one — a caller handing over a whole square's worth gets
            // the whole square rather than half of it.
            while (!stopped && now.untilItSteps <= 0) {
                steps++

                // A step takes it to the far end of the square it is over, and
                // a step made from that end takes it off the square. Which of
                // the two this is depends on where it already stands rather
                // than on how long it has been going, so a thing loosed at the
                // far end leaves on its first step instead of being drawn
                // twice over the square it came from.
                if (now.place.atTheFarEndGoing(now.going)) {
                    val next = stepped(carried, now)

                    if (next == null) {
                        blockedBy(carried, now)?.let { struckWalls += it }
                        carried = cameToRest(carried, now)
                        stopped = true
                        continue
                    }

                    now = next.copy(untilItSteps = now.untilItSteps + Projectile.A_STEP)
                    flewOnto += now.at
                    carried = carriedAlong(carried, now)
                } else {
                    now = now.copy(
                        place = now.place.endTowards(now.going, coming = false),
                        untilItSteps = now.untilItSteps + Projectile.A_STEP,
                    )
                }

                val met = if (now.leaving) emptyList() else whatItHit(carried, now)

                if (met.isNotEmpty()) {
                    hurt += met
                    met.forEach { carried = struckDown(carried, it) }
                    carried = cameToRest(carried, now)
                    stopped = true
                    continue
                }

                now = now.copy(alreadyTried = triedHere(carried, now))

                if (now.squaresLeft <= 0) {
                    carried = cameToRest(carried, now)
                    stopped = true
                }
            }

            if (stopped) return@forEach

            // One that moved has already been asked what it met. One that did
            // not still has to be: somebody may have walked under it.
            if (steps > 0) stillGoing += now else overItsSquare(now)
        }

        return Moved(
            carried.copy(inFlight = stillGoing),
            flewOnto,
            hurt,
            struckWalls,
            settled,
            left,
            landed,
        )
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
            untilItSteps = Projectile.A_STEP,
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
        // A spell nobody in the party cast goes over the head of the monsters
        // it crosses, unless it is one of the ones already burning as they
        // travel. It is why a corridor of monsters never thins itself out, and
        // why a trap's bolt sails over them to reach the party behind.
        //
        // What the party throw is under no such rule: their spell takes the
        // first thing it comes to, which is the whole point of throwing it.
        val fromTheParty = flying.thrownBy is Projectile.Thrower.AChampion
        val overThem = !fromTheParty && flying.spell?.hurtsWhatItPasses == false


        val monsters = if (overThem) {
            emptyList()
        } else {
            monstersUnder(world, flying).filterNot { it.index in flying.alreadyTried }
        }

        if (monsters.isNotEmpty()) {
            val struck = if (flying.harm.everybody) monsters else monsters.take(1)

            return struck.mapNotNull {
                val kind = kinds.getOrNull(it.type.value)
                if (!lands(world, flying, kind)) null
                else Hurt.AMonster(
                    it.index,
                    thrownAgainst(damageOf(world, flying, kind), flying) { off ->
                        kind?.saves(off, dice) == true
                    },
                )
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
        if (sparesThem(flying)) return emptyList()

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

        // Each of them rolled for separately: a burning thing is thrown off by
        // whoever manages it and takes the rest whole, so a party do not share
        // one throw between them.
        return struck.map { whose ->
            Hurt.AChampion(
                whose,
                thrownAgainst(damageOf(world, flying, null), flying) { off ->
                    world.championIn(whose)?.saves(off, dice) == true
                },
            )
        }
    }

    /**
     * What is left of [harm] once whoever it found has had their throw.
     *
     * A thing that allows no throw at all is left whole — [makes] is not even
     * asked, so nothing rolls a die it was never going to be given.
     */
    private fun thrownAgainst(
        harm: Damage,
        flying: Projectile,
        makes: (SavingThrow) -> Boolean,
    ): Damage {
        val off = flying.harm.thrownOff ?: return harm

        return if (makes(off)) flying.harm.aMadeThrowIsWorth.of(harm) else harm
    }

    /**
     * Whether a spell the party cast passes through them rather than touching
     * them.
     *
     * A spell knows whose it is: walking into your own missile costs nothing.
     * Two of theirs are not so careful — a fireball and a bolt of lightning
     * take either side — and those say so.
     *
     * Only a spell. A thrown thing is a thrown thing wherever it came from,
     * and hits whoever is standing where it went.
     */
    private fun sparesThem(flying: Projectile) =
        flying.spell != null &&
            flying.thrownBy is Projectile.Thrower.AChampion &&
            !flying.harm.takesEitherSide

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
        // A conjured thing is not aimed. It goes where it was sent and takes
        // whatever is standing there, so there is nothing to roll: a missile
        // is the one thing in the game that never misses.
        if (flying.spell != null) return true

        val threw = (flying.thrownBy as? Projectile.Thrower.AChampion) ?: return true
        val champion = world.championIn(threw.slot) ?: return true

        val weapon = flying.what?.let { world.item(it) }

        // A thrown weapon is refused by a creature too good for it in the same
        // way a swung one is, before the roll. Only a thrown thing is asked:
        // nobody aimed a trap's bolt, and it has already landed above.
        if (weapon != null && kind != null && !kind.immunities.canBeHitBy(weapon.value)) {
            return false
        }

        val bonus = champion.abilities.dexterityToHitBonus + (weapon?.value ?: 0)
        val needed = champion.needsToHit(kind?.armorClass ?: 0) - bonus

        return dice.roll(1, 20, 0).coerceIn(1, 20) >= needed
    }

    /** What the thing does where it lands, which is what kind of thing it is. */
    private fun damageOf(world: GameState, flying: Projectile, kind: MonsterProperty?): Damage {
        // Something that shrugs this kind of harm off takes none of it, and
        // still stops the thing: a bolt is spent on a creature it cannot hurt
        // rather than carrying on through it to the next one.
        if (kind?.immunities?.shrugsOff(flying.harm.hurting) == true) return Damage(0)

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
                burning = flying.burstsLike,
            ),
        )

    /**
     * A thing still going is over its square rather than on it: it is in the
     * air, and what is in the air is drawn at the height of it rather than
     * lying among whatever else is on that floor. A conjured bolt is on no
     * floor at all and leaves nothing behind it either way.
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
