package pl.pelotasplus.eyeofbeholder.data.model

import kotlin.math.abs

/**
 * What comes of the party bedding down where they stand.
 */
sealed interface Rest {

    /** Nobody slept, or the rest was broken: something came near enough. */
    data class SomethingIsNear(val world: GameState) : Rest

    /** Nobody slept: a script has forbidden it here. */
    data object NotHere : Rest

    /** The party may lie down: [world] is the one they lie down in. */
    data class Slept(val world: GameState) : Rest

    /**
     * The party up again after [hours], in [world]. [fullyRested] is whether
     * the rest found nobody left to mend, which is what the party are told.
     */
    data class Woke(val world: GameState, val hours: Int, val fullyRested: Boolean) : Rest
}

/**
 * The party sleeping where they stand.
 *
 * Whether anything is near enough to stop them is not measured but played out:
 * the monsters are given [ROUNDS_BEFORE_SETTLING] turns to move first, and if
 * any of them ends up beside the party the rest is refused. So a monster two
 * corridors away is no trouble, and one that walks in while the party are
 * settling is — which a straight measure of distance cannot tell apart. The
 * same is played out again every ten minutes of the rest, see [night].
 */
class Resting(
    private val kinds: List<MonsterProperty>,
    private val stepping: MonsterStepping? = null,
    val types: ItemTypes? = null,
) {

    /**
     * Whether the party may lie down at all, and the world they lie down in —
     * the monsters having had their turns while the party settled.
     *
     * One standing by until it sees the party does not count, even beside
     * them: it has not noticed them yet, and settling is when it would.
     */
    fun begin(world: GameState, walking: MonsterPathing? = null): Rest {
        if (world.preventRest) return Rest.NotHere

        val settled = whileTheySettle(world, walking)
        if (settled.monsters.any { !(it.standingBy && !it.provoked) && settled.isBesideTheParty(it) }) {
            return Rest.SomethingIsNear(settled)
        }
        return Rest.Slept(settled)
    }

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
                    .world
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
 * An hour of sleep further on: whatever has hold of somebody is an hour
 * nearer letting go, and venom takes [VENOM_AN_HOUR] from everybody carrying
 * it.
 *
 * Sleeping is not a cure for anything. It is time passing, and an hour is a
 * dozen times longer than the longest grip, so in practice a party who lie
 * down for an hour get up free of one. Venom is the exception, and lying
 * down is the worst thing to do while carrying it: a night of it costs far
 * more than the night mends, which is what makes the cure worth carrying.
 */
fun GameState.anHourSlept(): GameState =
    gripsStepped(Ticks(TICKS_AN_HOUR)).first.venomWorkedFor(1)

/**
 * [HOURS_A_MENDED_POINT] hours of sleep further on, [hours] into the rest.
 *
 * A paladin has lay on hands back. Everybody with food mends a hit point and
 * eats [FOOD_A_STRETCH] of it unless a ring feeds them; and on the stretch
 * that ends a day, somebody with nothing in them loses a hit point instead.
 * Mending wants a full stomach even from somebody a ring feeds — what the
 * ring means is that the stomach never empties.
 *
 * Says whether anybody was brought to starving by it: a stomach emptied, or
 * a hit point lost to an empty one. Eating the last five counts, even when it
 * leaves nothing over.
 */
fun GameState.aStretchSlept(hours: Int, types: ItemTypes?): Pair<GameState, Boolean> {
    var starving = false
    val slept = champions.mapIndexed { slot, who ->
        val withHands = who.layOnHandsBack()
        if (!who.inTheParty || who.deadForGood) return@mapIndexed withHands

        if (who.food.value > 0) {
            val mended = withHands.copy(
                hitPoints = who.hitPoints.copy(current = (who.hitPoints.current + 1).coerceAtMost(who.hitPoints.max)),
            )
            when {
                fedByARing(PartySlot(slot), types) -> mended
                who.food.value <= FOOD_A_STRETCH -> mended.copy(food = Food(0)).also { starving = true }
                else -> mended.copy(food = Food(who.food.value - FOOD_A_STRETCH))
            }
        } else if (hours % HOURS_A_STARVED_POINT == 0) {
            starving = true
            withHands.copy(hitPoints = who.hitPoints.copy(current = who.hitPoints.current - 1))
        } else {
            withHands
        }
    }
    return copy(champions = slept) to starving
}

/**
 * A paladin's lay on hands, back on their first page: made ready where it
 * is, or put in the first empty place there. Only a paladin and nothing else
 * gets it back this way.
 */
private fun Champion.layOnHandsBack(): Champion {
    if (characterClass != CharacterClass.PALADIN) return this
    val firstPage = clericSpells.page(0)
    val at = firstPage.indexOfFirst { it.spell == Spell.LAY_ON_HANDS }.takeIf { it >= 0 }
        ?: firstPage.indexOfFirst { it == SpellSlot.Empty }.takeIf { it >= 0 }
        ?: return this
    return copy(clericSpells = clericSpells.with(at, SpellSlot.Ready(Spell.LAY_ON_HANDS)))
}

private fun GameState.venomWorkedFor(hours: Int): GameState =
    poisoned.fold(this) { world, whose ->
        world.championHurt(whose, Damage(VENOM_AN_HOUR * hours))
    }

/** What an hour of sleep costs somebody poisoned. Transcribed. */
private const val VENOM_AN_HOUR = 10

/** Ticks in an hour of sleep. Transcribed rather than derived. */
private const val TICKS_AN_HOUR = 32760

/** What a stretch of sleep costs in food. */
private const val FOOD_A_STRETCH = 5

/** Whether anybody is hurt at all, whether or not they can do anything about it. */
val GameState.anybodyStillHurt: Boolean get() = mostHurt != null

/** How near a monster has to be to keep the party awake. */
private const val WITHIN_REACH = 1

/** How long a stretch of sleep is: what mending and hunger both come round on. */
const val HOURS_A_MENDED_POINT = 8

/** How long an empty stomach takes to cost a hit point. */
const val HOURS_A_STARVED_POINT = 24

/**
 * What an hour of rest counts as to a spell still running on the party, which
 * is far longer than any of them lasts: a single hour ends every one.
 */
val AN_HOUR_OF_REST = Ticks(TICKS_AN_HOUR)

/** The world as the spells on the party find it after [hours] of rest. */
fun GameState.spellsRunThroughARest(hours: Int): GameState =
    if (hours <= 0) this else spellsRunDown(Ticks(AN_HOUR_OF_REST.value * hours)).first
