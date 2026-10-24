package pl.pelotasplus.eyeofbeholder.data

import kotlinx.coroutines.runBlocking
import org.junit.experimental.categories.Category
import pl.pelotasplus.eyeofbeholder.NeedsGameData
import pl.pelotasplus.eyeofbeholder.data.model.Dice
import pl.pelotasplus.eyeofbeholder.data.model.GameState
import pl.pelotasplus.eyeofbeholder.data.model.HitPoints
import pl.pelotasplus.eyeofbeholder.data.model.ItemIndex
import pl.pelotasplus.eyeofbeholder.data.model.ItemTypes
import pl.pelotasplus.eyeofbeholder.data.model.Night
import pl.pelotasplus.eyeofbeholder.data.model.PartySlot
import pl.pelotasplus.eyeofbeholder.data.model.Rest
import pl.pelotasplus.eyeofbeholder.data.model.RestQuestion
import pl.pelotasplus.eyeofbeholder.data.model.Resting
import pl.pelotasplus.eyeofbeholder.data.model.Spell
import pl.pelotasplus.eyeofbeholder.data.model.SpellPages
import pl.pelotasplus.eyeofbeholder.data.model.SpellSlot
import pl.pelotasplus.eyeofbeholder.data.model.WatchingTheRest
import pl.pelotasplus.eyeofbeholder.data.model.aStretchSlept
import pl.pelotasplus.eyeofbeholder.data.model.night
import pl.pelotasplus.eyeofbeholder.data.repository.ItemTypesRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.OriginalSaveRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.ResourceRepositoryImpl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * What a rest does with spells, ten minutes at a time, on the quick start
 * party: Pericles a paladin, Wolfspirit a cleric, Laurann a mage.
 *
 * The step counts come from the rule — a spell on page `p` once a count that
 * starts at nothing and climbs one a step is past 48 + `p`, and back to 48
 * after each — and not from a run.
 */
@Category(NeedsGameData::class)
class WhatARestDoesWithSpellsTest {

    private val resources = ResourceRepositoryImpl()

    private val save = runBlocking {
        OriginalSaveRepositoryImpl(resources).loadOriginalSave(OriginalSaveRepositoryImpl.QUICK_START).getOrThrow()
    }

    private val types: ItemTypes = runBlocking { ItemTypesRepositoryImpl(resources).loadItemTypes().getOrThrow() }

    private val pericles = PartySlot(0)
    private val wolfspirit = PartySlot(2)
    private val laurann = PartySlot(3)

    private val noRolling = Dice { _, _, modifier -> modifier }

    /** The party unhurt, with nothing chosen but what [pages] put down. */
    private fun party(
        mage: SpellPages = SpellPages.NONE,
        cleric: SpellPages = SpellPages.NONE,
    ): GameState = GameState(party = save.standing, champions = save.party, items = save.items)
        .let { world ->
            world.champions.indices.fold(world) { w, slot ->
                w.withChampion(PartySlot(slot)) {
                    it.copy(
                        hitPoints = it.hitPoints.copy(current = it.hitPoints.max),
                        mageSpells = SpellPages.NONE,
                        clericSpells = SpellPages.NONE,
                    )
                }
            }
        }
        .withChampion(laurann) { it.copy(mageSpells = mage) }
        .withChampion(wolfspirit) { it.copy(clericSpells = cleric) }

    private fun pages(vararg places: Pair<Int, SpellSlot>) =
        places.fold(SpellPages.NONE) { pages, (at, place) -> pages.with(at, place) }

    /** [steps] steps of [night], and everything said along the way, numbered by the step it was said in. */
    private fun stepped(night: Night, steps: Int): Pair<Night, List<Pair<Int, String>>> {
        var now = night
        val said = mutableListOf<Pair<Int, String>>()
        repeat(steps) {
            val step = now.stepped(types, noRolling)
            now = step.night
            step.said.forEach { said += now.steps to it }
        }
        return now to said
    }

    // --- learning -----------------------------------------------------------

    @Test
    fun `the first spell of a rest comes after eight hours and ten minutes`() {
        val night = Night(party(mage = pages(0 to SpellSlot.Learning(Spell.MAGIC_MISSILE))))

        val (after, said) = stepped(night, 49)

        assertEquals(listOf(49 to "LAURANN memorized magic missile."), said)
        assertEquals(SpellSlot.Ready(Spell.MAGIC_MISSILE), after.world.champions[laurann.index].mageSpells.slots[0])
    }

    /** A first-level spell after another is one step; a third-level one, three. */
    @Test
    fun `each spell after the first waits a step per level`() {
        val cleric = pages(
            0 to SpellSlot.Learning(Spell.BLESS),
            1 to SpellSlot.Learning(Spell.CURE_LIGHT_WOUNDS),
            20 to SpellSlot.Learning(Spell.PRAYER),
        )

        val (_, said) = stepped(Night(party(cleric = cleric)), 60)

        assertEquals(
            listOf(
                49 to "WOLFSPIRIT gained bless.",
                50 to "WOLFSPIRIT gained cure light wounds.",
                53 to "WOLFSPIRIT gained prayer.",
            ),
            said,
        )
    }

    /**
     * A mage's page is counted six places at a time, so the seventh first-level
     * spell waits two steps as a second-level one would.
     */
    @Test
    fun `a mage's seventh spell on a page waits as if a level higher`() {
        val mage = pages(*Array(7) { it to SpellSlot.Learning(Spell.MAGIC_MISSILE) })

        val (_, said) = stepped(Night(party(mage = mage)), 60)

        assertEquals(listOf(49, 50, 51, 52, 53, 54, 56), said.map { it.first })
    }

    @Test
    fun `nothing is learned without a holy symbol or a book about them`() {
        val night = Night(
            party(mage = pages(0 to SpellSlot.Learning(Spell.MAGIC_MISSILE)))
                .withChampion(laurann) { it.copy(carrying = it.carrying.map { ItemIndex(ItemIndex.NOTHING) }) },
        )

        assertEquals(emptyList(), stepped(night, 60).second)
        assertTrue(night.spellsToLearn(types).not(), "a mage with no book counted as having spells to learn")
    }

    // --- healing ------------------------------------------------------------

    /**
     * A ready cure is cast on the most hurt as soon as the healers are let
     * heal, used up, and back to learn a full eight hours later — and cast
     * again the step it is learned, since healing follows learning.
     */
    @Test
    fun `a healer casts what cures they have and learns them again`() {
        val hurt = party(cleric = pages(0 to SpellSlot.Ready(Spell.CURE_SERIOUS_WOUNDS)))
            .withChampion(laurann) { it.copy(hitPoints = HitPoints(5, it.hitPoints.max)) }

        val (after, said) = stepped(Night(hurt, healersHeal = true), 50)

        assertEquals(
            listOf(
                1 to "WOLFSPIRIT casts healing on LAURANN.",
                50 to "WOLFSPIRIT gained cure serious wounds.",
                50 to "WOLFSPIRIT casts healing on LAURANN.",
            ),
            said,
        )
        // two dice of eight that roll nothing and the one over, twice, and
        // the point the eighth hour mends
        assertEquals(8, after.world.champions[laurann.index].hitPoints.current)
    }

    @Test
    fun `nobody heals unless the healers were let`() {
        val hurt = party(cleric = pages(0 to SpellSlot.Ready(Spell.CURE_SERIOUS_WOUNDS)))
            .withChampion(laurann) { it.copy(hitPoints = HitPoints(5, it.hitPoints.max)) }

        assertEquals(emptyList(), stepped(Night(hurt), 10).second)
    }

    // --- the stretch --------------------------------------------------------

    @Test
    fun `a paladin has lay on hands back every eight hours`() {
        val world = party().withChampion(pericles) {
            it.copy(clericSpells = pages(0 to SpellSlot.Learning(Spell.LAY_ON_HANDS)))
        }

        val after = world.aStretchSlept(8, types).first

        assertEquals(SpellSlot.Ready(Spell.LAY_ON_HANDS), after.champions[pericles.index].clericSpells.slots[0])
    }

    // --- the whole rest -----------------------------------------------------

    private class Watching(private val answers: Map<RestQuestion, Boolean>) : WatchingTheRest {
        val asked = mutableListOf<RestQuestion>()
        val said = mutableListOf<String>()

        override suspend fun asked(question: RestQuestion, hours: Int) =
            answers.getValue(question).also { asked += question }

        override suspend fun said(words: String) {
            said += words
        }

        override suspend fun anHourPassed(hours: Int) = Unit
        override suspend fun stepped(world: GameState) = Unit
    }

    private fun rest(world: GameState, watching: Watching) =
        runBlocking { Resting(kinds = emptyList(), types = types).night(world, walking = null, watching = watching, dice = noRolling) }

    /** Unhurt with a spell to learn: up once it is learned, at eight hours, and rested. */
    @Test
    fun `a rest lasts until the last spell is learned`() {
        val watching = Watching(emptyMap())

        val woke = assertIs<Rest.Woke>(rest(party(mage = pages(0 to SpellSlot.Learning(Spell.SHIELD))), watching))

        assertEquals(8, woke.hours)
        assertTrue(woke.fullyRested)
        assertEquals(listOf("LAURANN memorized shield."), watching.said)
        assertEquals(emptyList(), watching.asked)
    }

    /** Nothing to learn and somebody hurt: asked whether to rest until healed, and no ends it there. */
    @Test
    fun `a party with nothing to learn are asked about resting until healed`() {
        val hurt = party().withChampion(laurann) { it.copy(hitPoints = HitPoints(5, it.hitPoints.max)) }
        val watching = Watching(mapOf(RestQuestion.STILL_INJURED to false))

        val woke = assertIs<Rest.Woke>(rest(hurt, watching))

        assertEquals(listOf(RestQuestion.STILL_INJURED), watching.asked)
        assertEquals(0, woke.hours)
        assertTrue(!woke.fullyRested)
    }

    @Test
    fun `the healers are asked about first, before any of it passes`() {
        val hurt = party(cleric = pages(0 to SpellSlot.Ready(Spell.CURE_LIGHT_WOUNDS)))
            .withChampion(laurann) { it.copy(hitPoints = HitPoints(5, it.hitPoints.max)) }
        val watching = Watching(mapOf(RestQuestion.HEALERS to false, RestQuestion.STILL_INJURED to false))

        rest(hurt, watching)

        assertEquals(listOf(RestQuestion.HEALERS, RestQuestion.STILL_INJURED), watching.asked)
    }

    @Test
    fun `nobody hurt and nothing to learn is fully rested at once`() {
        val woke = assertIs<Rest.Woke>(rest(party(), Watching(emptyMap())))

        assertEquals(0, woke.hours)
        assertTrue(woke.fullyRested)
    }
}
