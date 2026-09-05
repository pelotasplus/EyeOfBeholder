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
import pl.pelotasplus.eyeofbeholder.data.model.Dice
import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.Fighting
import pl.pelotasplus.eyeofbeholder.data.model.Flight
import pl.pelotasplus.eyeofbeholder.data.model.Food
import pl.pelotasplus.eyeofbeholder.data.model.GameState
import pl.pelotasplus.eyeofbeholder.data.model.HitPoints
import pl.pelotasplus.eyeofbeholder.data.model.Inf
import pl.pelotasplus.eyeofbeholder.data.model.ItemIndex
import pl.pelotasplus.eyeofbeholder.data.model.ItemKind
import pl.pelotasplus.eyeofbeholder.data.model.ItemTypes
import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.MonsterImmunities
import pl.pelotasplus.eyeofbeholder.data.model.MonsterInstance
import pl.pelotasplus.eyeofbeholder.data.model.MonsterSlot
import pl.pelotasplus.eyeofbeholder.data.model.MonsterTypeId
import pl.pelotasplus.eyeofbeholder.data.model.PartySlot
import pl.pelotasplus.eyeofbeholder.data.model.PartyState
import pl.pelotasplus.eyeofbeholder.data.model.PortraitId
import pl.pelotasplus.eyeofbeholder.data.model.Projectile
import pl.pelotasplus.eyeofbeholder.data.model.SquarePlace
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
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Some creatures are reached only by a weapon of a certain quality, and
 * anything plainer never touches them.
 *
 * This is not a penalty to the roll but a refusal before it: the party can
 * swing all day with plain steel and the thing takes nothing, which is why it
 * has to be settled ahead of the die rather than folded into what a champion
 * needs. Two bits say it — one wanting a weapon of +1 or better, one wanting
 * +2 — and the enchantment counted is the weapon's own, a bare fist being
 * nothing.
 *
 * Level 5's clerics stand on 13x8 and are what gets swung at; what they are
 * immune to is put there by the test, since the rule is under test rather than
 * any one floor's creatures. One test at the end reads a shipped number
 * instead, so that a wrong byte offset is caught as well as a wrong rule.
 */
@Category(NeedsGameData::class)
class WhatAWeaponIsTooPlainForTest {

    private val resources = ResourceRepositoryImpl()

    private fun infs() = InfRepositoryImpl(
        resourceRepository = resources,
        mazRepository = MazRepositoryImpl(resources),
        vmpRepository = VmpRepositoryImpl(resources),
        vcnRepository = VcnRepositoryImpl(resources),
        palRepository = PalRepositoryImpl(resources),
        cpsRepository = CpsRepositoryImpl(resources),
        decRepository = DecRepositoryImpl(resources),
    )

    private val level: Inf = runBlocking { infs().loadInf("LEVEL5.INF").getOrThrow() }

    private val itemTypes: ItemTypes = runBlocking {
        ItemTypesRepositoryImpl(resources).loadItemTypes().getOrThrow()
    }

    private val dungeon = runBlocking {
        ItemsRepositoryImpl(resources).loadItems().getOrThrow()
    }

    private val kinds get() = level.subLevels[0].monsters

    /** Every die comes up its highest, so nothing is ever missed by rolling. */
    private val alwaysTwenty = Dice { times, pips, modifier -> times * pips + modifier }

    private val needsNothing = MonsterImmunities(0)
    private val needsPlusOne = MonsterImmunities(0x200)
    private val needsPlusTwo = MonsterImmunities(0x1000)

    // --- what the flag word says ---------------------------------------------

    @Test
    fun `a creature with neither bit is touched by a bare fist`() {
        assertTrue(needsNothing.canBeHitBy(0))
    }

    @Test
    fun `one wanting a plus one refuses plain steel and takes the next step up`() {
        assertFalse(needsPlusOne.canBeHitBy(0))
        assertTrue(needsPlusOne.canBeHitBy(1))
        assertTrue(needsPlusOne.canBeHitBy(2))
    }

    @Test
    fun `and one wanting a plus two refuses the plus one as well`() {
        assertFalse(needsPlusTwo.canBeHitBy(0))
        assertFalse(needsPlusTwo.canBeHitBy(1))
        assertTrue(needsPlusTwo.canBeHitBy(2))
        assertTrue(needsPlusTwo.canBeHitBy(3))
    }

    /** Both bits together want the higher of the two, not the lower. */
    @Test
    fun `both bits set want the better weapon`() {
        val both = MonsterImmunities(0x1200)

        assertFalse(both.canBeHitBy(1))
        assertTrue(both.canBeHitBy(2))
    }

    /**
     * The word carries other immunities in bits nothing here reads, and a
     * creature holding only those is hit by anything.
     */
    @Test
    fun `the rest of the word says nothing about weapons`() {
        assertTrue(MonsterImmunities(0x00A5).canBeHitBy(0))
    }

    // --- and what that does to a swing ---------------------------------------

    @Test
    fun `a plain weapon never lands on something wanting a plus two`() {
        val struck = swing(enchantment = 0, immunities = needsPlusTwo)

        assertIs<Blow.Missed>(struck.blow)
    }

    /** Nor does a plus one, which is the step that is not enough. */
    @Test
    fun `nor does the weapon one short of enough`() {
        assertIs<Blow.Missed>(swing(enchantment = 1, immunities = needsPlusTwo).blow)
    }

    @Test
    fun `a good enough weapon lands`() {
        assertIs<Blow.Hit>(swing(enchantment = 2, immunities = needsPlusTwo).blow)
    }

    /**
     * The refusal is not a poor roll: a swing that cannot land takes nothing
     * off however good the die was, and this one rolled a twenty.
     */
    @Test
    fun `a refused swing leaves the creature untouched`() {
        val before = worldHolding(enchantment = 0)
        val struck = fighting(needsPlusTwo).strike(before, PartySlot(0), CarrySlot(0))

        assertEquals(
            before.monsters.map { it.hitPoints },
            struck.world.monsters.map { it.hitPoints },
        )
    }

    /** And a bare fist is a weapon of nothing, so it is refused too. */
    @Test
    fun `a bare fist cannot touch one either`() {
        val barehanded = world()
        val struck = fighting(needsPlusTwo).strike(barehanded, PartySlot(0), CarrySlot(0))

        assertIs<Blow.Missed>(struck.blow)
        assertIs<Blow.Hit>(fighting(needsNothing).strike(barehanded, PartySlot(0), CarrySlot(0)).blow)
    }

    /** Where nothing is asked for, the plainest weapon lands as it always did. */
    @Test
    fun `a creature asking for nothing is hit by plain steel`() {
        assertIs<Blow.Hit>(swing(enchantment = 0, immunities = needsNothing).blow)
    }

    // --- and to a throw ------------------------------------------------------

    /**
     * A thrown weapon is refused the same way a swung one is. The throw is
     * still a throw — the thing carries on over the creature it could not
     * hurt, the way any miss does.
     */
    @Test
    fun `a plain weapon thrown at one goes past it`() {
        assertEquals(emptyList(), hurtBy(enchantment = 0, immunities = needsPlusTwo))
        assertEquals(emptyList(), hurtBy(enchantment = 1, immunities = needsPlusTwo))
    }

    @Test
    fun `and a good enough one thrown at it lands`() {
        assertEquals(listOf(theTarget), hurtBy(enchantment = 2, immunities = needsPlusTwo))
    }

    /** Where nothing is asked for, a plain thrown weapon lands as before. */
    @Test
    fun `a creature asking for nothing is hit by a plain thrown weapon`() {
        assertEquals(listOf(theTarget), hurtBy(enchantment = 0, immunities = needsNothing))
    }

    // --- a floor's own creature ----------------------------------------------

    /**
     * Level 11 keeps two creatures under the same type id, one per sublevel,
     * and only the second wants a good weapon: the shaggy thing a script puts
     * in front of the party at 4x30 takes nothing from plain steel, while the
     * bulette of the first sublevel takes it from anything.
     */
    @Test
    fun `a real floor's own creature carries it`() = runBlocking {
        val eleven = infs().loadInf("LEVEL11.INF").getOrThrow()

        val guardian = eleven.subLevels[1].monsters.first { it.id == 0 }
        assertFalse(guardian.immunities.canBeHitBy(0), "plain steel reached it")
        assertFalse(guardian.immunities.canBeHitBy(1), "a plus one reached it")
        assertTrue(guardian.immunities.canBeHitBy(2), "a plus two did not")

        val bulette = eleven.subLevels[0].monsters.first { it.id == 0 }
        assertTrue(bulette.immunities.canBeHitBy(0), "the bulette wanted a better weapon")
    }

    // --- the fixture ---------------------------------------------------------

    private val theTarget = MonsterSlot(0)

    /** Which monsters a weapon of [enchantment] hurts, thrown onto their square. */
    private fun hurtBy(enchantment: Int, immunities: MonsterImmunities): List<MonsterSlot> {
        val weapon = handWeapon(enchantment)
        val standing = MonsterInstance(
            index = theTarget,
            unit = 0,
            location = Location(13, 8),
            place = SquarePlace.MIDDLE,
            direction = Direction.SOUTH,
            type = MonsterTypeId(0),
            gfxIndex = 0,
            mode = 0,
            pause = 0,
            weapon = 0,
            pocketItem = 0,
            hitPoints = HitPoints(60, 60),
        )

        // Slot zero of the table is what nothing points at, so the weapon that
        // is really in the air is the one after it.
        val inTheAir = ItemIndex(1)
        val world = GameState(
            party = PartyState(Location(13, 9), Direction.NORTH),
            champions = listOf(fighter(), fighter()),
            monsters = listOf(standing),
            items = listOf(weapon, weapon),
            inFlight = listOf(
                // Not leaving: a thing still on its way out of the square it
                // was thrown from meets nothing there, and this one is over
                // the creature rather than being let go beside it.
                Projectile(
                    what = inTheAir,
                    at = Location(13, 8),
                    place = SquarePlace.MIDDLE,
                    going = Direction.NORTH,
                    thrownBy = Projectile.Thrower.AChampion(PartySlot(0)),
                    leaving = false,
                ),
            ),
        )

        return Flight(
            sublevel = level.subLevels[0],
            level = 5,
            itemTypes = itemTypes,
            kinds = listOf(kinds.first().copy(immunities = immunities)),
            dice = alwaysTwenty,
        ).onward(world)
            .hurt
            .filterIsInstance<Flight.Hurt.AMonster>()
            .map { it.slot }
    }

    private fun handWeapon(enchantment: Int) =
        dungeon.items.first { itemTypes.kindOf(it) == ItemKind.SWUNG_BY_HAND }
            .copy(value = enchantment)

    private fun fighting(immunities: MonsterImmunities) =
        Fighting(itemTypes, kinds.map { it.copy(immunities = immunities) }, alwaysTwenty)

    private fun swing(enchantment: Int, immunities: MonsterImmunities) =
        fighting(immunities).strike(worldHolding(enchantment), PartySlot(0), CarrySlot(0))

    private fun fighter() = Champion(
        name = "Anselm",
        portrait = PortraitId(0),
        abilities = Abilities(
            strength = Ability(10, 10),
            dexterity = Ability(10, 10),
        ),
        hitPoints = HitPoints(20, 20),
        armorClass = ArmorClass(10),
        food = Food(100),
        characterClass = CharacterClass.FIGHTER,
        levels = listOf(ClassLevel(9, XpPoints(0))),
        carrying = List(27) { ItemIndex(ItemIndex.NOTHING) },
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
        dice = alwaysTwenty,
    )

    /** The world with a hand weapon of [enchantment] in the first champion's hand. */
    private fun worldHolding(enchantment: Int): GameState {
        val weapon = handWeapon(enchantment)
        val world = world()

        // Slot zero of the table is what an empty hand points at, so nothing
        // real may live there.
        val table = world.items.ifEmpty { listOf(weapon) }
        val at = ItemIndex(table.size)

        return world.copy(
            items = table + weapon,
            champions = world.champions.mapIndexed { slot, champion ->
                if (slot != 0) champion
                else champion.copy(carrying = champion.carrying.toMutableList().also { it[0] = at })
            },
        )
    }
}
