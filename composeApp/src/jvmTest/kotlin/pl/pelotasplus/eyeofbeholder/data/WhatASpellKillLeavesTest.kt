package pl.pelotasplus.eyeofbeholder.data

import kotlinx.coroutines.runBlocking
import org.junit.experimental.categories.Category
import pl.pelotasplus.eyeofbeholder.NeedsGameData
import pl.pelotasplus.eyeofbeholder.data.model.AConeOfCold
import pl.pelotasplus.eyeofbeholder.data.model.Abilities
import pl.pelotasplus.eyeofbeholder.data.model.Ability
import pl.pelotasplus.eyeofbeholder.data.model.ArmorClass
import pl.pelotasplus.eyeofbeholder.data.model.BurningHands
import pl.pelotasplus.eyeofbeholder.data.model.Champion
import pl.pelotasplus.eyeofbeholder.data.model.ChampionFlags
import pl.pelotasplus.eyeofbeholder.data.model.CharacterClass
import pl.pelotasplus.eyeofbeholder.data.model.ClassLevel
import pl.pelotasplus.eyeofbeholder.data.model.Dice
import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.Flight
import pl.pelotasplus.eyeofbeholder.data.model.Food
import pl.pelotasplus.eyeofbeholder.data.model.GameState
import pl.pelotasplus.eyeofbeholder.data.model.HitPoints
import pl.pelotasplus.eyeofbeholder.data.model.Inf
import pl.pelotasplus.eyeofbeholder.data.model.Item
import pl.pelotasplus.eyeofbeholder.data.model.ItemIconId
import pl.pelotasplus.eyeofbeholder.data.model.ItemIndex
import pl.pelotasplus.eyeofbeholder.data.model.ItemNameId
import pl.pelotasplus.eyeofbeholder.data.model.ItemTypeId
import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.MonsterImmunities
import pl.pelotasplus.eyeofbeholder.data.model.MonsterInstance
import pl.pelotasplus.eyeofbeholder.data.model.MonsterProperty
import pl.pelotasplus.eyeofbeholder.data.model.MonsterSize
import pl.pelotasplus.eyeofbeholder.data.model.MonsterSlot
import pl.pelotasplus.eyeofbeholder.data.model.MonsterTypeId
import pl.pelotasplus.eyeofbeholder.data.model.PartySlot
import pl.pelotasplus.eyeofbeholder.data.model.PartyState
import pl.pelotasplus.eyeofbeholder.data.model.PortraitId
import pl.pelotasplus.eyeofbeholder.data.model.Projectile
import pl.pelotasplus.eyeofbeholder.data.model.Spell
import pl.pelotasplus.eyeofbeholder.data.model.SquarePlace
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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * A monster killed by a spell or by something in the air leaves what it
 * carries and pays its experience, the same as one cut down with a blade.
 * The Catacombs cube that carries the Spider Key is the case that needs it:
 * burnt to death it used to leave nothing, and every Spider door stayed shut.
 */
@Category(NeedsGameData::class)
class WhatASpellKillLeavesTest {

    private val resources = ResourceRepositoryImpl()

    private val level5: Inf = runBlocking {
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

    private val partyAt = Location(13, 9)
    private val ahead = Location(13, 8)
    private val carried = ItemIndex(1)

    private val atItsWorst = Dice { times, pips, modifier -> times * pips + modifier }

    @Test
    fun `a monster a missile kills leaves what it carries`() {
        val after = missileKill()

        assertTrue(after.monsters.isEmpty(), "the missile did not kill it")
        assertDropped(after)
    }

    @Test
    fun `a monster a missile kills is paid for`() {
        assertPaid(missileKill())
    }

    @Test
    fun `a monster burning hands kills leaves what it carries and is paid for`() {
        val after = BurningHands(kinds = listOf(aKind()), dice = atItsWorst, level = LEVEL)
            .castBy(world(), casterLevel = 9).world

        assertTrue(after.monsters.isEmpty(), "the flame did not kill it")
        assertDropped(after)
        assertPaid(after)
    }

    @Test
    fun `a monster a cone of cold kills leaves what it carries and is paid for`() {
        val after = AConeOfCold(kinds = listOf(aKind()), dice = atItsWorst, level = LEVEL)
            .castBy(world(), casterLevel = 9).world

        assertTrue(after.monsters.isEmpty(), "the cold did not kill it")
        assertDropped(after)
        assertPaid(after)
    }

    /** One that lives through it carries on carrying, and nothing is paid. */
    @Test
    fun `a monster that survives the spell drops nothing`() {
        val tough = world(hitPoints = HitPoints(500, 500))
        val after = AConeOfCold(kinds = listOf(aKind()), dice = atItsWorst, level = LEVEL)
            .castBy(tough, casterLevel = 9).world

        assertEquals(1, after.monsters.size)
        assertEquals(tough.items.count { it.exists }, after.items.count { it.exists })
        assertEquals(XpPoints(0), after.champions.single().levels.single().experience)
    }

    private fun missileKill(): GameState {
        val thrown = Spell.MAGIC_MISSILE.throws ?: error("magic missile throws nothing")
        val world = world().copy(
            inFlight = listOf(
                Projectile(
                    what = null,
                    at = ahead,
                    place = SquarePlace.MIDDLE,
                    going = Direction.NORTH,
                    squaresLeft = thrown.flies.reach,
                    thrownBy = Projectile.Thrower.AChampion(PartySlot(0)),
                    harm = thrown.dealtBy(9),
                    spell = thrown.flies,
                    leaving = false,
                ),
            ),
        )

        return Flight(
            sublevel = level5.subLevels[0],
            level = LEVEL,
            kinds = listOf(aKind()),
            dice = atItsWorst,
        ).onward(world).world
    }

    private fun assertDropped(after: GameState) {
        val dropped = after.items.withIndex().filter {
            it.index != carried.value && it.value.exists && it.value.location == ahead && it.value.level == LEVEL
        }
        val copy = assertNotNull(dropped.singleOrNull(), "nothing was left on its square").value
        assertEquals(template().type, copy.type)
    }

    private fun assertPaid(after: GameState) {
        assertTrue(
            after.champions.single().levels.single().experience > XpPoints(0),
            "the kill paid nothing",
        )
    }

    private fun world(hitPoints: HitPoints = HitPoints(3, 3)) = GameState(
        party = PartyState(partyAt, Direction.NORTH),
        champions = listOf(fighter()),
        monsters = listOf(
            MonsterInstance(
                index = MonsterSlot(0),
                unit = 0,
                location = ahead,
                place = SquarePlace.MIDDLE,
                direction = Direction.SOUTH,
                type = MonsterTypeId(0),
                gfxIndex = 0,
                mode = 0,
                pause = 0,
                weapon = 0,
                pocketItem = carried.value,
                hitPoints = hitPoints,
            ),
        ),
        items = listOf(nothing(), template(), nothing()),
    )

    private fun template() = Item(
        nameUnidentified = ItemNameId(34),
        nameIdentified = ItemNameId(1),
        flags = 0,
        icon = ItemIconId(64),
        type = ItemTypeId(38),
        place = SquarePlace.MIDDLE,
        location = Location(0, 0),
        next = 0,
        prev = 0,
        level = 0,
        value = 6,
    )

    private fun nothing() = template().copy(location = Item.NOWHERE, level = 0)

    private fun aKind() = MonsterProperty(
        id = 0,
        armorClass = 9, hitChance = 13, level = 1,
        hpDcTimes = 0, hpDcPips = 0, hpDcBase = 1,
        attacksPerRound = 1, dmgDc = emptyList(),
        immunities = MonsterImmunities(0), capsFlags = 0, typeFlags = 2, experience = 120,
        size = MonsterSize.FILLS_THE_SQUARE,
        sound1 = 0, sound2 = 0,
        numRemoteAttacks = 0, remoteWeaponChangeMode = null, numRemoteWeapons = null,
        remoteWeapons = emptyList(), tuResist = -1, dmgModifierEvade = 0,
        decorations = emptyList(),
    )

    private fun fighter() = Champion(
        name = "Anselm",
        portrait = PortraitId(0),
        abilities = Abilities(
            strength = Ability(10, 10),
            dexterity = Ability(10, 10),
        ),
        hitPoints = HitPoints(90, 90),
        armorClass = ArmorClass(10),
        food = Food(100),
        characterClass = CharacterClass.FIGHTER,
        levels = listOf(ClassLevel(1, XpPoints(0))),
        carrying = List(27) { ItemIndex(ItemIndex.NOTHING) },
        flags = ChampionFlags(1),
    )

    private companion object {
        const val LEVEL = 5
    }
}
