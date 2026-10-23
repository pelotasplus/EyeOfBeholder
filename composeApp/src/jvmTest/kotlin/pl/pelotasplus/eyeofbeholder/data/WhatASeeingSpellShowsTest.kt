package pl.pelotasplus.eyeofbeholder.data

import kotlinx.coroutines.runBlocking
import org.junit.experimental.categories.Category
import pl.pelotasplus.eyeofbeholder.NeedsGameData
import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.GameState
import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.MonsterProperty
import pl.pelotasplus.eyeofbeholder.data.model.PartySlot
import pl.pelotasplus.eyeofbeholder.data.model.PartyState
import pl.pelotasplus.eyeofbeholder.data.model.SettlesOn
import pl.pelotasplus.eyeofbeholder.data.model.Spell
import pl.pelotasplus.eyeofbeholder.data.model.SpellLasts
import pl.pelotasplus.eyeofbeholder.data.model.Ticks
import pl.pelotasplus.eyeofbeholder.data.model.inSight
import pl.pelotasplus.eyeofbeholder.data.repository.CpsRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.DecRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.InfRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.MazRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.PalRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.ResourceRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.VcnRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.VmpRepositoryImpl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Detect invisibility and the two true seeings: what lets a party see a
 * monster of a kind nobody otherwise can.
 *
 * No kind on any of the sixteen floors is hidden, so the mark is put on the
 * fifth floor's own clerics by hand. What is under test is the rule — the
 * game's data never asks it.
 */
@Category(NeedsGameData::class)
class WhatASeeingSpellShowsTest {

    private val level = runBlocking {
        val resources = ResourceRepositoryImpl()
        InfRepositoryImpl(
            resourceRepository = resources,
            mazRepository = MazRepositoryImpl(resources),
            vmpRepository = VmpRepositoryImpl(resources),
            vcnRepository = VcnRepositoryImpl(resources),
            palRepository = PalRepositoryImpl(resources),
            cpsRepository = CpsRepositoryImpl(resources),
            decRepository = DecRepositoryImpl(resources),
        ).loadInf("LEVEL5.INF").getOrThrow()
    }

    private val kinds: List<MonsterProperty> get() = level.subLevels.flatMap { it.monsters }

    private val hiddenKinds: List<MonsterProperty>
        get() = kinds.map { it.copy(capsFlags = it.capsFlags or A_HIDDEN_KIND) }

    private val monsters get() = level.monsterInstances

    private fun world() = GameState(party = PartyState(Location(13, 9), Direction.NORTH))

    private fun GameState.under(spell: Spell): GameState = assertNotNull(
        spellBegunWhereItSettles(spell, PartySlot(0), casterLevel = OFF_A_SCROLL),
        "${spell.calledIt} would not begin",
    )

    // --- the three spells ----------------------------------------------------

    /** Five half-minutes a level of its caster: twenty-two and a half minutes off a scroll. Data. */
    @Test
    fun `detect invisibility lasts two and a half minutes for every level`() {
        val lasts = assertIs<SpellLasts>(Spell.DETECT_INVISIBILITY.lasts)

        assertEquals(SpellLasts(base = 0, perLevel = 5), lasts)
        assertEquals(Ticks(24570), lasts.castBySomeoneOfLevel(OFF_A_SCROLL))
    }

    /** Half a minute a level, the mage's and the cleric's alike. Data. */
    @Test
    fun `true seeing lasts half a minute for every level, whoever casts it`() {
        listOf(Spell.TRUE_SEEING, Spell.A_CLERICS_TRUE_SEEING).forEach { spell ->
            val lasts = assertIs<SpellLasts>(spell.lasts)

            assertEquals(SpellLasts(base = 0, perLevel = 1), lasts, spell.name)
            assertEquals(Ticks(4914), lasts.castBySomeoneOfLevel(OFF_A_SCROLL), spell.name)
        }
    }

    /** Over the party as one, asking nobody, and sparking over every box. */
    @Test
    fun `all three are cast over the whole party`() {
        SEEING.forEach { spell ->
            assertEquals(SettlesOn.THE_PARTY, spell.settlesOn, spell.name)
            assertTrue(spell.sparksOverTheParty, spell.name)
        }
    }

    // --- what the party can see ----------------------------------------------

    @Test
    fun `any one of the three lets the party see the hidden`() {
        assertFalse(world().seesTheHidden, "a party under nothing saw the hidden")

        SEEING.forEach { spell ->
            assertTrue(world().under(spell).seesTheHidden, spell.name)
        }
    }

    /** And it goes when its clock does. */
    @Test
    fun `the seeing ends with the spell`() {
        val seeing = world().under(Spell.TRUE_SEEING)

        val (after, ended) = seeing.spellsRunDown(Ticks(4914))

        assertEquals(listOf(Spell.TRUE_SEEING), ended.map { it.spell })
        assertFalse(after.seesTheHidden)
    }

    /**
     * Being hidden is not seeing. A party with one of their own under an
     * invisibility can see that champion perfectly well and see nothing
     * else they could not before.
     */
    @Test
    fun `hiding a champion does not let the party see hidden monsters`() {
        val hidden = assertNotNull(
            world().spellBegunOn(Spell.INVISIBILITY, PartySlot(0), PartySlot(0), OFF_A_SCROLL),
        )

        assertFalse(hidden.seesTheHidden)
    }

    // --- what is drawn -------------------------------------------------------

    /** A floor with no hidden kind is drawn as it stands, seeing spell or not. */
    @Test
    fun `nothing of an ordinary kind is ever left out`() {
        assertEquals(monsters, monsters.inSight(kinds, partySeesTheHidden = false))
    }

    @Test
    fun `a hidden kind is left out with nothing to see it by`() {
        assertTrue(monsters.isNotEmpty(), "the floor has nobody to hide")

        assertEquals(emptyList(), monsters.inSight(hiddenKinds, partySeesTheHidden = false))
    }

    @Test
    fun `and drawn like anything else once the party can see it`() {
        assertEquals(monsters, monsters.inSight(hiddenKinds, partySeesTheHidden = true))
    }

    /**
     * The flash of a blow shows it without a spell: a party swinging into an
     * empty-looking square learns something was there by watching it go white.
     */
    @Test
    fun `a hidden monster shows for the moment it is struck`() {
        val struck = monsters.first()
        val world = monsters.map { if (it.index == struck.index) it.copy(struck = true) else it }

        assertEquals(
            listOf(struck.index),
            world.inSight(hiddenKinds, partySeesTheHidden = false).map { it.index },
        )
    }

    private companion object {
        /** The capability bit that marks a kind as one nobody can see. */
        const val A_HIDDEN_KIND = 0x100

        /** A scroll is always read at ninth-level strength. */
        const val OFF_A_SCROLL = 9

        val SEEING = listOf(Spell.DETECT_INVISIBILITY, Spell.TRUE_SEEING, Spell.A_CLERICS_TRUE_SEEING)
    }
}
