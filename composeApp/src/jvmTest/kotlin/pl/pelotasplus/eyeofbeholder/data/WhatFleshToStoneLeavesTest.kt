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
import pl.pelotasplus.eyeofbeholder.data.model.Dice
import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.Fighting
import pl.pelotasplus.eyeofbeholder.data.model.FleshToStone
import pl.pelotasplus.eyeofbeholder.data.model.Flight
import pl.pelotasplus.eyeofbeholder.data.model.Food
import pl.pelotasplus.eyeofbeholder.data.model.GameState
import pl.pelotasplus.eyeofbeholder.data.model.HitPoints
import pl.pelotasplus.eyeofbeholder.data.model.Inf
import pl.pelotasplus.eyeofbeholder.data.model.ItemIndex
import pl.pelotasplus.eyeofbeholder.data.model.ItemTypes
import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.MonsterLook
import pl.pelotasplus.eyeofbeholder.data.model.MonsterSlot
import pl.pelotasplus.eyeofbeholder.data.model.MonstersTurn
import pl.pelotasplus.eyeofbeholder.data.model.PartySlot
import pl.pelotasplus.eyeofbeholder.data.model.PartyState
import pl.pelotasplus.eyeofbeholder.data.model.PortraitId
import pl.pelotasplus.eyeofbeholder.data.model.Projectile
import pl.pelotasplus.eyeofbeholder.data.model.Spell
import pl.pelotasplus.eyeofbeholder.data.model.SquarePlace
import pl.pelotasplus.eyeofbeholder.data.model.WhatTheBlowCameTo
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Flesh to stone, on level 5's clerics standing together on 13x8.
 *
 * Coming up from 13x9 facing north, the near corners of their square are its
 * southern two: slot 17 stands in the south-west, on the party's left, and
 * slot 16 in the south-east, on their right.
 *
 * A monster turned to stone is turned for good. It never acts again, anything
 * thrown at it lands, and a blow by hand breaks it apart for what killing it
 * would have been worth.
 */
@Category(NeedsGameData::class)
class WhatFleshToStoneLeavesTest {

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

    private val onTheLeft = MonsterSlot(17)
    private val onTheRight = MonsterSlot(16)

    private fun fighter() = Champion(
        name = "Anselm",
        portrait = PortraitId(0),
        abilities = Abilities(strength = Ability(10, 10), dexterity = Ability(10, 10)),
        hitPoints = HitPoints(400, 400),
        armorClass = ArmorClass(10),
        food = Food(100),
        characterClass = CharacterClass.FIGHTER,
        levels = listOf(ClassLevel(1, XpPoints(0))),
        carrying = List(CarrySlot.ALL_OF_THEM) { ItemIndex(ItemIndex.NOTHING) },
        flags = ChampionFlags(IN_THE_PARTY),
    )

    /** Every die its highest: no dodging a spell, and any save made. */
    private val everyDieHighest = Dice { times, pips, modifier -> times * pips + modifier }

    /** Every die its lowest, save the hundred-sided one: no dodging, no save. */
    private val nothingEscapes = Dice { times, pips, modifier ->
        if (pips == 100) times * pips + modifier else times + modifier
    }

    private val alwaysOne = Dice { times, _, modifier -> times + modifier }

    private fun world(facing: Direction = Direction.NORTH) = GameState(
        party = PartyState(Location(13, 9), facing),
        champions = listOf(fighter(), fighter()),
    ).arrivingAt(
        level = 5,
        places = level.monsterInstances,
        maz = level.subLevels[0].maz,
        kinds = kinds,
        dice = everyDieHighest,
    )

    private fun GameState.monster(slot: MonsterSlot) = monsters.single { it.index == slot }

    private fun GameState.stoned(slot: MonsterSlot) = copy(
        monsters = monsters.map { if (it.index == slot) it.turnedToStone() else it },
    )

    private fun cast(world: GameState, by: PartySlot, dice: Dice = nothingEscapes) =
        FleshToStone(kinds, dice).castBy(world, by)

    // --- the spell -----------------------------------------------------------

    /** Aimed down the corridor: nobody is asked, and nothing lasts. */
    @Test
    fun `it is aimed ahead rather than laid on anybody`() {
        assertTrue(Spell.FLESH_TO_STONE.turnsTheNearestAheadToStone)
        assertNull(Spell.FLESH_TO_STONE.laidOn)
        assertNull(Spell.FLESH_TO_STONE.lasts)
        assertTrue(Spell.FLESH_TO_STONE.givesTheCasterAway)
    }

    /**
     * The one nearest the caster, which for two standing side by side is the
     * one on the caster's own side: the champion on the left turns the cleric
     * on the left, and the one on the right the other.
     */
    @Test
    fun `it takes the creature on the caster's own side`() {
        val byTheLeft = cast(world(), PartySlot(0))
        val byTheRight = cast(world(), PartySlot(1))

        assertEquals(FleshToStone.CameTo.TURNED, byTheLeft.came)
        assertTrue(byTheLeft.world.monster(onTheLeft).stone)
        assertFalse(byTheLeft.world.monster(onTheRight).stone, "it took both")

        assertTrue(byTheRight.world.monster(onTheRight).stone)
        assertFalse(byTheRight.world.monster(onTheLeft).stone)
    }

    @Test
    fun `a creature that saves is not turned`() {
        val cast = cast(world(), PartySlot(0), dice = everyDieHighest)

        assertEquals(FleshToStone.CameTo.RESISTED, cast.came)
        assertTrue(cast.world.monsters.none { it.stone })
    }

    /**
     * Quick enough is out of the way before any save is rolled. At a will-o'-
     * wisp's 99 the hundred-sided die has to come up 99 or more to reach it,
     * so a 98 is dodged and a 99 is not — the boundary is the whole rule.
     */
    @Test
    fun `a creature quick enough is out of the way`() {
        val quick = kinds.map { it.copy(dmgModifierEvade = A_WISPS_EVADE) }

        fun castRolling(hundred: Int) = FleshToStone(
            quick,
            Dice { times, pips, modifier -> if (pips == 100) hundred else times + modifier },
        ).castBy(world(), PartySlot(0))

        assertEquals(FleshToStone.CameTo.RESISTED, castRolling(98).came)
        assertEquals(FleshToStone.CameTo.TURNED, castRolling(99).came)
    }

    /** Facing an empty square, the caster is told there is nothing to take. */
    @Test
    fun `nothing ahead is nothing within reach`() {
        val cast = cast(world(facing = Direction.SOUTH), PartySlot(0))

        assertEquals(FleshToStone.CameTo.NOTHING_IN_REACH, cast.came)
    }

    // --- what stone is -------------------------------------------------------

    /** Drawn as stone; the flash of a blow still shows over it. */
    @Test
    fun `stone is drawn as stone, and a blow's flash over it`() {
        val statue = world().stoned(onTheLeft).monster(onTheLeft)

        assertEquals(MonsterLook.STONE, statue.look)
        assertEquals(MonsterLook.STRUCK, statue.copy(struck = true).look)
        assertNull(world().monster(onTheLeft).look)
    }

    /**
     * A roused cleric in reach swings; the same cleric in stone does nothing.
     * The same roll is played twice and stone is the only difference.
     */
    @Test
    fun `stone takes no turn`() {
        val roused = world().rousedBy(onTheRight).rousedBy(onTheLeft)

        assertTrue(swungThrough(roused).struck.isNotEmpty(), "the clerics never swung at all")
        assertEquals(
            emptyList(),
            swungThrough(roused.stoned(onTheLeft).stoned(onTheRight)).struck,
            "a statue swung at the party",
        )
    }

    // --- breaking it -----------------------------------------------------------

    /** By hand it breaks, with no roll: a die of one is enough. */
    @Test
    fun `a blow by hand breaks stone apart`() {
        val struck = fighting(alwaysOne).strike(world().stoned(onTheLeft), PartySlot(0), CarrySlot(0))

        assertIs<Blow.Shattered>(struck.blow)
        assertTrue(struck.world.monsters.none { it.index == onTheLeft }, "the statue still stands")
        assertEquals(WhatTheBlowCameTo.Hacked, WhatTheBlowCameTo.of(struck.blow))
    }

    /**
     * Breaking a statue is worth what killing the creature would have been —
     * measured against a killing blow on the same cleric, rather than against
     * a figure, so the way experience is shared does not have to be known.
     */
    @Test
    fun `breaking it is worth what killing it would have been`() {
        val killed = fighting(everyDieHighest).strike(
            world().nearlyDead(onTheLeft),
            PartySlot(0),
            CarrySlot(0),
        )
        val broken = fighting(alwaysOne).strike(world().stoned(onTheLeft), PartySlot(0), CarrySlot(0))

        assertTrue(killed.world.monsters.none { it.index == onTheLeft }, "the killing blow did not kill")
        assertEquals(killed.world.experienceOfTheParty(), broken.world.experienceOfTheParty())
    }

    /**
     * Thrown, it is only hurt — but it is found. A throw that would miss the
     * living cleric lands on the stone one.
     */
    @Test
    fun `anything thrown at stone lands`() {
        assertEquals(emptyList(), thrownAt(world(), alwaysOne), "the living cleric was hit anyway")
        assertEquals(
            listOf(onTheLeft),
            thrownAt(world().stoned(onTheLeft), alwaysOne),
            "the throw missed a statue",
        )
    }

    // --- the fixture ---------------------------------------------------------

    private fun fighting(dice: Dice) = Fighting(
        itemTypes = itemTypes,
        kinds = kinds,
        level = 5,
        dice = dice,
    )

    private fun swungThrough(from: GameState): MonstersTurn.Taken {
        val turn = MonstersTurn(kinds, everyDieHighest)
        var world = turn.begun(from).world
        world = world.swingsCarriedOn()
        return turn.landed(world, world.landingThisFrame)
    }

    private fun GameState.nearlyDead(slot: MonsterSlot): GameState {
        val hurt = monster(slot).hitPoints.current - 1
        return monsterHurt(slot, Damage(hurt), kinds)
    }

    private fun GameState.experienceOfTheParty(): Long =
        champions.sumOf { champion -> champion.levels.sumOf { it.experience.count } }

    /** A thing thrown by the champion on the left, already over the clerics' square. */
    private fun thrownAt(world: GameState, dice: Dice): List<MonsterSlot> {
        val square = Location(13, 8)
        val flying = world.copy(
            monsters = world.monsters.filter { it.index == onTheLeft },
            inFlight = listOf(
                Projectile(
                    what = null,
                    at = square,
                    place = SquarePlace.SOUTH_WEST,
                    going = Direction.NORTH,
                    thrownBy = Projectile.Thrower.AChampion(PartySlot(0)),
                    leaving = false,
                ),
            ),
        )

        return Flight(sublevel = level.subLevels[0], level = 5, kinds = kinds, dice = dice)
            .onward(flying)
            .hurt
            .filterIsInstance<Flight.Hurt.AMonster>()
            .map { it.slot }
    }

    private companion object {
        const val IN_THE_PARTY = 1

        /** How quick the ninth floor's will-o'-wisps are, out of a hundred. */
        const val A_WISPS_EVADE = 99
    }
}
