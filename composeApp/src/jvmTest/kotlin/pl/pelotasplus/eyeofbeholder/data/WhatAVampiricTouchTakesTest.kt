package pl.pelotasplus.eyeofbeholder.data

import kotlinx.coroutines.runBlocking
import org.junit.experimental.categories.Category
import pl.pelotasplus.eyeofbeholder.NeedsGameData
import pl.pelotasplus.eyeofbeholder.data.model.Abilities
import pl.pelotasplus.eyeofbeholder.data.model.Ability
import pl.pelotasplus.eyeofbeholder.data.model.ArmorClass
import pl.pelotasplus.eyeofbeholder.data.model.Blow
import pl.pelotasplus.eyeofbeholder.data.model.CarrySlot
import pl.pelotasplus.eyeofbeholder.data.model.ChargeGrownBy
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
import pl.pelotasplus.eyeofbeholder.data.model.TrackIndex
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
 * A vampiric touch, struck at level 5's clerics on 13x8 from 13x9.
 *
 * The third of the hands a spell charges, and the only one that gives
 * anything back: what its blow costs the creature it found goes to whoever
 * struck. It is otherwise a touch like a shocking grasp — gone into the
 * first blow that does harm, and the clock is time to find something to hit.
 *
 * Where it differs from both the others is in how the caster's level counts.
 * A grasp grows its flat part a point a level and a flame blade does not grow
 * at all; this grows its *dice*, one of six for every two levels.
 */
@Category(NeedsGameData::class)
class WhatAVampiricTouchTakesTest {

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

    // --- the spell -----------------------------------------------------------

    @Test
    fun `it lasts half a minute a level, on whoever read it`() {
        val lasts = assertIs<SpellLasts>(Spell.VAMPIRIC_TOUCH.lasts)

        assertEquals(SpellLasts(base = 0, perLevel = 1), lasts)
        assertEquals(Ticks(4914), lasts.castBySomeoneOfLevel(OFF_A_SCROLL))
        assertEquals(SettlesOn.WHOEVER_CAST_IT, Spell.VAMPIRIC_TOUCH.settlesOn)
        assertEquals(TrackIndex(102), Spell.VAMPIRIC_TOUCH.heardAs)
    }

    /** And shows its own thing in the hand, not either of the other two. */
    @Test
    fun `the three charged hands show three different things`() {
        val shown = listOf(Spell.SHOCKING_GRASP, Spell.VAMPIRIC_TOUCH, Spell.FLAME_BLADE)
            .map { it.chargesAHand?.looksLike }

        assertEquals(shown.toSet().size, shown.size, "two of them show the same thing")
        assertNotEquals(null, Spell.VAMPIRIC_TOUCH.chargesAHand?.looksLike)
    }

    // --- how the caster's level counts ---------------------------------------

    /**
     * A die of six for every two levels, which is the thing none of the other
     * two do: their dice are fixed and only the flat part moves.
     */
    @Test
    fun `its dice grow with the caster where the others' do not`() {
        assertEquals(ChargeGrownBy.A_DIE_EVERY_TWO_LEVELS, Spell.VAMPIRIC_TOUCH.chargesAHand?.grownBy)
        assertEquals(ChargeGrownBy.A_POINT_A_LEVEL, Spell.SHOCKING_GRASP.chargesAHand?.grownBy)
        assertEquals(ChargeGrownBy.NOTHING, Spell.FLAME_BLADE.chargesAHand?.grownBy)

        mapOf(3 to 1, 4 to 2, 5 to 2, OFF_A_SCROLL to 4, 12 to 6).forEach { (level, dice) ->
            assertEquals(
                DamageDice(times = dice, pips = 6, base = 0),
                world().drawn(level).running.chargeOn(caster, charged)?.strikesFor,
                "read by somebody of $level",
            )
        }
    }

    /** A d6 four times at its highest, off a scroll, and nothing for strength ten. */
    @Test
    fun `a blow with it strikes for its dice`() {
        val hit = assertIs<Blow.Hit>(swung(world().drawn()).blow)

        assertEquals(Damage(4 * 6), hit.damage)
    }

    // --- what it gives back --------------------------------------------------

    /** Every point the blow cost goes to whoever struck it. */
    @Test
    fun `the striker is given what the blow took`() {
        val before = world().drawn()
        val short = before.championIn(caster)!!.hitPoints.current

        val struck = swung(before)
        val hit = assertIs<Blow.Hit>(struck.blow)

        assertEquals(
            short + hit.damage.points,
            struck.world.championIn(caster)!!.hitPoints.current,
        )
    }

    /**
     * Over their own maximum, and nothing trims them back.
     *
     * Not a mending, which stops at whole: this is what the blow took off
     * something else, handed over whole — so a champion who was barely hurt
     * ends up carrying more than they can hold.
     */
    @Test
    fun `it carries them over their own maximum`() {
        val whole = world(hitPoints = HitPoints(FULL, FULL)).drawn()

        val struck = swung(whole)
        val hit = assertIs<Blow.Hit>(struck.blow)
        val after = struck.world.championIn(caster)!!

        assertTrue(hit.damage.points > 0)
        assertEquals(FULL + hit.damage.points, after.hitPoints.current)
        assertTrue(after.hitPoints.current > after.hitPoints.max, "it was trimmed back to whole")
    }

    /** A blow that costs nothing gives nothing, and keeps the charge. */
    @Test
    fun `a creature magic passes by gives it nothing`() {
        val warded = kinds.map { it.copy(immunities = MonsterImmunities(SHRUGS_OFF_MAGIC)) }
        val before = world().drawn()
        val short = before.championIn(caster)!!.hitPoints.current

        val struck = Fighting(itemTypes, warded, everyDieHighest).strike(before, caster, charged)

        assertIs<Blow.Missed>(struck.blow)
        assertEquals(short, struck.world.championIn(caster)!!.hitPoints.current)
        assertNotNull(struck.world.running.chargeOn(caster, charged), "the touch was spent on nothing")
    }

    /** And the other two touches give nothing, however much they take. */
    @Test
    fun `a shocking grasp takes without giving`() {
        val before = world().drawn(spell = Spell.SHOCKING_GRASP)
        val short = before.championIn(caster)!!.hitPoints.current

        val struck = swung(before)

        assertIs<Blow.Hit>(struck.blow)
        assertEquals(short, struck.world.championIn(caster)!!.hitPoints.current)
    }

    // --- and it is a touch, like the grasp -----------------------------------

    @Test
    fun `the blow that does harm spends it`() {
        val struck = swung(world().drawn())

        assertIs<Blow.Hit>(struck.blow)
        assertNull(struck.world.running.chargeOn(caster, charged), "the touch survived a hit")
        assertTrue(struck.world.handIsFree(caster, charged))
    }

    @Test
    fun `the hand holding it is empty and not free`() {
        val after = world().drawn()

        assertFalse(after.championIn(caster)!!.holding(charged).isSomething)
        assertFalse(after.handIsFree(caster, charged))
        assertTrue(after.handIsFree(caster, readFrom))
    }

    // --- the fixture ---------------------------------------------------------

    private fun GameState.drawn(
        casterLevel: Int = OFF_A_SCROLL,
        spell: Spell = Spell.VAMPIRIC_TOUCH,
    ) = assertNotNull(handCharged(caster, charged, spell, casterLevel))

    private fun swung(world: GameState) =
        Fighting(itemTypes, kinds, everyDieHighest).strike(world, caster, charged)

    private fun fighter(hitPoints: HitPoints) = Champion(
        name = "Anselm",
        portrait = PortraitId(0),
        abilities = Abilities(strength = Ability(10, 10), dexterity = Ability(10, 10)),
        hitPoints = hitPoints,
        armorClass = ArmorClass(10),
        food = Food(100),
        characterClass = CharacterClass.MAGE,
        levels = listOf(ClassLevel(9, XpPoints(0))),
        carrying = List(CarrySlot.ALL_OF_THEM) { ItemIndex(ItemIndex.NOTHING) },
        flags = ChampionFlags(1),
    )

    private fun world(hitPoints: HitPoints = HitPoints(HURT, FULL)) = GameState(
        party = PartyState(Location(13, 9), Direction.NORTH),
        champions = listOf(fighter(hitPoints), fighter(hitPoints)),
    ).arrivingAt(
        level = 5,
        places = level.monsterInstances,
        maz = level.subLevels[0].maz,
        kinds = kinds,
        dice = everyDieHighest,
    )

    private companion object {
        /** A scroll is always read at ninth-level strength. */
        const val OFF_A_SCROLL = 9

        const val FULL = 60
        const val HURT = 20

        /** The bit a creature sets to say magic does nothing to it. */
        const val SHRUGS_OFF_MAGIC = 0x10
    }
}
