package pl.pelotasplus.eyeofbeholder.data

import kotlinx.coroutines.runBlocking
import org.junit.experimental.categories.Category
import pl.pelotasplus.eyeofbeholder.NeedsGameData
import pl.pelotasplus.eyeofbeholder.data.model.Abilities
import pl.pelotasplus.eyeofbeholder.data.model.Ability
import pl.pelotasplus.eyeofbeholder.data.model.ArmorClass
import pl.pelotasplus.eyeofbeholder.data.model.Blow
import pl.pelotasplus.eyeofbeholder.data.model.CarrySlot
import pl.pelotasplus.eyeofbeholder.data.model.Champion
import pl.pelotasplus.eyeofbeholder.data.model.ChampionFlags
import pl.pelotasplus.eyeofbeholder.data.model.CharacterClass
import pl.pelotasplus.eyeofbeholder.data.model.ClassLevel
import pl.pelotasplus.eyeofbeholder.data.model.Damage
import pl.pelotasplus.eyeofbeholder.data.model.DamageDice
import pl.pelotasplus.eyeofbeholder.data.model.Dice
import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.Fighting
import pl.pelotasplus.eyeofbeholder.data.model.Food
import pl.pelotasplus.eyeofbeholder.data.model.GameState
import pl.pelotasplus.eyeofbeholder.data.model.HitPoints
import pl.pelotasplus.eyeofbeholder.data.model.Inf
import pl.pelotasplus.eyeofbeholder.data.model.ItemIndex
import pl.pelotasplus.eyeofbeholder.data.model.ItemTypes
import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.PartySlot
import pl.pelotasplus.eyeofbeholder.data.model.PartyState
import pl.pelotasplus.eyeofbeholder.data.model.PortraitId
import pl.pelotasplus.eyeofbeholder.data.model.SettlesOn
import pl.pelotasplus.eyeofbeholder.data.model.Spell
import pl.pelotasplus.eyeofbeholder.data.model.SpellLasts
import pl.pelotasplus.eyeofbeholder.data.model.Ticks
import pl.pelotasplus.eyeofbeholder.data.model.XpPoints
import pl.pelotasplus.eyeofbeholder.data.repository.CpsRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.DecRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.InfRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.ItemTypesRepositoryImpl
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Shocking grasp, struck at level 5's clerics on 13x8 from 13x9.
 *
 * The spell charges the hand it was not read from: that hand stays empty, and
 * its next blow that does harm strikes for a d8 and the caster's level and
 * spends the charge. A miss, or a creature quick enough to take nothing,
 * leaves it charged for another try until the spell runs out.
 */
@Category(NeedsGameData::class)
class WhatAShockingGraspChargesTest {

    private val resources = ResourceRepositoryImpl()

    private val level: Inf = runBlocking {
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

    private val itemTypes: ItemTypes = runBlocking {
        ItemTypesRepositoryImpl(resources).loadItemTypes().getOrThrow()
    }

    private val kinds get() = level.subLevels[0].monsters

    private val caster = PartySlot(0)
    private val readFrom = CarrySlot(0)
    private val charged = CarrySlot(1)

    private val everyDieHighest = Dice { times, pips, modifier -> times * pips + modifier }

    private fun fighter() = Champion(
        name = "Anselm",
        portrait = PortraitId(0),
        abilities = Abilities(strength = Ability(10, 10), dexterity = Ability(10, 10)),
        hitPoints = HitPoints(40, 40),
        armorClass = ArmorClass(10),
        food = Food(100),
        characterClass = CharacterClass.FIGHTER,
        levels = listOf(ClassLevel(1, XpPoints(0))),
        carrying = List(CarrySlot.ALL_OF_THEM) { ItemIndex(ItemIndex.NOTHING) },
        flags = ChampionFlags(1),
    )

    private fun world() = GameState(
        party = PartyState(Location(13, 9), Direction.NORTH),
        champions = listOf(fighter(), fighter()),
    ).arrivingAt(
        level = 5,
        places = level.monsterInstances,
        maz = level.subLevels[0].maz,
        kinds = kinds,
        dice = everyDieHighest,
    )

    private fun GameState.grasping() = assertNotNull(
        handCharged(caster, charged, Spell.SHOCKING_GRASP, casterLevel = OFF_A_SCROLL),
    )

    private fun swung(world: GameState) =
        Fighting(itemTypes, kinds, everyDieHighest).strike(world, caster, charged)

    // --- the spell -----------------------------------------------------------

    /** Half a minute a level of its caster: four and a half off a scroll. Data. */
    @Test
    fun `it lasts half a minute a level`() {
        val lasts = assertIs<SpellLasts>(Spell.SHOCKING_GRASP.lasts)

        assertEquals(SpellLasts(base = 0, perLevel = 1), lasts)
        assertEquals(Ticks(4914), lasts.castBySomeoneOfLevel(OFF_A_SCROLL))
    }

    /**
     * On the caster, asking nobody — and not among the spells whose casting
     * gives a hiding caster away: the blows the hand strikes do that.
     */
    @Test
    fun `it is the caster's own and charges a d8`() {
        assertEquals(SettlesOn.WHOEVER_CAST_IT, Spell.SHOCKING_GRASP.settlesOn)
        assertEquals(
            DamageDice(times = 1, pips = 8, base = 0),
            Spell.SHOCKING_GRASP.chargesAHand?.strikesFor,
        )
        assertFalse(Spell.SHOCKING_GRASP.givesTheCasterAway)
    }

    // --- the charged hand -------------------------------------------------------

    /** The hand charged, with the caster's level counted into the charge. */
    @Test
    fun `it charges the one hand with a d8 and the caster's level`() {
        val after = world().grasping()

        assertEquals(DamageDice(times = 1, pips = 8, base = OFF_A_SCROLL), after.running.chargeOn(caster, charged)?.strikesFor)
        assertNull(after.running.chargeOn(caster, readFrom), "the other hand was charged too")
        assertTrue(after.running.hasAChargedHand(caster))
    }

    /** Empty, and not free: nothing can go into it while the charge is there. */
    @Test
    fun `a charged hand is empty and not free`() {
        val after = world().grasping()

        assertFalse(after.championIn(caster)!!.holding(charged).isSomething, "something was put in it")
        assertFalse(after.handIsFree(caster, charged))
        assertTrue(after.handIsFree(caster, readFrom))
    }

    @Test
    fun `a second charge on the same champion is refused`() {
        assertNull(world().grasping().handCharged(caster, readFrom, Spell.SHOCKING_GRASP, OFF_A_SCROLL))
    }

    // --- striking with it -------------------------------------------------------

    /** A d8 at its highest, the scroll's nine, and nothing for strength ten. */
    @Test
    fun `a blow with it strikes for the charge`() {
        val struck = swung(world().grasping())

        val hit = assertIs<Blow.Hit>(struck.blow)
        assertEquals(Damage(8 + OFF_A_SCROLL), hit.damage)
    }

    /** The same hand uncharged is a fist: a d2. */
    @Test
    fun `the same hand uncharged is only a fist`() {
        val hit = assertIs<Blow.Hit>(swung(world()).blow)

        assertEquals(Damage(2), hit.damage)
    }

    /**
     * A single touch: the blow that does harm takes the charge with it, and
     * the hand is an empty hand again — a fist next time.
     */
    @Test
    fun `the first blow that does harm spends the charge`() {
        val struck = swung(world().grasping())

        assertIs<Blow.Hit>(struck.blow)
        assertNull(struck.world.running.chargeOn(caster, charged), "the charge survived a hit")
        assertTrue(struck.world.handIsFree(caster, charged))

        // the hand's rest after the first blow, waited out
        val again = assertIs<Blow.Hit>(swung(struck.world.copy(recovering = emptyList())).blow)
        assertEquals(Damage(2), again.damage, "the next blow should be a fist's")
    }

    /** A miss keeps it, for another try. */
    @Test
    fun `a miss keeps the charge`() {
        val alwaysOne = Dice { times, _, modifier -> times + modifier }

        val struck = Fighting(itemTypes, kinds, alwaysOne).strike(world().grasping(), caster, charged)

        assertIs<Blow.Missed>(struck.blow)
        assertNotNull(struck.world.running.chargeOn(caster, charged))
    }

    /**
     * A creature quick enough is out of the way of the charge itself: the blow
     * finds it, takes nothing off it, and is reported as the miss it comes to —
     * so the charge is still there for the next one.
     */
    @Test
    fun `a quick enough creature takes nothing, and the charge is kept`() {
        val quick = kinds.map { it.copy(dmgModifierEvade = A_WISPS_EVADE) }
        val dodging = Dice { times, pips, modifier ->
            if (pips == 100) 1 else times * pips + modifier
        }

        val struck = Fighting(itemTypes, quick, dodging).strike(world().grasping(), caster, charged)

        assertIs<Blow.Missed>(struck.blow)
        assertNotNull(struck.world.running.chargeOn(caster, charged))
    }

    /**
     * Spent, the spell still runs to its own end — that end is still told —
     * and casting it again starts a fresh charge rather than being refused.
     */
    @Test
    fun `a spent charge runs on to its end, and may be cast again`() {
        val spent = swung(world().grasping()).world

        assertTrue(spent.running.all.any { it.spell == Spell.SHOCKING_GRASP }, "the spell stopped running")
        assertFalse(spent.running.hasAChargedHand(caster))

        assertNotNull(
            spent.handCharged(caster, charged, Spell.SHOCKING_GRASP, OFF_A_SCROLL),
            "a fresh grasp was refused over a spent one",
        )
    }

    /** And it goes with the spell's clock, leaving the hand empty and free. */
    @Test
    fun `the charge ends with the spell`() {
        val (after, ended) = world().grasping().spellsRunDown(Ticks(4914))

        assertEquals(listOf(Spell.SHOCKING_GRASP), ended.map { it.spell })
        assertNull(after.running.chargeOn(caster, charged))
        assertTrue(after.handIsFree(caster, charged))
    }

    private companion object {
        /** A scroll is always read at ninth-level strength. */
        const val OFF_A_SCROLL = 9

        /** How quick the ninth floor's will-o'-wisps are, out of a hundred. */
        const val A_WISPS_EVADE = 99
    }
}
