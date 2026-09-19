package pl.pelotasplus.eyeofbeholder.data

import kotlinx.coroutines.runBlocking
import org.junit.experimental.categories.Category
import pl.pelotasplus.eyeofbeholder.NeedsGameData
import pl.pelotasplus.eyeofbeholder.data.model.Abilities
import pl.pelotasplus.eyeofbeholder.data.model.Ability
import pl.pelotasplus.eyeofbeholder.data.model.ArmorClass
import pl.pelotasplus.eyeofbeholder.data.model.Champion
import pl.pelotasplus.eyeofbeholder.data.model.ChampionFlags
import pl.pelotasplus.eyeofbeholder.data.model.CharacterClass
import pl.pelotasplus.eyeofbeholder.data.model.ClassLevel
import pl.pelotasplus.eyeofbeholder.data.model.Damage
import pl.pelotasplus.eyeofbeholder.data.model.Dice
import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.Flight
import pl.pelotasplus.eyeofbeholder.data.model.Food
import pl.pelotasplus.eyeofbeholder.data.model.GameState
import pl.pelotasplus.eyeofbeholder.data.model.HitPoints
import pl.pelotasplus.eyeofbeholder.data.model.Inf
import pl.pelotasplus.eyeofbeholder.data.model.Item
import pl.pelotasplus.eyeofbeholder.data.model.ItemIndex
import pl.pelotasplus.eyeofbeholder.data.model.ItemTypeId
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

/**
 * What a shot is worth, which is the launcher's business rather than the
 * ammunition's.
 *
 * ITEMTYPE.DAT gives a bow a die of six and an arrow a single pip, so rolling
 * the arrow is rolling nothing: every shot in the game would land for one
 * point, and a quiver of +2 arrows would be worth exactly as much as a plain
 * one. A shot therefore rolls the launcher and adds both enchantments, the
 * bow's and the arrow's, the way a swung weapon adds its own.
 *
 * A thrown thing has no launcher and rolls itself, and it is here too, because
 * the enchantment on it used to be dropped on the way and that is the half of
 * this that is a repair rather than a choice.
 */
@Category(NeedsGameData::class)
class WhatABowAddsToAnArrowTest {

    @Test
    fun `a shot rolls the bow rather than the arrow`() {
        // A bow is 1d6 where an arrow is 1d1.
        assertEquals(Damage(6), shot(bow = plainBow, arrow = plainArrow))
    }

    @Test
    fun `the arrow's enchantment is added to the shot`() {
        assertEquals(Damage(6 + 2), shot(bow = plainBow, arrow = plainArrow.copy(value = 2)))
    }

    @Test
    fun `the bow's enchantment is added as well`() {
        assertEquals(
            Damage(6 + 1 + 2),
            shot(bow = plainBow.copy(value = 1), arrow = plainArrow.copy(value = 2)),
        )
    }

    @Test
    fun `a thrown thing still rolls itself`() {
        // A dagger is 1d4 against something a champion's size.
        assertEquals(Damage(4), thrown(plainDagger))
    }

    @Test
    fun `and carries its own enchantment`() {
        assertEquals(Damage(4 + 2), thrown(plainDagger.copy(value = 2)))
    }

    // --- the fixture -------------------------------------------------------

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

    private val alwaysTheHighest = Dice { times, pips, modifier -> times * pips + modifier }

    private val dungeon = runBlocking { ItemsRepositoryImpl(resources).loadItems().getOrThrow() }

    private val plainBow = anItemOfType(A_BOW)
    private val plainArrow = anItemOfType(ARROWS)
    private val plainDagger = anItemOfType(A_DAGGER)

    private fun anItemOfType(type: ItemTypeId): Item =
        dungeon.items.first { it.type == type }.copy(value = 0)

    /** A shot: the arrow flies and the bow it came off travels with it. */
    private fun shot(bow: Item, arrow: Item) = landedOn(
        items = listOf(arrow, arrow, bow),
        flying = flying(what = ItemIndex(1), shotFrom = ItemIndex(2)),
    )

    private fun thrown(weapon: Item) = landedOn(
        items = listOf(weapon, weapon),
        flying = flying(what = ItemIndex(1), shotFrom = null),
    )

    private fun flying(what: ItemIndex, shotFrom: ItemIndex?) = Projectile(
        what = what,
        shotFrom = shotFrom,
        at = TARGET,
        place = SquarePlace.MIDDLE,
        going = Direction.NORTH,
        thrownBy = Projectile.Thrower.AChampion(PartySlot(0)),
        leaving = false,
    )

    private fun landedOn(items: List<Item>, flying: Projectile): Damage {
        val standing = MonsterInstance(
            index = MonsterSlot(0),
            unit = 0,
            location = TARGET,
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
            party = PartyState(Location(TARGET.x, TARGET.y + 1), Direction.NORTH),
            champions = listOf(fighter(), fighter()),
            monsters = listOf(standing),
            items = items,
            inFlight = listOf(flying),
        )

        return Flight(
            sublevel = level.subLevels[0],
            level = 5,
            itemTypes = itemTypes,
            // Nothing shrugged off and nothing oversized, so what comes back
            // is the roll rather than a share of it or the larger set of dice.
            kinds = listOf(
                level.subLevels[0].monsters.first()
                    .copy(immunities = MonsterImmunities(0), capsFlags = 0),
            ),
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

    private companion object {
        val TARGET = Location(13, 8)

        val A_BOW = ItemTypeId(7)
        val ARROWS = ItemTypeId(16)
        val A_DAGGER = ItemTypeId(5)
    }
}
