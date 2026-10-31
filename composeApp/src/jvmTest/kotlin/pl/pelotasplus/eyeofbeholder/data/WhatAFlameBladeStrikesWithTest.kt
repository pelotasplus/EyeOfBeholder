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
import pl.pelotasplus.eyeofbeholder.data.model.MonsterImmunities
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
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A flame blade, struck at level 5's clerics on 13x8 from 13x9.
 *
 * It puts a weapon into the hand it was not read from rather than a touch, and
 * the difference is the whole of the spell. It is the same weapon in a first
 * level cleric's hand as in a ninth's, and it is not spent: it strikes for as
 * long as the spell runs, so the clock is what the party are counting rather
 * than the next blow.
 *
 * [WhatAShockingGraspChargesTest] is the other half of the pair, and the one
 * that is a touch.
 */
@Category(NeedsGameData::class)
class WhatAFlameBladeStrikesWithTest {

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

    private fun GameState.drawn(casterLevel: Int = OFF_A_SCROLL) = assertNotNull(
        handCharged(caster, charged, Spell.FLAME_BLADE, casterLevel),
    )

    private fun swung(world: GameState) =
        Fighting(itemTypes, kinds, everyDieHighest).strike(world, caster, charged)

    // --- the spell -----------------------------------------------------------

    /**
     * Four half-minutes and another for every level of the caster, which is
     * the only thing about the spell their level touches. Data.
     */
    @Test
    fun `it lasts two minutes and a half-minute a level`() {
        val lasts = assertIs<SpellLasts>(Spell.FLAME_BLADE.lasts)

        assertEquals(SpellLasts(base = 4, perLevel = 1), lasts)
        assertEquals(Ticks(2730), lasts.castBySomeoneOfLevel(1))
        assertEquals(Ticks(7098), lasts.castBySomeoneOfLevel(OFF_A_SCROLL))
    }

    @Test
    fun `it is the caster's own and draws a blade of a d4 and four`() {
        assertEquals(SettlesOn.WHOEVER_CAST_IT, Spell.FLAME_BLADE.settlesOn)
        assertEquals(
            DamageDice(times = 1, pips = 4, base = 4),
            Spell.FLAME_BLADE.chargesAHand?.strikesFor,
        )
    }

    /** No spark over anybody's box: the blade appearing in the hand is the sign. */
    @Test
    fun `nothing is shown over the party when it is read`() {
        assertFalse(Spell.FLAME_BLADE.throwsSparks)
        assertFalse(Spell.FLAME_BLADE.sparksOverTheParty)
        assertFalse(Spell.FLAME_BLADE.sparksOverTheCaster)
    }

    /** And it shows in the hand as itself, not as the other charge does. */
    @Test
    fun `it shows a different thing in the hand from a grasp`() {
        assertNotEquals(
            Spell.SHOCKING_GRASP.chargesAHand?.looksLike,
            Spell.FLAME_BLADE.chargesAHand?.looksLike,
        )
        assertEquals(
            Spell.FLAME_BLADE.chargesAHand?.looksLike,
            world().drawn().running.chargeOn(caster, charged)?.looksLike,
        )
    }

    // --- the hand it is in ---------------------------------------------------

    /**
     * The same blade whoever read it. The level goes into how long it is held
     * and not into what it strikes for, which is the one spell where those
     * come apart.
     */
    @Test
    fun `it is the same blade at first level as at ninth`() {
        val weak = world().drawn(casterLevel = 1)
        val strong = world().drawn(casterLevel = OFF_A_SCROLL)

        assertEquals(DamageDice(times = 1, pips = 4, base = 4), weak.running.chargeOn(caster, charged)?.strikesFor)
        assertEquals(
            weak.running.chargeOn(caster, charged)?.strikesFor,
            strong.running.chargeOn(caster, charged)?.strikesFor,
        )
    }

    @Test
    fun `the hand holding it is empty and not free`() {
        val after = world().drawn()

        assertFalse(after.championIn(caster)!!.holding(charged).isSomething, "something was put in it")
        assertFalse(after.handIsFree(caster, charged))
        assertTrue(after.handIsFree(caster, readFrom))
    }

    /** One of them at a time, as the other charge is one at a time. */
    @Test
    fun `a second blade on the same champion is refused`() {
        assertNull(world().drawn().handCharged(caster, readFrom, Spell.FLAME_BLADE, OFF_A_SCROLL))
    }

    // --- striking with it ----------------------------------------------------

    /** A d4 at its highest and the flat four, and nothing for strength ten. */
    @Test
    fun `a blow with it strikes for the blade`() {
        val hit = assertIs<Blow.Hit>(swung(world().drawn()).blow)

        assertEquals(Damage(4 + 4), hit.damage)
    }

    /**
     * And goes on striking for it. This is what the spell is: a touch would be
     * gone into that first blow and leave a fist behind it.
     */
    @Test
    fun `a blow that does harm keeps the blade`() {
        val struck = swung(world().drawn())

        assertIs<Blow.Hit>(struck.blow)
        assertNotNull(struck.world.running.chargeOn(caster, charged), "the blade was spent on one blow")
        assertFalse(struck.world.handIsFree(caster, charged))

        // the hand's rest after the first blow, waited out
        val again = assertIs<Blow.Hit>(swung(struck.world.copy(recovering = emptyList())).blow)
        assertEquals(Damage(4 + 4), again.damage, "the second blow should be the blade's too")
    }

    /**
     * It is magic however it looks, so a creature that magic passes by takes
     * nothing from it — and the blade is still in the hand afterwards, there
     * being nothing to spend.
     */
    @Test
    fun `a creature magic passes by takes nothing from it`() {
        val warded = kinds.map { it.copy(immunities = MonsterImmunities(SHRUGS_OFF_MAGIC)) }

        val struck = Fighting(itemTypes, warded, everyDieHighest).strike(world().drawn(), caster, charged)

        assertIs<Blow.Missed>(struck.blow)
        assertNotNull(struck.world.running.chargeOn(caster, charged))
    }

    // --- putting it out ------------------------------------------------------

    /** It goes with the spell's clock, leaving the hand empty and free. */
    @Test
    fun `the blade ends with the spell`() {
        val (after, ended) = world().drawn().spellsRunDown(Ticks(7098))

        assertEquals(listOf(Spell.FLAME_BLADE), ended.map { it.spell })
        assertNull(after.running.chargeOn(caster, charged))
        assertTrue(after.handIsFree(caster, charged))
    }

    /** Which is the only thing that ends it, however long the party swing. */
    @Test
    fun `it stands to its last tick, blows and all`() {
        val struck = swung(world().drawn()).world

        assertNotNull(struck.spellsRunDown(Ticks(7097)).first.running.chargeOn(caster, charged))
        assertNull(struck.spellsRunDown(Ticks(7098)).first.running.chargeOn(caster, charged))
    }

    private companion object {
        /** A scroll is always read at ninth-level strength. */
        const val OFF_A_SCROLL = 9

        /** The bit a creature sets to say magic does nothing to it. */
        const val SHRUGS_OFF_MAGIC = 0x10
    }
}
