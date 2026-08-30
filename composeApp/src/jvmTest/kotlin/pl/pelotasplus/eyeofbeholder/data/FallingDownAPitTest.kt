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
import pl.pelotasplus.eyeofbeholder.data.model.Item
import pl.pelotasplus.eyeofbeholder.data.model.ItemIndex
import pl.pelotasplus.eyeofbeholder.data.model.ItemKind
import pl.pelotasplus.eyeofbeholder.data.model.ItemTypes
import pl.pelotasplus.eyeofbeholder.data.model.LevelScriptRunner
import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.PartySlot
import pl.pelotasplus.eyeofbeholder.data.model.PartyState
import pl.pelotasplus.eyeofbeholder.data.model.PortraitId
import pl.pelotasplus.eyeofbeholder.data.model.Ring
import pl.pelotasplus.eyeofbeholder.data.model.ScriptEvent
import pl.pelotasplus.eyeofbeholder.data.model.ScriptRun
import pl.pelotasplus.eyeofbeholder.data.model.XpPoints
import pl.pelotasplus.eyeofbeholder.data.repository.CpsRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.DecRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.InfRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.ItemTypesRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.ItemsRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.MazRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.PalRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.ResourceRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.VcnRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.VmpRepositoryImpl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The pit on the first floor's (14,17), which drops the party onto the second.
 *
 * A pit is the commonest blow in the game — a hundred and thirty-five of the
 * hundred and sixty-six a script can deal are this one — and they are all the
 * same shape: 2d6 to everybody, no saving throw, and nothing at all to
 * anyone wearing a ring of feather fall.
 *
 * Everybody's dice are rolled separately, so six champions down one pit lose
 * six different amounts rather than one amount six times.
 */
@Category(NeedsGameData::class)
class FallingDownAPitTest {

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
        ).loadInf("LEVEL1.INF").getOrThrow()
    }

    private val itemTypes: ItemTypes = runBlocking {
        ItemTypesRepositoryImpl(resources).loadItemTypes().getOrThrow()
    }

    private val dungeonItems: List<Item> = runBlocking {
        ItemsRepositoryImpl(resources).loadItems().getOrThrow().items
    }

    private fun champion(
        hitPoints: Int = 40,
        wearing: ItemIndex = ItemIndex(ItemIndex.NOTHING),
    ) = Champion(
        name = "Anselm",
        portrait = PortraitId(0),
        abilities = Abilities(strength = Ability(10, 10), dexterity = Ability(10, 10)),
        hitPoints = HitPoints(hitPoints, 40),
        armorClass = ArmorClass(10),
        food = Food(100),
        characterClass = CharacterClass.FIGHTER,
        levels = listOf(ClassLevel(1, XpPoints(0))),
        carrying = List(CarrySlot.ALL_OF_THEM) {
            if (it == CarrySlot.RINGS.first().index) wearing else ItemIndex(ItemIndex.NOTHING)
        },
        flags = ChampionFlags(IN_THE_PARTY),
    )

    private fun world(party: List<Champion> = List(6) { champion() }) = GameState(
        party = PartyState(THE_PIT, Direction.SOUTH),
        champions = party,
        items = dungeonItems,
    ).arrivingAt(level = LEVEL, places = emptyList(), maz = level.subLevels[0].maz)

    /**
     * Falling in. The square answers the party arriving on it, and the script
     * ends by sending them down, so what comes back is the drop entire.
     */
    private fun GameState.fallIn(dice: Dice = everyDieHighest): ScriptRun = runBlocking {
        LevelScriptRunner(
            script = level.script,
            level = LEVEL,
            itemTypes = itemTypes,
            dice = dice,
        ).onEvent(
            triggers = level.triggers,
            event = ScriptEvent.PARTY_ENTERED,
            state = this@fallIn,
            at = THE_PIT,
        )
    }

    /** Every die comes up its highest, so 2d6 is always twelve. */
    private val everyDieHighest = Dice { times, pips, modifier -> times * pips + modifier }

    /** And its lowest, so 2d6 is two. */
    private val everyDieLowest = Dice { times, _, modifier -> times + modifier }

    private fun aRingOf(effect: Ring) = ItemIndex(
        dungeonItems.withIndex().drop(1)
            .first { itemTypes.kindOf(it.value) == ItemKind.RING && itemTypes.ring(it.value) == effect }
            .index
    )

    @Test
    fun `the pit drops the party onto the floor below`() {
        val fell = world().fallIn()

        assertEquals(2, fell.changeLevel?.level)
        assertEquals(Location(15, 18), fell.changeLevel?.location)
    }

    @Test
    fun `and it costs everybody two dice of six`() {
        val fell = world().fallIn()

        assertEquals(6, fell.hurt.size)
        assertEquals(setOf(12), fell.hurt.values.map { it.points }.toSet())
        fell.state.champions.forEach { assertEquals(28, it.hitPoints.current) }
    }

    /** The floor of the same dice, to show the number is rolled and not fixed. */
    @Test
    fun `the smallest fall is two`() {
        val fell = world().fallIn(dice = everyDieLowest)

        assertEquals(setOf(2), fell.hurt.values.map { it.points }.toSet())
    }

    /** A ring of feather fall is the whole of the answer to a pit. */
    @Test
    fun `whoever wears a ring of feather fall lands unhurt`() {
        val party = List(6) { if (it == 0) champion(wearing = aRingOf(Ring.FEATHER_FALL)) else champion() }
        val fell = world(party).fallIn()

        assertEquals(null, fell.hurt[PartySlot(0)])
        assertEquals(40, fell.state.champions[0].hitPoints.current)
        assertEquals(28, fell.state.champions[1].hitPoints.current)
    }

    /** And any other ring is only a ring. */
    @Test
    fun `another ring is no help`() {
        val party = List(6) { if (it == 0) champion(wearing = aRingOf(Ring.ADORNMENT)) else champion() }
        val fell = world(party).fallIn()

        assertEquals(12, fell.hurt[PartySlot(0)]?.points)
    }

    /** A ring carried rather than worn does nothing, being on no finger. */
    @Test
    fun `a ring in the pack is no help`() {
        val inThePack = champion().let {
            it.copy(
                carrying = it.carrying.toMutableList().also { slots ->
                    slots[IN_THE_PACK] = aRingOf(Ring.FEATHER_FALL)
                },
            )
        }
        val fell = world(List(6) { if (it == 0) inThePack else champion() }).fallIn()

        assertEquals(12, fell.hurt[PartySlot(0)]?.points)
    }

    /** Nobody empty is hurt, there being nobody in the slot to hurt. */
    @Test
    fun `an empty place in the party takes nothing`() {
        val party = List(6) { if (it == 5) Champion.NOBODY else champion() }
        val fell = world(party).fallIn()

        assertEquals(5, fell.hurt.size)
        assertEquals(null, fell.hurt[PartySlot(5)])
    }

    /**
     * A champion lying at nothing goes on losing hit points down the pit, and
     * stops at ten below, which is as far down as anybody goes.
     */
    @Test
    fun `somebody already down falls the rest of the way`() {
        val party = List(6) { if (it == 0) champion(hitPoints = 1) else champion() }
        val fell = world(party).fallIn()

        assertNotNull(fell.hurt[PartySlot(0)])
        assertEquals(Champion.BEYOND_RAISING, fell.state.champions[0].hitPoints.current)
    }

    /** And somebody past raising is past hurting too. */
    @Test
    fun `somebody past raising takes nothing more`() {
        val party = List(6) {
            if (it == 0) champion(hitPoints = Champion.BEYOND_RAISING) else champion()
        }
        val fell = world(party).fallIn()

        assertEquals(null, fell.hurt[PartySlot(0)])
    }

    /** The numbers go up on the portraits, which is the only report of a fall. */
    @Test
    fun `the blow is put up for the player to see`() {
        val fell = world().fallIn()
        val showing = fell.state.blowsShown(fell.hurt)

        assertTrue(
            (0 until 6).all { showing.damageShownOn(PartySlot(it))?.points == 12 },
            "every portrait should be showing what the fall cost",
        )
    }

    private companion object {
        const val LEVEL = 1
        const val IN_THE_PARTY = 1

        /** A slot that is neither hand nor finger. */
        const val IN_THE_PACK = 10

        val THE_PIT = Location(14, 17)
    }
}
