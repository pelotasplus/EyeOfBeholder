package pl.pelotasplus.eyeofbeholder.data.model

/** What a rest stops to ask the player. */
enum class RestQuestion {
    /** Asked once, before any of it passes, when there is somebody to heal and somebody to heal them. */
    HEALERS,

    /** Asked once nothing is left to learn and somebody is still hurt. */
    STILL_INJURED,

    /** Asked when a stomach empties, or a day on an empty one costs a hit point. */
    STARVING,
}

/**
 * Whoever is watching a rest: asked its questions, told what happened, and
 * given each step's world as it comes, so that stopping the rest at any point
 * leaves the world as far on as it got.
 */
interface WatchingTheRest {
    suspend fun asked(question: RestQuestion, hours: Int): Boolean

    /** A spell learned, or a healing cast. */
    suspend fun said(words: String)

    suspend fun anHourPassed(hours: Int)

    suspend fun stepped(world: GameState)
}

/**
 * A rest under way, ten minutes at a time.
 *
 * Each champion keeps a count of steps that goes up by one every step and
 * is what spells are learned on. A spell still to learn on page `p` comes
 * once the count is past [LEARNING_FROM] + `p`, the count drops back to
 * [LEARNING_FROM] when it does, and the next one is a page's worth of steps
 * later. So the first spell of a rest takes eight hours and ten minutes, and
 * each one after it ten minutes more per level. Mage spells count their page
 * as six places to a page rather than ten: on a page of more than six spells
 * the seventh onwards wait as if they were a level higher. It is the rule as
 * played, and a test pins it.
 *
 * A healer casting a cure starts their count from nothing, and has to wait
 * the full eight hours to have it back.
 *
 * [mending] is who the healers heal, the most hurt as the rest begins, and
 * looked for again only every eight hours — so it is also what decides that
 * somebody is still hurt, and a party healed early still sleep out the
 * stretch.
 */
data class Night(
    val world: GameState,
    val steps: Int = 0,
    val counts: List<Int> = List(world.champions.size) { 0 },
    val healersHeal: Boolean = false,
    val untilHealed: Boolean = false,
    val mending: PartySlot? = world.mostHurt,
) {
    val hours: Int get() = steps / STEPS_AN_HOUR

    /** One step, with what was said in it and whether it brought on the starving question. */
    fun stepped(types: ItemTypes?, dice: Dice): Step {
        var night = copy(steps = steps + 1, counts = counts.map { it + 1 })
        val said = mutableListOf<String>()

        night = night.learning(types, said)
        if (healersHeal) night = night.healing(types, dice, said)

        var starving = false
        if (night.steps % STEPS_AN_HOUR == 0) {
            night = night.copy(world = night.world.anHourSlept())
            if (night.hours % HOURS_A_MENDED_POINT == 0) {
                val stretch = night.world.aStretchSlept(night.hours, types)
                starving = stretch.second
                night = night.copy(world = stretch.first, mending = stretch.first.mostHurt)
            }
        }
        return Step(night, said, starving)
    }

    private fun learning(types: ItemTypes?, said: MutableList<String>): Night {
        var world = world
        val counts = counts.toMutableList()

        world.champions.forEachIndexed { slot, who ->
            if (who.food.value <= 0 || !who.inTheParty || who.hitPoints.current <= 0 || who.petrified) return@forEachIndexed
            val whose = PartySlot(slot)

            if (world.carries(whose, ItemKind.HOLY_SYMBOL, types)) {
                who.clericSpells.learnedOne(counts[slot], CLERIC_PAGE)?.let { (pages, spell) ->
                    world = world.withChampion(whose) { it.copy(clericSpells = pages) }
                    counts[slot] = LEARNING_FROM
                    said += "${who.name} gained ${spell.calledIt}."
                }
            }
            if (world.carries(whose, ItemKind.SPELLBOOK, types)) {
                world.champions[slot].mageSpells.learnedOne(counts[slot], MAGE_PAGE)?.let { (pages, spell) ->
                    world = world.withChampion(whose) { it.copy(mageSpells = pages) }
                    counts[slot] = LEARNING_FROM
                    said += "${who.name} memorized ${spell.calledIt}."
                }
            }
        }
        return copy(world = world, counts = counts)
    }

    /**
     * Every healer casts every cure they have ready on [mending], each one
     * used up and back to be learned. A healer whose count is up and who has
     * a cure still coming stops the healing for everybody this step, so the
     * cures are cast together rather than one as each is learned.
     */
    private fun healing(types: ItemTypes?, dice: Dice, said: MutableList<String>): Night {
        val target = mending ?: return this
        var world = world
        val counts = counts.toMutableList()

        for (slot in world.champions.indices) {
            val healer = world.champions[slot]
            val whose = PartySlot(slot)
            if (!healer.isAHealer || !world.carries(whose, ItemKind.HOLY_SYMBOL, types)) continue
            if (counts[slot] >= LEARNING_FROM && healer.clericSpells.slots.any { it is SpellSlot.Learning && it.spell in CURES }) break

            healer.clericSpells.slots.forEachIndexed { at, place ->
                if (place !is SpellSlot.Ready || place.spell !in CURES) return@forEachIndexed
                val cure = (place.spell.laidOn as LaidOnAChampion.Mends).by
                val hurt = world.champions[target.index]

                world = world
                    .withChampion(whose) { it.copy(clericSpells = it.clericSpells.with(at, SpellSlot.Learning(place.spell))) }
                    .withChampion(target) {
                        val mended = it.hitPoints.current + cure.given(healer, hurt, dice)
                        it.copy(hitPoints = it.hitPoints.copy(current = mended.coerceAtMost(it.hitPoints.max)))
                    }
                counts[slot] = 0
                said += "${healer.name} casts healing on ${hurt.name}."
            }
        }
        return copy(world = world, counts = counts)
    }

    /** Whether anybody able to learn has a spell still to learn and what to learn it from. */
    fun spellsToLearn(types: ItemTypes?): Boolean = world.champions.withIndex().any { (slot, who) ->
        val whose = PartySlot(slot)
        who.inTheParty && !who.deadForGood && who.food.value > 0 && (
            (who.isAHealer && world.carries(whose, ItemKind.HOLY_SYMBOL, types) &&
                who.clericSpells.slots.any { it is SpellSlot.Learning }) ||
                (who.levelIn(CharacterClass.MAGE) != null && world.carries(whose, ItemKind.SPELLBOOK, types) &&
                    who.mageSpells.slots.any { it is SpellSlot.Learning })
            )
    }

    /** What one step came to. */
    data class Step(val night: Night, val said: List<String>, val starving: Boolean)

    companion object {
        const val STEPS_AN_HOUR = 6

        /** Where a champion's count stands once they have learned a spell. */
        const val LEARNING_FROM = 48

        private const val CLERIC_PAGE = 10
        private const val MAGE_PAGE = 6

        private val CURES = setOf(Spell.CURE_LIGHT_WOUNDS, Spell.CURE_SERIOUS_WOUNDS, Spell.CURE_CRITICAL_WOUNDS)

        /**
         * Whether a healer will be asked to heal over this rest: somebody hurt,
         * and a cleric or paladin standing, with a holy symbol and a cure
         * chosen, learned or not.
         */
        fun healersOffered(world: GameState, types: ItemTypes?): Boolean =
            world.mostHurt != null && world.champions.withIndex().any { (slot, who) ->
                who.inTheParty && who.hitPoints.current > 0 && !who.petrified && who.isAHealer &&
                    world.carries(PartySlot(slot), ItemKind.HOLY_SYMBOL, types) &&
                    who.clericSpells.slots.any { it.spell in CURES }
            }
    }
}

/**
 * A rest from lying down to getting up, step after step until there is
 * nothing more to be had from it, a question is answered no, or a monster
 * comes near. The watcher can also simply stop it, which leaves the world
 * where the last step put it.
 */
suspend fun Resting.night(
    world: GameState,
    walking: MonsterPathing?,
    watching: WatchingTheRest,
    dice: Dice = Dice.random,
): Rest {
    val begun = begin(world, walking)
    if (begun !is Rest.Slept) return begun

    var night = Night(begun.world)
    if (Night.healersOffered(night.world, types)) {
        night = night.copy(healersHeal = watching.asked(RestQuestion.HEALERS, 0))
    }

    while (true) {
        val step = night.stepped(types, dice)
        night = step.night
        watching.stepped(night.world)
        step.said.forEach { watching.said(it) }
        if (night.steps % Night.STEPS_AN_HOUR == 0) watching.anHourPassed(night.hours)

        if (step.starving && !watching.asked(RestQuestion.STARVING, night.hours)) break

        val learning = night.spellsToLearn(types)
        if (!learning && !night.untilHealed && night.mending != null) {
            if (!watching.asked(RestQuestion.STILL_INJURED, night.hours)) break
            night = night.copy(untilHealed = true)
        }

        when (val still = begin(night.world, walking)) {
            is Rest.SomethingIsNear -> {
                watching.stepped(still.world)
                return still
            }
            is Rest.Slept -> night = night.copy(world = still.world)
            Rest.NotHere, is Rest.Woke -> Unit
        }
        watching.stepped(night.world)

        if (!learning && ((!night.untilHealed && !night.healersHeal) || night.mending == null)) break
    }
    return Rest.Woke(night.world, night.hours, fullyRested = night.mending == null)
}

/** The most hurt of those a rest can mend, or nobody. */
val GameState.mostHurt: PartySlot?
    get() = champions.withIndex()
        .filter { (_, it) -> it.inTheParty && !it.deadForGood && it.hitPoints.current < it.hitPoints.max }
        .minByOrNull { (_, it) -> it.hitPoints.current }
        ?.let { PartySlot(it.index) }

/** The first spell still to learn, learned, if [count] has come far enough for its page. */
private fun SpellPages.learnedOne(count: Int, perPage: Int): Pair<SpellPages, Spell>? {
    val at = slots.indexOfFirst { it is SpellSlot.Learning }
    if (at < 0 || at / perPage + Night.LEARNING_FROM >= count) return null
    val spell = (slots[at] as SpellSlot.Learning).spell
    return with(at, SpellSlot.Ready(spell)) to spell
}

private val Champion.isAHealer: Boolean
    get() = levelIn(CharacterClass.CLERIC) != null || levelIn(CharacterClass.PALADIN) != null

private fun GameState.carries(whose: PartySlot, kind: ItemKind, types: ItemTypes?): Boolean {
    if (types == null) return false
    return champions[whose.index].carrying.any { held -> item(held)?.let { types.kindOf(it) == kind } == true }
}
