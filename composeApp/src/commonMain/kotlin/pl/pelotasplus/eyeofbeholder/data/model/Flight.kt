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
    )

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
        val stillGoing = mutableListOf<Projectile>()

        world.inFlight.forEach { flying ->
            val waited = flying.copy(untilNextSquare = flying.untilNextSquare - ticks)

            if (waited.untilNextSquare > 0) {
                stillGoing += waited
                return@forEach
            }

            val next = stepped(carried, waited)

            if (next == null) {
                carried = burstIfItWould(landed(carried, waited), waited)
                return@forEach
            }

            flewOnto += next.at
            carried = carriedAlong(carried, next)

            val struck = whatItHit(carried, next)
            if (struck.isNotEmpty()) {
                hurt += struck
                struck.forEach { carried = struckDown(carried, it) }
                carried = burstIfItWould(landed(carried, next), next)
                return@forEach
            }

            if (next.squaresLeft <= 0) {
                carried = landed(carried, next)
            } else {
                stillGoing += next
            }
        }

        return Moved(carried.copy(inFlight = stillGoing), flewOnto, hurt)
    }

    /**
     * The projectile one square along, or null where the wall stops it.
     *
     * The square it is leaving cannot stop it. A trap fires out of the
     * masonry it is built into, and asking that masonry's permission would
     * mean nothing ever left the wall it came from.
     */
    private fun stepped(world: GameState, flying: Projectile): Projectile? {
        val onto = flying.going.oneStepFrom(flying.at)

        if (onto.x !in 0 until sublevel.maz.width || onto.y !in 0 until sublevel.maz.height) {
            return null
        }

        val face = world.wall(level, onto, flying.going.wallSideFacingBack)
        if (!flying.leaving && !sublevel.canBeReachedOnto(face)) return null

        return flying.copy(
            at = onto,
            squaresLeft = flying.squaresLeft - 1,
            untilNextSquare = Projectile.ACROSS_A_SQUARE,
            leaving = false,
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
        val monsters = world.monsters.filter {
            it.x == flying.at.x && it.y == flying.at.y && it.couldBeHurt
        }

        if (monsters.isNotEmpty()) {
            val struck = if (flying.harm.everybody) monsters else monsters.take(1)
            return struck.map {
                Hurt.AMonster(it.index, damageOf(world, flying, kinds.getOrNull(it.type.value)))
            }
        }

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
        // thrown thing finds one of them and stops there.
        val struck = if (flying.harm.everybody) {
            canBeHurt
        } else {
            listOfNotNull(canBeHurt.getOrNull(dice.roll(1, canBeHurt.size, -1)))
        }

        return struck.map { Hurt.AChampion(it, damageOf(world, flying, null)) }
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
        is Hurt.AMonster -> world.monsterHurt(hurt.slot, hurt.by)
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
    private fun carriedAlong(world: GameState, flying: Projectile): GameState =
        flying.what?.let { world.itemLandedAt(it, level, flying.at, flying.place) } ?: world

    /** A projectile that has stopped is simply a thing lying where it stopped. */
    private fun landed(world: GameState, flying: Projectile): GameState =
        carriedAlong(world, flying)
}
