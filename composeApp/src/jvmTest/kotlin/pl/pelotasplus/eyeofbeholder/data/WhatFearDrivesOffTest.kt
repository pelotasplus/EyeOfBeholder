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
import pl.pelotasplus.eyeofbeholder.data.model.Damage
import pl.pelotasplus.eyeofbeholder.data.model.Dice
import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.Food
import pl.pelotasplus.eyeofbeholder.data.model.Frightening
import pl.pelotasplus.eyeofbeholder.data.model.GameState
import pl.pelotasplus.eyeofbeholder.data.model.HitPoints
import pl.pelotasplus.eyeofbeholder.data.model.Inf
import pl.pelotasplus.eyeofbeholder.data.model.ItemIndex
import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.MonsterImmunities
import pl.pelotasplus.eyeofbeholder.data.model.MonsterInstance
import pl.pelotasplus.eyeofbeholder.data.model.MonsterPathing
import pl.pelotasplus.eyeofbeholder.data.model.MonsterSlot
import pl.pelotasplus.eyeofbeholder.data.model.MonsterStepping
import pl.pelotasplus.eyeofbeholder.data.model.MonstersTurn
import pl.pelotasplus.eyeofbeholder.data.model.PartyState
import pl.pelotasplus.eyeofbeholder.data.model.PortraitId
import pl.pelotasplus.eyeofbeholder.data.model.Spell
import pl.pelotasplus.eyeofbeholder.data.model.WallSide
import pl.pelotasplus.eyeofbeholder.data.model.XpPoints
import pl.pelotasplus.eyeofbeholder.data.model.canBeWalkedOnto
import pl.pelotasplus.eyeofbeholder.data.model.getWall
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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Fear, on level 5's clerics, who stand together on 13x8 with the party
 * coming up from 13x9.
 *
 * A frightened monster turns its back where it stands — which is what stops
 * it swinging — and spends twenty of its own walks getting away, never taking
 * a step that ends nearer the party. Cornered, it stands with its back turned
 * until the fright runs out. A blow ends it at once.
 */
@Category(NeedsGameData::class)
class WhatFearDrivesOffTest {

    private val level: Inf = runBlocking {
        val resources = ResourceRepositoryImpl()
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

    private val sub get() = level.subLevels[0]
    private val kinds get() = sub.monsters

    private val onTheLeft = MonsterSlot(17)
    private val onTheRight = MonsterSlot(16)

    /** Every die its lowest, save the hundred-sided one: no dodging, no save. */
    private val nothingEscapes = Dice { times, pips, modifier ->
        if (pips == 100) times * pips + modifier else times + modifier
    }

    /** Every die its highest: no dodging, and any save made. */
    private val everyDieHighest = Dice { times, pips, modifier -> times * pips + modifier }

    private val walking get() = MonsterPathing(
        stepping = MonsterStepping(level = 5, subLevel = sub, kinds = kinds),
        kinds = kinds,
    )

    private fun world(party: Location = Location(13, 9), facing: Direction = Direction.NORTH) =
        GameState(
            party = PartyState(party, facing),
            champions = List(6) { fighter() },
        ).arrivingAt(
            level = 5,
            places = level.monsterInstances,
            maz = sub.maz,
            kinds = kinds,
            dice = everyDieHighest,
        )

    /**
     * The same, with the clerics past talking. They stand waiting to see what
     * the party do, and rousing one rouses both.
     */
    private fun roused() = world().rousedBy(onTheLeft)

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
        flags = ChampionFlags(1),
    )

    private fun GameState.monster(slot: MonsterSlot) = monsters.single { it.index == slot }

    private fun GameState.with(slot: MonsterSlot, change: (MonsterInstance) -> MonsterInstance) =
        copy(monsters = monsters.map { if (it.index == slot) change(it) else it })

    /** One cleric alone on [at], the other taken off the floor. */
    private fun alone(at: Location, party: Location, change: (MonsterInstance) -> MonsterInstance) =
        world(party).copy(
            monsters = world(party).monsters
                .filter { it.index == onTheLeft }
                .map { change(it.copy(location = at)) },
        )

    private fun frightened(world: GameState, dice: Dice = nothingEscapes) =
        Frightening(kinds, dice).castAt(world)

    // --- the spell -----------------------------------------------------------

    @Test
    fun `it is aimed ahead and scatters sparks`() {
        assertTrue(Spell.FEAR.frightensWhatIsAhead)
        assertTrue(Spell.FEAR.throwsSparks)
        assertTrue(Spell.FEAR.givesTheCasterAway)
    }

    /**
     * Every creature on the square, not only the nearest: both clerics turn
     * their backs to the party, south of them, by facing north.
     */
    @Test
    fun `everything on the square ahead turns its back`() {
        val after = frightened(roused())

        listOf(onTheLeft, onTheRight).forEach { slot ->
            val cleric = after.monster(slot)
            assertEquals(MonsterInstance.WALKS_AFRAID, cleric.afraidFor, "$slot")
            assertEquals(Direction.NORTH, cleric.direction, "$slot should face away from the party")
            assertTrue(cleric.provoked, "$slot should be roused by it")
        }
    }

    @Test
    fun `one that saves is not frightened`() {
        val after = frightened(roused(), dice = everyDieHighest)

        assertTrue(after.monsters.none { it.afraid })
    }

    @Test
    fun `a kind that is never afraid is not frightened`() {
        val fearless = kinds.map { it.copy(immunities = MonsterImmunities(NEVER_AFRAID)) }

        val after = Frightening(fearless, nothingEscapes).castAt(roused())

        assertTrue(after.monsters.none { it.afraid })
    }

    /**
     * The fifth floor's clerics are the very case: they stand talking until
     * somebody hits one of them, and until then no fear reaches them.
     */
    @Test
    fun `the clerics are not frightened while they are still talking`() {
        val talking = world()
        assertTrue(talking.monster(onTheLeft).standingBy, "the clerics should start out talking")

        assertTrue(frightened(talking).monsters.none { it.afraid })
    }

    /** Held, or stone: neither can be sent running. */
    @Test
    fun `what is held or stone is not frightened`() {
        val held = frightened(roused().with(onTheLeft) { it.heldFor(5) })
        val stone = frightened(roused().with(onTheLeft) { it.turnedToStone() })

        assertFalse(held.monster(onTheLeft).afraid, "held")
        assertFalse(stone.monster(onTheLeft).afraid, "stone")
        assertTrue(stone.monster(onTheRight).afraid, "the one beside it was spared too")
    }

    /** Chased down and struck, it turns and fights. */
    @Test
    fun `a blow ends being afraid`() {
        val afraid = frightened(roused())

        val struck = afraid.monsterHurt(onTheLeft, Damage(1), kinds)

        assertFalse(struck.monster(onTheLeft).afraid)
        assertTrue(struck.monster(onTheRight).afraid, "the one not struck stays afraid")
    }

    // --- running away --------------------------------------------------------

    /**
     * Its back to the party, it does not swing: a monster swings only at the
     * square it faces. The same roused clerics swing when they are not afraid.
     */
    @Test
    fun `a monster with its back turned does not swing`() {
        assertTrue(swungThrough(roused()).struck.isNotEmpty(), "the clerics never swung at all")
        assertEquals(emptyList(), swungThrough(frightened(roused())).struck)
    }

    /**
     * Twenty walks, the last of which turns it back: so nineteen spent getting
     * away, none of them ending nearer the party than it began.
     */
    @Test
    fun `it walks away and never nearer, until the fright runs out`() {
        val party = Location(13, 10)
        var world = alone(Location(13, 9), party) { it.scaredOff(party) }

        repeat(MonsterInstance.WALKS_AFRAID - 1) { walk ->
            val before = world.monster(onTheLeft)
            world = MonstersTurn(kinds).begun(world, walking).world
            val after = world.monster(onTheLeft)

            assertTrue(
                party.blocksFrom(after.location) >= party.blocksFrom(before.location),
                "walk ${walk + 1} took it from ${before.location} nearer, to ${after.location}",
            )
            assertEquals(before.afraidFor - 1, after.afraidFor, "walk ${walk + 1}")
        }

        assertTrue(party.blocksFrom(world.monster(onTheLeft).location) > 1, "it never got away")

        world = MonstersTurn(kinds).begun(world, walking).world
        assertFalse(world.monster(onTheLeft).afraid, "the twentieth walk should end it")
    }

    /**
     * Cornered — at the closed end of a passage, the party in its only way
     * out — it stays where it is with its back to them, and the fright still
     * runs down.
     */
    @Test
    fun `cornered, it stands with its back turned`() {
        val (deadEnd, exit) = assertNotNull(aDeadEnd(), "level 5 has no dead end to corner it in")
        val world = alone(deadEnd, party = exit) { it.scaredOff(exit) }
        val facing = world.monster(onTheLeft).direction

        val after = MonstersTurn(kinds).begun(world, walking).world.monster(onTheLeft)

        assertEquals(deadEnd, after.location, "it went somewhere from a dead end")
        assertEquals(facing, after.direction, "it turned round")
        assertEquals(MonsterInstance.WALKS_AFRAID - 1, after.afraidFor)
    }

    // --- the fixture ---------------------------------------------------------

    private fun swungThrough(from: GameState): MonstersTurn.Taken {
        val turn = MonstersTurn(kinds, everyDieHighest)
        var world = turn.begun(from).world
        world = world.swingsCarriedOn()
        return turn.landed(world, world.landingThisFrame)
    }

    /** A square open on one side only, and the square through that side. */
    private fun aDeadEnd(): Pair<Location, Location>? {
        fun open(from: Location, way: Direction): Boolean {
            val to = way.oneStepFrom(from)
            if (to.x !in 0..31 || to.y !in 0..31) return false
            val leaving = sub.maz.square(from).getWall(way.transformWallSide(WallSide.NORTH))
            val entering = sub.maz.square(to).getWall(way.transformWallSide(WallSide.SOUTH))
            return sub.canBeWalkedOnto(leaving) && sub.canBeWalkedOnto(entering)
        }

        for (y in 1..30) for (x in 1..30) {
            val here = Location(x, y)
            val ways = Direction.entries.filter { open(here, it) }
            if (ways.size == 1) return here to ways.single().oneStepFrom(here)
        }
        return null
    }

    private companion object {
        /** The bit that marks a kind as one nothing frightens. */
        const val NEVER_AFRAID = 0x4
    }
}
