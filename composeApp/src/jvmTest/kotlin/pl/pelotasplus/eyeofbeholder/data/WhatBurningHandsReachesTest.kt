package pl.pelotasplus.eyeofbeholder.data

import kotlinx.coroutines.runBlocking
import org.junit.experimental.categories.Category
import pl.pelotasplus.eyeofbeholder.NeedsGameData
import pl.pelotasplus.eyeofbeholder.data.model.Abilities
import pl.pelotasplus.eyeofbeholder.data.model.Ability
import pl.pelotasplus.eyeofbeholder.data.model.ArmorClass
import pl.pelotasplus.eyeofbeholder.data.model.BurningHands
import pl.pelotasplus.eyeofbeholder.data.model.CarrySlot
import pl.pelotasplus.eyeofbeholder.data.model.Champion
import pl.pelotasplus.eyeofbeholder.data.model.ChampionFlags
import pl.pelotasplus.eyeofbeholder.data.model.CharacterClass
import pl.pelotasplus.eyeofbeholder.data.model.ClassLevel
import pl.pelotasplus.eyeofbeholder.data.model.Dice
import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.Food
import pl.pelotasplus.eyeofbeholder.data.model.GameState
import pl.pelotasplus.eyeofbeholder.data.model.HarmKind
import pl.pelotasplus.eyeofbeholder.data.model.HitPoints
import pl.pelotasplus.eyeofbeholder.data.model.Inf
import pl.pelotasplus.eyeofbeholder.data.model.ItemIndex
import pl.pelotasplus.eyeofbeholder.data.model.ItemTypes
import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.MonsterImmunities
import pl.pelotasplus.eyeofbeholder.data.model.MonsterInstance
import pl.pelotasplus.eyeofbeholder.data.model.MonsterProperty
import pl.pelotasplus.eyeofbeholder.data.model.MonsterSlot
import pl.pelotasplus.eyeofbeholder.data.model.MonsterTypeId
import pl.pelotasplus.eyeofbeholder.data.model.PartyState
import pl.pelotasplus.eyeofbeholder.data.model.PortraitId
import pl.pelotasplus.eyeofbeholder.data.model.Spell
import pl.pelotasplus.eyeofbeholder.data.model.SquarePlace
import pl.pelotasplus.eyeofbeholder.data.model.TrackIndex
import pl.pelotasplus.eyeofbeholder.data.model.ViewPlace
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Burning hands, read at a square with something standing in every place of
 * it.
 *
 * The spell burns one square and decides what it reaches by where on that
 * square a creature stands, so the tests here are mostly about placing things
 * and counting what came off them. What makes it worth this many is that one
 * of the four facings reaches a different set from the other three.
 */
@Category(NeedsGameData::class)
class WhatBurningHandsReachesTest {

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

    /**
     * One kind, so that where a thing stands is the only thing that differs,
     * and nothing it is immune to — what a hide turns aside is tested on its
     * own below, and left out of the counting everywhere else.
     */
    private val oneKind: List<MonsterProperty>
        get() = listOf(level.subLevels[0].monsters.first().copy(immunities = MonsterImmunities(0)))

    // --- the spell as it is written -------------------------------------------

    @Test
    fun `it burns the square ahead, runs for no time, and shows nothing over the party`() {
        assertTrue(Spell.BURNING_HANDS.burnsTheSquareAhead)
        assertNull(Spell.BURNING_HANDS.lasts, "nothing about it goes on after the words")
        assertNull(Spell.BURNING_HANDS.throws, "nothing of it flies")
        assertFalse(Spell.BURNING_HANDS.throwsSparks)
        assertFalse(Spell.BURNING_HANDS.sparksOverTheParty)
        assertFalse(Spell.BURNING_HANDS.sparksOverTheCaster)
    }

    /** Its own track, and not the one the other first-level spells share. */
    @Test
    fun `it is heard as its own noise`() {
        assertEquals(TrackIndex(87), Spell.BURNING_HANDS.heardAs)
        assertEquals(TrackIndex(92), Spell.ARMOUR.heardAs)
    }

    @Test
    fun `it is fire as well as magic`() {
        assertEquals(setOf(HarmKind.MAGIC, HarmKind.FIRE), BurningHands.HURTS_BY)
    }

    // --- where it reaches -----------------------------------------------------

    /**
     * Facing east, south or west it reaches every place of the square: all
     * four corners and the middle, each once.
     */
    @Test
    fun `it reaches everything on the square ahead`() {
        listOf(Direction.EAST, Direction.SOUTH, Direction.WEST).forEach { facing ->
            val left = whatIsLeftOf(burning(facing))

            ViewPlace.entries.forEach { seen ->
                assertEquals(
                    ONE_BURN,
                    left[seen],
                    "facing $facing, the thing at $seen should have burnt once",
                )
            }
        }
    }

    /**
     * Facing north it misses the far corner on the party's left entirely and
     * burns the near corner on their left twice — which is the game's, and
     * the whole reason the places it reaches are a table rather than a rule.
     */
    @Test
    fun `facing north it misses one corner and burns another twice`() {
        val left = whatIsLeftOf(burning(Direction.NORTH))

        assertEquals(UNTOUCHED, left[ViewPlace.FAR_LEFT], "it should not have been reached at all")
        assertEquals(TWO_BURNS, left[ViewPlace.NEAR_LEFT], "it should have burnt twice")

        listOf(ViewPlace.FAR_RIGHT, ViewPlace.NEAR_RIGHT, ViewPlace.MIDDLE).forEach { seen ->
            assertEquals(ONE_BURN, left[seen], "the thing at $seen should have burnt once")
        }
    }

    /** And the one it burns twice is reported twice rather than once. */
    @Test
    fun `the corner it reaches twice is named twice`() {
        val burned = BurningHands(oneKind, itemTypes, rolling(A_SMALL_ROLL))
            .castBy(worldFacing(Direction.NORTH), OFF_A_SCROLL)
            .burned

        val nearLeft = slotAt(ViewPlace.NEAR_LEFT, Direction.NORTH)

        assertEquals(PLACES_IT_REACHES, burned.size)
        assertEquals(2, burned.count { it == nearLeft })
    }

    /** One square, and nothing on any other. */
    @Test
    fun `it reaches neither the party's own square nor the one past it`() {
        val elsewhere = listOf(
            standing(MonsterSlot(0), Location(13, 9), SquarePlace.MIDDLE),
            standing(MonsterSlot(1), Location(13, 7), SquarePlace.MIDDLE),
        )
        val world = worldFacing(Direction.NORTH).copy(monsters = elsewhere)

        val struck = BurningHands(oneKind, itemTypes, rolling(A_SMALL_ROLL)).castBy(world, OFF_A_SCROLL)

        assertEquals(emptyList(), struck.burned)
        assertTrue(struck.world.monsters.all { it.hitPoints.current == FULL_HEALTH })
    }

    // --- what it takes off ----------------------------------------------------

    /**
     * A die of three and two for every level of the caster. The level buys the
     * flat part and not another die, so the spread is the same at ninth level
     * as at first and only the floor moves.
     */
    @Test
    fun `a level is worth two points and never another die`() {
        assertEquals(1 + 2, burnFor(casterLevel = 1, rolled = 1))
        assertEquals(3 + 2, burnFor(casterLevel = 1, rolled = 3))

        assertEquals(1 + 18, burnFor(casterLevel = OFF_A_SCROLL, rolled = 1))
        assertEquals(3 + 18, burnFor(casterLevel = OFF_A_SCROLL, rolled = 3))
    }

    /** A creature that makes its throw against a spell takes half of it. */
    @Test
    fun `a made throw is worth half of it`() {
        assertEquals(
            (3 + 18) / 2,
            burnFor(casterLevel = OFF_A_SCROLL, rolled = 3, savingThrow = A_MADE_THROW),
        )
    }

    /** And one quick enough is clear of the flame, though it was cast over it. */
    @Test
    fun `a quick enough creature takes nothing`() {
        val quick = oneKind.map { it.copy(dmgModifierEvade = A_WISPS_EVADE) }

        val struck = BurningHands(quick, itemTypes, rolling(A_BIG_ROLL, evade = A_LUCKY_DODGE))
            .castBy(worldFacing(Direction.SOUTH), OFF_A_SCROLL)

        assertTrue(
            struck.world.monsters.all { it.hitPoints.current == FULL_HEALTH },
            "something burnt a creature that was out of the way",
        )
    }

    /**
     * Fire, and so a creature that lives in fire takes none of it. This is the
     * difference between a spell made of fire and a weapon that merely looks
     * like one — a flame blade reaches the same creature in full.
     */
    @Test
    fun `a creature fire passes by takes nothing from it`() {
        assertEquals(0, burnFor(casterLevel = OFF_A_SCROLL, rolled = 3, immunities = SHRUGS_OFF_FIRE))
    }

    @Test
    fun `and neither does one magic passes by`() {
        assertEquals(0, burnFor(casterLevel = OFF_A_SCROLL, rolled = 3, immunities = SHRUGS_OFF_MAGIC))
    }

    /** A creature that takes magic by halves takes this by halves too. */
    @Test
    fun `a creature weak magic barely touches takes half`() {
        assertEquals(
            (3 + 18) / 2,
            burnFor(casterLevel = OFF_A_SCROLL, rolled = 3, immunities = TAKES_MAGIC_BY_HALVES),
        )
    }

    /** The square ahead is the square ahead, closed door or not. */
    @Test
    fun `a wall between is no shelter`() {
        val shut = worldFacing(Direction.NORTH)

        // 13x8 has the clerics' own walls around it; nothing about the spell
        // consults them, which is what this says.
        val struck = BurningHands(oneKind, itemTypes, rolling(A_BIG_ROLL)).castBy(shut, OFF_A_SCROLL)

        assertEquals(PLACES_IT_REACHES, struck.burned.size)
    }

    // --- the fixture ----------------------------------------------------------

    /**
     * The square ahead with something in every place of it, burnt by a ninth
     * level reading with the smallest roll — small enough that nothing dies
     * and the corner reached twice is still there to be reached again.
     */
    private fun burning(facing: Direction): GameState =
        BurningHands(oneKind, itemTypes, rolling(A_SMALL_ROLL))
            .castBy(worldFacing(facing), OFF_A_SCROLL)
            .world

    /** What each of the five has left, named by where the party see it. */
    private fun whatIsLeftOf(world: GameState): Map<ViewPlace, Int> =
        ViewPlace.entries.associateWith { seen ->
            world.monsters.first { it.index == slotAt(seen, world.party.facing) }.hitPoints.current
        }

    /** Where the party see a place is what names its slot, so the rows line up. */
    private fun slotAt(seen: ViewPlace, facing: Direction) =
        MonsterSlot(seen.onASquareFacing(facing).ordinal)

    private fun worldFacing(facing: Direction): GameState {
        val ahead = facing.oneStepFrom(Location(13, 9))

        return GameState(
            party = PartyState(Location(13, 9), facing),
            champions = listOf(fighter(), fighter()),
            monsters = PLACES.map { place -> standing(MonsterSlot(place.ordinal), ahead, place) },
        )
    }

    private fun standing(slot: MonsterSlot, at: Location, place: SquarePlace) = MonsterInstance(
        index = slot,
        unit = 0,
        location = at,
        place = place,
        direction = Direction.SOUTH,
        type = MonsterTypeId(oneKind.first().id),
        gfxIndex = 0,
        mode = 0,
        pause = 0,
        weapon = 0,
        pocketItem = 0,
        hitPoints = HitPoints(FULL_HEALTH, FULL_HEALTH),
    )

    /** What one standing in the middle of the square ahead loses. */
    private fun burnFor(
        casterLevel: Int,
        rolled: Int,
        savingThrow: Int = A_FAILED_THROW,
        immunities: Int? = null,
    ): Int {
        val kinds = immunities?.let { oneKind.map { kind -> kind.copy(immunities = MonsterImmunities(it)) } }
            ?: oneKind
        val alone = standing(MonsterSlot(0), Location(13, 8), SquarePlace.MIDDLE)
        val world = worldFacing(Direction.NORTH).copy(monsters = listOf(alone))

        val struck = BurningHands(kinds, itemTypes, rolling(rolled, savingThrow))
            .castBy(world, casterLevel)

        return FULL_HEALTH - struck.world.monsters.first().hitPoints.current
    }

    /**
     * Dice that answer each of the spell's three questions separately, told
     * apart by the die being thrown: the harm is a die of three, a throw
     * against a spell is a die of twenty, and being quick enough is out of a
     * hundred.
     */
    private fun rolling(
        harm: Int,
        savingThrow: Int = A_FAILED_THROW,
        evade: Int = NEVER_QUICK_ENOUGH,
    ) = Dice { times, pips, modifier ->
        when (pips) {
            A_DIE_OF_THREE -> harm * times + modifier
            A_DIE_OF_TWENTY -> savingThrow
            OUT_OF_A_HUNDRED -> evade
            else -> times * pips + modifier
        }
    }

    private fun fighter() = Champion(
        name = "Anselm",
        portrait = PortraitId(0),
        abilities = Abilities(strength = Ability(10, 10), dexterity = Ability(10, 10)),
        hitPoints = HitPoints(40, 40),
        armorClass = ArmorClass(10),
        food = Food(100),
        characterClass = CharacterClass.MAGE,
        levels = listOf(ClassLevel(9, XpPoints(0))),
        carrying = List(CarrySlot.ALL_OF_THEM) { ItemIndex(ItemIndex.NOTHING) },
        flags = ChampionFlags(1),
    )

    private companion object {
        /** A scroll is always read at ninth-level strength. */
        const val OFF_A_SCROLL = 9

        /**
         * Enough that nothing on the square dies of five burns, so what is
         * left of each says how many times it was reached.
         */
        const val FULL_HEALTH = 500

        val PLACES = listOf(
            SquarePlace.NORTH_WEST,
            SquarePlace.NORTH_EAST,
            SquarePlace.SOUTH_WEST,
            SquarePlace.SOUTH_EAST,
            SquarePlace.MIDDLE,
        )

        /** Five places, and the one facing that reaches one of them twice. */
        const val PLACES_IT_REACHES = 5

        const val A_SMALL_ROLL = 1
        const val A_BIG_ROLL = 3

        val ONE_BURN = FULL_HEALTH - (A_SMALL_ROLL + 2 * OFF_A_SCROLL)
        val TWO_BURNS = FULL_HEALTH - 2 * (A_SMALL_ROLL + 2 * OFF_A_SCROLL)
        const val UNTOUCHED = FULL_HEALTH

        const val A_FAILED_THROW = 1
        const val A_MADE_THROW = 20
        const val NEVER_QUICK_ENOUGH = 100
        const val A_LUCKY_DODGE = 1

        /** How quick the ninth floor's will-o'-wisps are, out of a hundred. */
        const val A_WISPS_EVADE = 99

        const val SHRUGS_OFF_FIRE = 0x800
        const val SHRUGS_OFF_MAGIC = 0x10
        const val TAKES_MAGIC_BY_HALVES = 0x2000

        const val A_DIE_OF_THREE = 3
        const val A_DIE_OF_TWENTY = 20
        const val OUT_OF_A_HUNDRED = 100
    }
}
