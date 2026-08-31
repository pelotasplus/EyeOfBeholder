package pl.pelotasplus.eyeofbeholder.data.model

import kotlin.math.abs

/**
 * What comes of the party bedding down where they stand.
 */
sealed interface Rest {

    /** Nobody slept: something came near enough while the party settled. */
    data class SomethingIsNear(val world: GameState) : Rest

    /** Nobody slept: a script has forbidden it here. */
    data object NotHere : Rest

    /** The party slept — [world] is what they wake to, mended and hungrier. */
    data class Slept(val world: GameState) : Rest
}

/**
 * The party sleeping where they stand.
 *
 * Whether anything is near enough to stop them is not measured but played out:
 * the monsters are given [ROUNDS_BEFORE_SETTLING] turns to move first, and if
 * any of them ends up beside the party the rest is refused. So a monster two
 * corridors away is no trouble, and one that walks in while the party are
 * settling is — which a straight measure of distance cannot tell apart.
 */
class Resting(
    private val kinds: List<MonsterProperty>,
    private val stepping: MonsterStepping? = null,
) {

    /**
     * The party sleep, mending a hit point each and eating their way through
     * their food, until nobody is hurt any more or the food runs out.
     *
     * Everyone still standing pays food for as long as the party sleep, whether
     * they had anything to mend or not — which is what a long rest to heal one
     * badly-hurt champion costs the others.
     *
     * Spells are memorised over a rest too, in the game this will become; there
     * are none to memorise yet, so a rest is only its food and its mending.
     */
    fun rest(world: GameState, walking: MonsterPathing? = null): Rest {
        val begun = begin(world, walking)
        if (begun !is Rest.Slept) return begun

        var sleeping = begun.world
        while (sleeping.champions.any { it.wouldMendBySleeping }) {
            sleeping = sleeping.sleptAnHour(HOURS_A_MENDED_POINT)
        }
        return Rest.Slept(sleeping)
    }

    /**
     * Whether the party may lie down at all, and the world they lie down in —
     * the monsters having had their turns while the party settled.
     */
    fun begin(world: GameState, walking: MonsterPathing? = null): Rest {
        if (world.preventRest) return Rest.NotHere

        val settled = whileTheySettle(world, walking)
        if (settled.monsters.any { settled.isBesideTheParty(it) }) {
            return Rest.SomethingIsNear(settled)
        }
        return Rest.Slept(settled)
    }

    /**
     * Whether anybody is still hurt and has the food to mend, which is what
     * keeps a rest going.
     */
    fun anybodyStillMending(world: GameState): Boolean = world.anybodyCanStillMend

    /**
     * The world after the monsters have had their few turns, which is what the
     * party settling down amounts to. Whatever walked stays where it walked,
     * slept or not.
     */
    private fun whileTheySettle(world: GameState, walking: MonsterPathing?): GameState {
        // Only what the caller says may walk, walks. Making a way to walk up
        // here instead would send monsters after the party that are meant to be
        // standing still, and a rest is not the place to overrule that.
        if (walking == null) return world

        var settling = world
        repeat(ROUNDS_BEFORE_SETTLING) {
            THE_UNITS_THAT_STIR.forEach { unit ->
                settling = MonstersTurn(kinds, stepping = stepping)
                    .begun(settling, walking = walking, group = unit)
            }
        }
        return settling
    }

    private companion object {
        /** How many turns the monsters get while the party are settling. */
        const val ROUNDS_BEFORE_SETTLING = 5

        /** Which units are given those turns. */
        val THE_UNITS_THAT_STIR = listOf(0, 1)
    }
}

/**
 * Whether [monster] stands within arm's reach of the party.
 *
 * On the game's own measure of distance, where the shorter way is halved
 * before it is added to the longer, so a diagonal step is worth less than two
 * straight ones.
 */
fun GameState.isBesideTheParty(monster: MonsterInstance): Boolean {
    val across = abs(monster.x - party.position.x)
    val along = abs(monster.y - party.position.y)
    return (minOf(across, along) / 2) + maxOf(across, along) <= WITHIN_REACH
}

/**
 * The world [hours] of sleep further on: everyone still standing mends a hit
 * point and eats, which is what an hour of sleep comes to.
 *
 * Nothing happens by the hour — mending and hunger both come round every
 * [HOURS_A_MENDED_POINT] hours — so this is called with that many at a time.
 */
fun GameState.sleptAnHour(hours: Int = 1): GameState {
    if (hours < HOURS_A_MENDED_POINT) return this
    return copy(champions = champions.map { it.sleptAStep() })
        .gripsStepped(Ticks(hours * TICKS_AN_HOUR)).first
        .venomWorkedFor(hours)
}

/**
 * The world [hours] further on for whoever is carrying venom.
 *
 * Lying down does nothing to it, and is the worst thing to do while carrying
 * it: it takes [VENOM_AN_HOUR] off every poisoned champion for each hour
 * slept, on top of what it is already taking from them by the half-minute.
 * A night of it costs far more than the night mends, which is what makes the
 * cure worth carrying rather than worth saving.
 *
 * Nothing is rolled — it is the same ten every hour.
 */
private fun GameState.venomWorkedFor(hours: Int): GameState =
    poisoned.fold(this) { world, whose ->
        world.championHurt(whose, Damage(VENOM_AN_HOUR * hours))
    }

/** What an hour of sleep costs somebody poisoned. Transcribed. */
private const val VENOM_AN_HOUR = 10

/**
 * Ticks in an hour of sleep.
 *
 * Sleeping is not a cure for anything. It is time passing, and whatever has
 * hold of a champion is on a clock: the party get up with every one of those
 * clocks wound forward by however long they lay there, and anything with less
 * than that left on it has run out. An hour is a dozen times longer than the
 * longest grip, so in practice a party who lie down get up free of it.
 *
 * Transcribed rather than derived.
 */
private const val TICKS_AN_HOUR = 32760

/** Whether anybody is hurt at all, whether or not they can do anything about it. */
val GameState.anybodyStillHurt: Boolean get() = champions.any { it.stillHurt }

/** Whether anybody still standing has run out of food. */
val GameState.anybodyStarving: Boolean
    get() = champions.any { it.inTheParty && !it.deadForGood && it.food.value <= 0 }

/**
 * Whether anybody's last mouthful went in the stretch between this world and
 * [after] — the moment a party become a starving one, which is the moment
 * worth telling them about.
 */
fun GameState.somebodyRanOutOfFood(after: GameState): Boolean =
    champions.zip(after.champions).any { (was, now) ->
        was.food.value > 0 && now.food.value <= 0
    }

/**
 * A day of sleep on an empty stomach, which costs a hit point.
 *
 * Only sleeping starves a champion — walking about on nothing does not — and
 * somebody already past raising is past being starved as well.
 */
fun GameState.starvedADay(): GameState = copy(
    champions = champions.map {
        val starving = it.inTheParty && !it.deadForGood && it.food.value <= 0 &&
            it.hitPoints.current > Champion.BEYOND_RAISING
        if (!starving) it else it.copy(
            hitPoints = it.hitPoints.copy(current = it.hitPoints.current - 1),
        )
    },
)

/** Whether anybody is hurt and has the food to mend it, which is what a rest needs. */
val GameState.anybodyCanStillMend: Boolean get() = champions.any { it.wouldMendBySleeping }

/**
 * Hurt, and near enough alive to mend.
 *
 * Being down is not being past mending: a champion at nothing left is
 * unconscious rather than gone, and sleep brings them round. Only somebody
 * past raising is beyond what a rest can do — which takes a cleric, not a bed.
 */
private val Champion.stillHurt: Boolean
    get() = inTheParty && !deadForGood && hitPoints.current < hitPoints.max

/** Whether a champion can sleep and has hurt for the sleep to mend. */
val Champion.wouldMendBySleeping: Boolean
    get() = stillHurt && food.value > 0

/** A champion one stretch of sleep on: hurt mended if there is any, and food eaten. */
private fun Champion.sleptAStep(): Champion {
    if (!inTheParty || deadForGood || food.value <= 0) return this
    return copy(
        hitPoints = hitPoints.copy(
            current = (hitPoints.current + POINTS_MENDED_A_STRETCH).coerceAtMost(hitPoints.max),
        ),
        food = Food((food.value - FOOD_A_STEP).coerceAtLeast(0)),
    )
}

/** How near a monster has to be to keep the party awake. */
private const val WITHIN_REACH = 1

/** How long a stretch of sleep is: what mending and hunger both come round on. */
const val HOURS_A_MENDED_POINT = 8

/** How long an empty stomach takes to cost a hit point. */
const val HOURS_A_STARVED_POINT = 24

/**
 * What a stretch of sleep mends.
 *
 * A workaround until spells. Sleep alone is meant to be a trickle — a single
 * hit point a stretch — because the healing that matters is a cleric casting
 * it over the sleeping party, and there is no spellcasting yet. A trickle on
 * its own leaves a badly hurt champion weeks of sleep and more food than they
 * can carry away from full health, so it mends a stretch's worth at a time
 * instead. Put this back to one when the clerics can do their part.
 */
private const val POINTS_MENDED_A_STRETCH = HOURS_A_MENDED_POINT * 4

/**
 * What that stretch of sleep costs each champion in food.
 *
 * The other half of the same workaround. What the game means by a rest is
 * bounded by the food it eats, so mending faster without eating slower only
 * moves where the party run out — and one who joins the party with eight food
 * to their name cannot mend at all on the game's own rate. Put this back to
 * five along with the mending above.
 */
private const val FOOD_A_STEP = 2
