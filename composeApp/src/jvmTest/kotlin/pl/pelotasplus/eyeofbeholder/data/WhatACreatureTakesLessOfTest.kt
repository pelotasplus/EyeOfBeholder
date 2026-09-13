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
import pl.pelotasplus.eyeofbeholder.data.model.DealtBy
import pl.pelotasplus.eyeofbeholder.data.model.Dice
import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.Fighting
import pl.pelotasplus.eyeofbeholder.data.model.Flight
import pl.pelotasplus.eyeofbeholder.data.model.Food
import pl.pelotasplus.eyeofbeholder.data.model.GameState
import pl.pelotasplus.eyeofbeholder.data.model.HitPoints
import pl.pelotasplus.eyeofbeholder.data.model.Inf
import pl.pelotasplus.eyeofbeholder.data.model.Item
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
import kotlin.test.assertIs

/**
 * Creatures that take only part of a blow.
 *
 * One bit halves an edge, for something a blade barely cuts. Another shrugs
 * off weak weapons: a quarter from anything short of +3, half from a +3, all
 * of it from better, and half of any magic — and a blow shrugged off entirely
 * still costs it the weapon's own bonus.
 *
 * Level 5's clerics are what gets struck, with the immunities put there by the
 * test; the last floor's dragon is read as shipped, to catch a wrong offset.
 */
@Category(NeedsGameData::class)
class WhatACreatureTakesLessOfTest {

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

    private val dungeon = runBlocking { ItemsRepositoryImpl(resources).loadItems().getOrThrow() }

    private val kinds get() = level.subLevels[0].monsters

    private val alwaysTheHighest = Dice { times, pips, modifier -> times * pips + modifier }

    private val nothing = MonsterImmunities(0)
    private val bladesHalved = MonsterImmunities(0x100)
    private val weakWeaponsShruggedOff = MonsterImmunities(0x2000)

    private fun plain(edged: Boolean = false) = DealtBy.AWeapon(enchantment = 0, edged = edged)

    // --- the rule ----------------------------------------------------------

    @Test
    fun `a creature with neither bit takes every point`() {
        assertEquals(Damage(10), nothing.softened(Damage(10), plain(edged = true)))
        assertEquals(Damage(10), nothing.softened(Damage(10), DealtBy.Magic))
    }

    @Test
    fun `a weak weapon does a quarter, a +3 half and a +4 all of it`() {
        fun by(enchantment: Int) = weakWeaponsShruggedOff
            .softened(Damage(16), DealtBy.AWeapon(enchantment, edged = false))

        assertEquals(Damage(4), by(0))
        assertEquals(Damage(4), by(2))
        assertEquals(Damage(8), by(3))
        assertEquals(Damage(16), by(4))
    }

    @Test
    fun `magic does half`() {
        assertEquals(Damage(8), weakWeaponsShruggedOff.softened(Damage(16), DealtBy.Magic))
    }

    @Test
    fun `a blow shrugged off entirely still costs it the weapon's bonus`() {
        val weak = DealtBy.AWeapon(enchantment = 2, edged = false)

        assertEquals(Damage(2), weakWeaponsShruggedOff.softened(Damage(3), weak))
        assertEquals(Damage(0), weakWeaponsShruggedOff.softened(Damage(3), plain()))
    }

    @Test
    fun `an edge is halved and a blunt weapon is not`() {
        assertEquals(Damage(5), bladesHalved.softened(Damage(10), plain(edged = true)))
        assertEquals(Damage(10), bladesHalved.softened(Damage(10), plain(edged = false)))
        assertEquals(Damage(10), bladesHalved.softened(Damage(10), DealtBy.Magic))
    }

    @Test
    fun `an edge is halved before a weak weapon is quartered`() {
        val both = MonsterImmunities(0x2100)

        assertEquals(Damage(2), both.softened(Damage(16), plain(edged = true)))
    }

    @Test
    fun `the last floor's dragon shrugs off weak weapons`() = runBlocking {
        val dragon = infs().loadInf("LEVEL16.INF").getOrThrow().subLevels[1].monsters[1]

        assertEquals(Damage(5), dragon.immunities.softened(Damage(20), plain()))
        assertEquals(Damage(10), dragon.immunities.softened(Damage(20), DealtBy.Magic))
    }

    // --- a swing -----------------------------------------------------------

    @Test
    fun `a plain blunt weapon swung at one does a quarter of what it does to anything else`() {
        val weapon = handWeapon(edged = false)

        assertEquals(
            Damage(swungFor(weapon, nothing).points shr 2),
            swungFor(weapon, weakWeaponsShruggedOff),
        )
    }

    @Test
    fun `a blade swung at one that blades barely cut does half`() {
        val blade = handWeapon(edged = true)

        assertEquals(Damage(swungFor(blade, nothing).points shr 1), swungFor(blade, bladesHalved))
    }

    // --- in flight ---------------------------------------------------------

    @Test
    fun `a plain weapon thrown at one does a quarter`() {
        val weapon = handWeapon(edged = false)

        assertEquals(
            Damage(thrownFor(weapon, nothing).points shr 2),
            thrownFor(weapon, weakWeaponsShruggedOff),
        )
    }

    @Test
    fun `a bolt of magic does half`() {
        assertEquals(
            Damage(boltFor(nothing).points shr 1),
            boltFor(weakWeaponsShruggedOff),
        )
    }

    // --- the fixture -------------------------------------------------------

    private val theTarget = MonsterSlot(0)

    private fun handWeapon(edged: Boolean): Item = dungeon.items.first {
        itemTypes.kindOf(it) == ItemKind.SWUNG_BY_HAND && itemTypes.isEdged(it) == edged
    }.copy(value = 0)

    private fun swungFor(weapon: Item, immunities: MonsterImmunities): Damage {
        val struck = Fighting(itemTypes, kinds.map { it.copy(immunities = immunities) }, alwaysTheHighest)
            .strike(worldHolding(weapon), PartySlot(0), CarrySlot(0))
        return assertIs<Blow.Hit>(struck.blow).damage
    }

    private fun thrownFor(weapon: Item, immunities: MonsterImmunities) = hurtIn(
        immunities,
        items = listOf(weapon, weapon),
        flying = Projectile(
            what = ItemIndex(1),
            at = Location(13, 8),
            place = SquarePlace.MIDDLE,
            going = Direction.NORTH,
            thrownBy = Projectile.Thrower.AChampion(PartySlot(0)),
            leaving = false,
        ),
    )

    private fun boltFor(immunities: MonsterImmunities) = hurtIn(
        immunities,
        items = emptyList(),
        flying = Projectile(
            at = Location(13, 8),
            place = SquarePlace.MIDDLE,
            going = Direction.NORTH,
            thrownBy = Projectile.Thrower.TheLevel,
            harm = Projectile.Harm(dice = DamageDice(4, 6, 0)),
            leaving = false,
        ),
    )

    private fun hurtIn(immunities: MonsterImmunities, items: List<Item>, flying: Projectile): Damage {
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
            hitPoints = HitPoints(200, 200),
        )
        val world = GameState(
            party = PartyState(Location(13, 9), Direction.NORTH),
            champions = listOf(fighter(), fighter()),
            monsters = listOf(standing),
            items = items,
            inFlight = listOf(flying),
        )

        return Flight(
            sublevel = level.subLevels[0],
            level = 5,
            itemTypes = itemTypes,
            kinds = listOf(kinds.first().copy(immunities = immunities)),
            dice = alwaysTheHighest,
        ).onward(world)
            .hurt
            .filterIsInstance<Flight.Hurt.AMonster>()
            .single()
            .by
    }

    private fun fighter() = Champion(
        name = "Anselm",
        portrait = PortraitId(0),
        abilities = Abilities(strength = Ability(10, 10), dexterity = Ability(10, 10)),
        hitPoints = HitPoints(20, 20),
        armorClass = ArmorClass(10),
        food = Food(100),
        characterClass = CharacterClass.FIGHTER,
        levels = listOf(ClassLevel(9, XpPoints(0))),
        carrying = List(27) { ItemIndex(ItemIndex.NOTHING) },
        flags = ChampionFlags(1),
    )

    private fun worldHolding(weapon: Item): GameState {
        val world = GameState(
            party = PartyState(Location(13, 9), Direction.NORTH),
            champions = listOf(fighter(), fighter()),
        ).arrivingAt(
            level = 5,
            places = level.monsterInstances,
            maz = level.subLevels[0].maz,
            kinds = kinds,
            dice = alwaysTheHighest,
        )

        // Slot zero of the table is what an empty hand points at.
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
