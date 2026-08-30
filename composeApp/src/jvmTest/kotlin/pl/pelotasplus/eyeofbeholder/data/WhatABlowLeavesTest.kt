package pl.pelotasplus.eyeofbeholder.data

import kotlinx.coroutines.runBlocking
import org.junit.experimental.categories.Category
import pl.pelotasplus.eyeofbeholder.NeedsGameData
import pl.pelotasplus.eyeofbeholder.data.model.Abilities
import pl.pelotasplus.eyeofbeholder.data.model.Ability
import pl.pelotasplus.eyeofbeholder.data.model.ArmorClass
import pl.pelotasplus.eyeofbeholder.data.model.CarrySlot
import pl.pelotasplus.eyeofbeholder.data.model.Champion
import pl.pelotasplus.eyeofbeholder.data.model.ChampionFlags
import pl.pelotasplus.eyeofbeholder.data.model.CharacterClass
import pl.pelotasplus.eyeofbeholder.data.model.ClassLevel
import pl.pelotasplus.eyeofbeholder.data.model.Dice
import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.Food
import pl.pelotasplus.eyeofbeholder.data.model.GameState
import pl.pelotasplus.eyeofbeholder.data.model.HitPoints
import pl.pelotasplus.eyeofbeholder.data.model.Inf
import pl.pelotasplus.eyeofbeholder.data.model.ItemIndex
import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.PartySlot
import pl.pelotasplus.eyeofbeholder.data.model.PartyState
import pl.pelotasplus.eyeofbeholder.data.model.PortraitId
import pl.pelotasplus.eyeofbeholder.data.model.Race
import pl.pelotasplus.eyeofbeholder.data.model.Ticks
import pl.pelotasplus.eyeofbeholder.data.model.WhatABlowLeaves
import pl.pelotasplus.eyeofbeholder.data.model.XpPoints
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What a monster's blow leaves on a champion besides the wound.
 *
 * Which monsters do it is read off their own table rather than written here:
 * the mantis warriors on the seventh floor hold a champion where they stand,
 * the basilisk on the eleventh turns them to stone, and the spider on the
 * first only poisons.
 */
@Category(NeedsGameData::class)
class WhatABlowLeavesTest {

    private val resources = ResourceRepositoryImpl()

    private fun load(name: String): Inf = runBlocking {
        InfRepositoryImpl(
            resourceRepository = resources,
            mazRepository = MazRepositoryImpl(resources),
            vmpRepository = VmpRepositoryImpl(resources),
            vcnRepository = VcnRepositoryImpl(resources),
            palRepository = PalRepositoryImpl(resources),
            cpsRepository = CpsRepositoryImpl(resources),
            decRepository = DecRepositoryImpl(resources),
        ).loadInf(name).getOrThrow()
    }

    private fun champion(hitPoints: Int = 40) = Champion(
        name = "Anselm",
        portrait = PortraitId(0),
        abilities = Abilities(
            strength = Ability(10, 10),
            dexterity = Ability(10, 10),
            constitution = Ability(10, 10),
        ),
        hitPoints = HitPoints(hitPoints, 40),
        armorClass = ArmorClass(10),
        food = Food(100),
        race = Race.HUMAN,
        characterClass = CharacterClass.FIGHTER,
        levels = listOf(ClassLevel(1, XpPoints(0))),
        carrying = List(CarrySlot.ALL_OF_THEM) { ItemIndex(ItemIndex.NOTHING) },
        flags = ChampionFlags(1),
    )

    private fun world(party: List<Champion> = List(6) { champion() }) = GameState(
        party = PartyState(Location(1, 1), Direction.NORTH),
        champions = party,
    )

    /** Every die its lowest, so a d20 is one and no throw is ever made. */
    private val neverSaves = Dice { times, _, modifier -> times + modifier }

    /** And its highest, so a d20 is twenty and every throw is made. */
    private val alwaysSaves = Dice { times, pips, modifier -> times * pips + modifier }

    private val first = PartySlot(0)

    // --- who does what, off the game's own monster table ---------------------

    @Test
    fun `the mantis warriors hold a champion rather than turning them to stone`() {
        val mantis = load("LEVEL7.INF").subLevels[0].monsters.first()

        assertEquals(listOf(WhatABlowLeaves.PARALYSIS), mantis.whatItsBlowLeaves)
    }

    @Test
    fun `the basilisk is what turns them to stone`() {
        val basilisk = load("LEVEL11.INF").subLevels[0].monsters[1]

        assertEquals(listOf(WhatABlowLeaves.PETRIFICATION), basilisk.whatItsBlowLeaves)
    }

    /** And one monster can leave two things, which the wasps do. */
    @Test
    fun `a wasp both poisons and holds`() {
        val wasp = load("LEVEL7.INF").subLevels[1].monsters.first()

        assertEquals(
            listOf(WhatABlowLeaves.POISON, WhatABlowLeaves.PARALYSIS),
            wasp.whatItsBlowLeaves,
        )
    }

    @Test
    fun `the spider only poisons`() {
        val spider = load("LEVEL1.INF").subLevels[1].monsters.first()

        assertEquals(listOf(WhatABlowLeaves.POISON), spider.whatItsBlowLeaves)
    }

    // --- what taking one does ------------------------------------------------

    @Test
    fun `a throw that is made leaves nothing`() {
        assertNull(world().championLeftWith(first, WhatABlowLeaves.PARALYSIS, alwaysSaves))
    }

    @Test
    fun `and one that is missed leaves the champion held`() {
        val held = world().championLeftWith(first, WhatABlowLeaves.PARALYSIS, neverSaves)

        assertTrue(held!!.champions[0].paralysed)
        assertFalse(held.champions[0].canAct, "somebody held cannot swing")
    }

    /** Nothing is taken twice, so a second grip is not a second clock. */
    @Test
    fun `what is already there is not left again`() {
        val held = world().championLeftWith(first, WhatABlowLeaves.PARALYSIS, neverSaves)!!

        assertNull(held.championLeftWith(first, WhatABlowLeaves.PARALYSIS, neverSaves))
    }

    /** There has to be somebody there to hold. */
    @Test
    fun `an empty place takes nothing`() {
        val nobody = world(List(6) { Champion.NOBODY })

        assertNull(nobody.championLeftWith(first, WhatABlowLeaves.PARALYSIS, neverSaves))
    }

    @Test
    fun `and neither does somebody past raising`() {
        val gone = world(List(6) { champion(hitPoints = Champion.BEYOND_RAISING) })

        assertNull(gone.championLeftWith(first, WhatABlowLeaves.PARALYSIS, neverSaves))
    }

    /**
     * Stone takes everything else with it: a poisoned champion turned to
     * stone comes out of it stone and nothing else.
     */
    @Test
    fun `being turned to stone wipes what else was wrong`() {
        val poisoned = world()
            .championLeftWith(first, WhatABlowLeaves.POISON, neverSaves)!!
            .championLeftWith(first, WhatABlowLeaves.PARALYSIS, neverSaves)!!

        val stone = poisoned.championLeftWith(first, WhatABlowLeaves.PETRIFICATION, neverSaves)!!

        assertTrue(stone.champions[0].petrified)
        assertFalse(stone.champions[0].poisoned)
        assertFalse(stone.champions[0].paralysed)
        assertTrue(stone.champions[0].inTheParty, "and they keep their place")
    }

    // --- letting go ----------------------------------------------------------

    @Test
    fun `a grip lets go when its time is up`() {
        var world = world().championLeftWith(first, WhatABlowLeaves.PARALYSIS, neverSaves)!!
        var freed = emptyList<PartySlot>()

        repeat(TICKS_ENOUGH) {
            world.gripsStepped().let { (loosened, letGo) ->
                world = loosened
                if (letGo.isNotEmpty()) freed = letGo
            }
        }

        assertEquals(listOf(first), freed)
        assertFalse(world.champions[0].paralysed)
        assertEquals(emptyList(), world.holding)
    }

    @Test
    fun `and holds until then`() {
        val held = world().championLeftWith(first, WhatABlowLeaves.PARALYSIS, neverSaves)!!

        assertTrue(held.gripsStepped().first.champions[0].paralysed)
    }

    /** Neither venom nor stone is on any clock, so neither goes by itself. */
    @Test
    fun `poison and stone wait for a cure`() {
        listOf(WhatABlowLeaves.POISON, WhatABlowLeaves.PETRIFICATION).forEach { what ->
            val left = world().championLeftWith(first, what, neverSaves)!!

            assertEquals(emptyList(), left.holding, "$what should be on no clock")
        }
    }

    private companion object {
        /** Long enough for the longest grip there is, and then some. */
        val TICKS_ENOUGH = WhatABlowLeaves.PARALYSIS.holdsFor!!.value / Ticks(2).value + 2
    }
}
