package pl.pelotasplus.eyeofbeholder.data

import kotlinx.coroutines.runBlocking
import org.junit.experimental.categories.Category
import pl.pelotasplus.eyeofbeholder.NeedsGameData
import pl.pelotasplus.eyeofbeholder.data.model.Champion
import pl.pelotasplus.eyeofbeholder.data.model.ChampionFlags
import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.Food
import pl.pelotasplus.eyeofbeholder.data.model.GameState
import pl.pelotasplus.eyeofbeholder.data.model.HitPoints
import pl.pelotasplus.eyeofbeholder.data.model.Ticks
import pl.pelotasplus.eyeofbeholder.data.model.Holding
import pl.pelotasplus.eyeofbeholder.data.model.WhatABlowLeaves
import pl.pelotasplus.eyeofbeholder.data.model.Inf
import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.MonsterMode
import pl.pelotasplus.eyeofbeholder.data.model.MonsterSlot
import pl.pelotasplus.eyeofbeholder.data.model.MonsterStepping
import pl.pelotasplus.eyeofbeholder.data.model.Night
import pl.pelotasplus.eyeofbeholder.data.model.PartySlot
import pl.pelotasplus.eyeofbeholder.data.model.PartyState
import pl.pelotasplus.eyeofbeholder.data.model.Rest
import pl.pelotasplus.eyeofbeholder.data.model.RestQuestion
import pl.pelotasplus.eyeofbeholder.data.model.Resting
import pl.pelotasplus.eyeofbeholder.data.model.WatchingTheRest
import pl.pelotasplus.eyeofbeholder.data.model.aStretchSlept
import pl.pelotasplus.eyeofbeholder.data.model.anHourSlept
import pl.pelotasplus.eyeofbeholder.data.model.anybodyStillHurt
import pl.pelotasplus.eyeofbeholder.data.model.HOURS_A_MENDED_POINT
import pl.pelotasplus.eyeofbeholder.data.model.HOURS_A_STARVED_POINT
import pl.pelotasplus.eyeofbeholder.data.model.isBesideTheParty
import pl.pelotasplus.eyeofbeholder.data.model.night
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
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The party bedding down where they stand, in level 5's temple.
 *
 * A stretch of eight hours' sleep is worth a hit point and costs five food.
 * Whether anything stops them is played out rather than measured — the
 * monsters take their turns first, and again every ten minutes, and the rest
 * is refused or broken if any of them ends up beside the party.
 */
@Category(NeedsGameData::class)
class RestingTest {

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

    private val sub get() = level.subLevels[0]
    private val kinds get() = sub.monsters

    private fun resting(theyWalk: Boolean = false) = Resting(
        kinds = kinds,
        stepping = MonsterStepping(level = 5, subLevel = sub, kinds = kinds).takeIf { theyWalk },
    )

    private fun walking() =
        pl.pelotasplus.eyeofbeholder.data.model.MonsterPathing(
            stepping = MonsterStepping(level = 5, subLevel = sub, kinds = kinds),
            kinds = kinds,
        )

    private fun champion(food: Int, hurt: HitPoints) = Champion.NOBODY.copy(
        name = "Test", hitPoints = hurt, food = Food(food), flags = ChampionFlags(1),
    )

    // --- what an hour does to a grip -----------------------------------------

    /** As long as a champion is held for when it takes hold. */
    private val aFreshGrip = WhatABlowLeaves.PARALYSIS.holdsFor!!.value

    /** What an hour of sleep costs somebody poisoned, which the engine fixes at ten. */
    private val VENOM_AN_HOUR = 10

    /**
     * Sleeping is not a cure. It is time passing, and whatever has hold of a
     * champion is on a clock — so the party get up with those clocks wound
     * forward by however long they lay there.
     *
     * An hour is a dozen times longer than the longest grip, so a party who
     * lie down for an hour get up free of one.
     */
    @Test
    fun `an hour of sleep outlasts anything holding somebody`() {
        val held = heldWorld()

        assertEquals(emptyList(), held.anHourSlept().holding, "they slept and something still had hold of them")
    }

    /** Ten minutes is not an hour: the clocks move by the hour. */
    @Test
    fun `and less than an hour lets nobody go`() {
        var night = Night(heldWorld())
        repeat(Night.STEPS_AN_HOUR - 1) { night = night.stepped(types = null, dice = { _, _, m -> m }).night }

        assertEquals(heldWorld().holding, night.world.holding, "less than an hour let a grip go")
    }

    private fun heldWorld() = world(champion(food = 100, hurt = HitPoints(10, 20))).copy(
        holding = listOf(
            Holding(PartySlot(0), WhatABlowLeaves.PARALYSIS, ticksLeft = aFreshGrip),
        ),
    )

    /**
     * Venom is the one thing sleeping makes worse. It goes on taking what it
     * takes while they lie there, and charges ten an hour for the privilege.
     */
    @Test
    fun `sleeping poisoned costs ten an hour`() {
        val hale = champion(food = 100, hurt = HitPoints(100, 200))

        val clean = world(hale).anHourSlept()
        val bitten = world(hale.poisoned(true)).anHourSlept()

        assertEquals(
            VENOM_AN_HOUR,
            clean.champions[0].hitPoints.current - bitten.champions[0].hitPoints.current,
            "the venom charged something other than ten for the hour",
        )
    }

    /** Whatever the night costs, it is the venom charging and nothing else. */
    @Test
    fun `and nothing at all once the venom is washed out`() {
        val hale = champion(food = 100, hurt = HitPoints(100, 200))

        assertEquals(
            world(hale).anHourSlept().champions[0].hitPoints.current,
            world(hale.poisoned(true).poisoned(false)).anHourSlept().champions[0].hitPoints.current,
            "somebody cured before lying down was charged anyway",
        )
    }

    /** Every poisoned champion pays it, and pays it separately. */
    @Test
    fun `each of them is charged for their own venom`() {
        val hale = champion(food = 100, hurt = HitPoints(100, 200))

        val night = world(hale.poisoned(true), hale.poisoned(true), hale).anHourSlept()
        val clean = world(hale, hale, hale).anHourSlept()

        assertEquals(
            listOf(VENOM_AN_HOUR, VENOM_AN_HOUR, 0),
            clean.champions.zip(night.champions).map { (was, now) ->
                was.hitPoints.current - now.hitPoints.current
            },
            "the venom was charged to the wrong people, or charged once for the party",
        )
    }

    /** There is nothing further to take from somebody past raising. */
    @Test
    fun `somebody past raising is beyond the venom`() {
        val gone = champion(food = 100, hurt = HitPoints(Champion.BEYOND_RAISING, 200)).poisoned(true)

        assertEquals(
            Champion.BEYOND_RAISING,
            world(gone).anHourSlept().champions[0].hitPoints.current,
            "the venom went on taking from somebody it could not reach",
        )
    }

    /** The party at [at] with the level's own monsters put where this test wants. */
    private fun world(
        vararg party: Champion,
        at: Location = Location(13, 12),
        monstersOn: List<Location> = emptyList(),
        forbidden: Boolean = false,
    ): GameState {
        val placed = GameState(party = PartyState(at, Direction.NORTH))
            .arrivingAt(level = 5, places = level.monsterInstances, maz = sub.maz, kinds = kinds)

        val one = placed.monsters.first()
        return placed.copy(
            party = PartyState(at, Direction.NORTH),
            champions = party.toList(),
            monsters = monstersOn.mapIndexed { i, on ->
                one.copy(index = MonsterSlot(i), location = on)
            },
            preventRest = forbidden,
        )
    }

    // --- what stops a rest ----------------------------------------------------

    @Test
    fun `a script can forbid rest here`() {
        val forbidden = world(champion(100, HitPoints(5, 20)), forbidden = true)
        assertEquals(Rest.NotHere, resting().begin(forbidden))
    }

    @Test
    fun `a monster already beside the party stops them`() {
        val hurt = world(champion(100, HitPoints(5, 20)), monstersOn = listOf(Location(13, 11)))
            .let { it.copy(monsters = it.monsters.map { m -> m.takingUpTheHunt() }) }

        assertIs<Rest.SomethingIsNear>(resting().begin(hurt))
    }

    /** Unless it is standing by until it sees them, which it has not yet. */
    @Test
    fun `one standing by beside them does not`() {
        val beside = world(champion(100, HitPoints(5, 20)), monstersOn = listOf(Location(13, 11)))
            .let { it.copy(monsters = it.monsters.map { m -> m.copy(mode = MonsterMode.WAITING_TO_SEE.asWritten) }) }

        assertIs<Rest.Slept>(resting().begin(beside))
    }

    /**
     * Standing still, a monster a few squares off is no trouble at all: the
     * party bed down.
     */
    @Test
    fun `a monster that cannot come near does not stop them`() {
        val hurt = world(champion(100, HitPoints(5, 20)), monstersOn = listOf(Location(13, 8)))

        assertIs<Rest.Slept>(resting(theyWalk = false).begin(hurt))
    }

    /**
     * The point of playing it out rather than measuring it. The same monster,
     * the same distance — but let it walk and it arrives while the party are
     * settling, and there is no sleeping through that.
     */
    @Test
    fun `one that can walk to them in those few turns does`() {
        val hurt = world(champion(100, HitPoints(5, 20)), monstersOn = listOf(Location(13, 9)))
            .let { it.copy(monsters = it.monsters.map { m -> m.copy(provoked = true) }) }

        val stopped = resting(theyWalk = true).begin(hurt, walking())

        assertIs<Rest.SomethingIsNear>(stopped)
        assertTrue(
            stopped.world.monsters.any { stopped.world.isBesideTheParty(it) },
            "it never actually got near",
        )
    }

    /**
     * Nothing walks that was not given a way to walk. A rest is not the place
     * to overrule that: monsters held where they were placed stay there, rather
     * than coming after the party the moment the party lie down.
     */
    @Test
    fun `monsters held in place do not stir while the party settle`() {
        val held = world(champion(100, HitPoints(5, 20)), monstersOn = listOf(Location(13, 9)))
            .let { it.copy(monsters = it.monsters.map { m -> m.copy(provoked = true) }) }

        val slept = resting(theyWalk = true).begin(held, walking = null)

        val after = assertIs<Rest.Slept>(slept).world.monsters.first()
        assertEquals(Location(13, 9), Location(after.x, after.y), "it walked while rooted")
        assertEquals(null, after.striking, "it swung while rooted")
    }

    /** Whatever walked while they settled stays where it walked, slept or not. */
    @Test
    fun `the monsters keep the ground they covered`() {
        val hurt = world(champion(100, HitPoints(5, 20)), monstersOn = listOf(Location(13, 9)))
            .let { it.copy(monsters = it.monsters.map { m -> m.copy(provoked = true) }) }

        val after = resting(theyWalk = true).begin(hurt, walking())

        val moved = assertIs<Rest.SomethingIsNear>(after).world.monsters.first()
        assertTrue(
            Location(moved.x, moved.y) != Location(13, 9),
            "it stood still through five turns of walking",
        )
    }

    /**
     * The floor does not hold still while they sleep: the monsters have their
     * turns every ten minutes of it, and are measured against the party each
     * time, the same way as when the party lay down. One hunting three squares
     * off, walled from them, is seen to keep moving through the night.
     */
    @Test
    fun `the monsters keep moving through the rest`() {
        val hurt = world(champion(100, HitPoints(19, 20)), monstersOn = listOf(Location(16, 12)))
            .let { it.copy(monsters = it.monsters.map { m -> m.takingUpTheHunt() }) }
        val seenOn = mutableSetOf<Location>()
        val watching = object : WatchingTheRest {
            override suspend fun asked(question: RestQuestion, hours: Int) = true
            override suspend fun said(words: String) = Unit
            override suspend fun anHourPassed(hours: Int) = Unit
            override suspend fun stepped(world: GameState) {
                world.monsters.first().let { seenOn += Location(it.x, it.y) }
            }
        }

        val woke = assertIs<Rest.Woke>(runBlocking { resting(theyWalk = true).night(hurt, walking(), watching) })

        assertEquals(HOURS_A_MENDED_POINT, woke.hours)
        assertTrue(seenOn.size > 1, "it stood where it was all night")
    }

    // --- how far a monster counts as beside them ------------------------------

    @Test
    fun `beside means within arm's reach on the game's own measure`() {
        fun withOneOn(at: Location) = world(
            champion(100, HitPoints(20, 20)),
            monstersOn = listOf(at),
        ).let { it to it.monsters.first() }

        // the party stand on 13x12: straight beside them, and diagonally
        listOf(Location(13, 11), Location(12, 11)).forEach { at ->
            val (world, monster) = withOneOn(at)
            assertTrue(world.isBesideTheParty(monster), "$at is not counted as beside them")
        }

        val (further, two) = withOneOn(Location(13, 10))
        assertTrue(!further.isBesideTheParty(two), "two squares off is not beside them")
    }

    // --- a stretch of sleep ---------------------------------------------------

    @Test
    fun `a stretch of sleep mends a hit point and eats five`() {
        val after = world(champion(100, HitPoints(10, 100))).aStretchSlept(HOURS_A_MENDED_POINT, null).first

        assertEquals(11, after.champions.first().hitPoints.current)
        assertEquals(95, after.champions.first().food.value)
    }

    @Test
    fun `mending stops at full health`() {
        val after = world(champion(100, HitPoints(20, 20))).aStretchSlept(HOURS_A_MENDED_POINT, null).first

        assertEquals(20, after.champions.first().hitPoints.current, "mended past full")
    }

    /**
     * Being down is not being past mending. A champion at nothing left is
     * unconscious rather than gone, and a rest brings them round — only ten
     * below is past what sleep can do.
     */
    @Test
    fun `a champion at nothing left is mended by sleeping`() {
        val down = world(champion(100, HitPoints(0, 82)))

        assertTrue(down.anybodyStillHurt, "nobody is hurt with one of them at nothing")
        assertEquals(1, down.aStretchSlept(HOURS_A_MENDED_POINT, null).first.champions.first().hitPoints.current)
    }

    /** Past raising is past a bed, though: that wants a cleric. */
    @Test
    fun `a champion past raising is not mended by sleeping`() {
        val gone = world(champion(100, HitPoints(Champion.BEYOND_RAISING, 82)))

        assertTrue(!gone.anybodyStillHurt, "a rest counted somebody past raising as mendable")
        assertEquals(
            Champion.BEYOND_RAISING,
            gone.aStretchSlept(HOURS_A_MENDED_POINT, null).first.champions.first().hitPoints.current,
        )
    }

    @Test
    fun `eating fills a stomach and no further`() {
        val fed = world(champion(80, HitPoints(20, 20))).championFed(PartySlot(0), by = 50)
        assertEquals(100, fed.champions.first().food.value, "ate past full")
    }

    // --- starving through a rest ----------------------------------------------

    /**
     * Eating the last of it is when the party are asked whether to sleep on,
     * the five that empties a stomach included; a stretch of an empty one that
     * costs nothing is not asked about again.
     */
    @Test
    fun `running out of food is a moment and not a state`() {
        val (empty, emptied) = world(champion(5, HitPoints(5, 400))).aStretchSlept(HOURS_A_MENDED_POINT, null)

        assertEquals(0, empty.champions.first().food.value)
        assertTrue(emptied, "their last mouthful went unnoticed")
        assertTrue(!empty.aStretchSlept(2 * HOURS_A_MENDED_POINT, null).second, "it counted an empty stomach as emptying again")
    }

    /**
     * Sleeping on an empty stomach costs rather than mends: the stretch that
     * ends a day takes a hit point, and that is asked about too.
     */
    @Test
    fun `a day asleep on an empty stomach costs a hit point`() {
        val hungry = world(champion(0, HitPoints(10, 20)))

        val (mid, askedMid) = hungry.aStretchSlept(HOURS_A_MENDED_POINT, null)
        val (day, askedAtDay) = hungry.aStretchSlept(HOURS_A_STARVED_POINT, null)

        assertEquals(10, mid.champions.first().hitPoints.current, "an empty stomach mended something")
        assertTrue(!askedMid)
        assertEquals(9, day.champions.first().hitPoints.current)
        assertTrue(askedAtDay)
    }

    // --- growing hungry as they go ------------------------------------------

    /**
     * Time empties a stomach, and it costs nothing on its own — an empty one
     * is only paid for by sleeping on it.
     */
    @Test
    fun `time makes the party hungrier`() {
        val fed = world(champion(100, HitPoints(20, 20)))

        val later = fed.hungrier()

        assertEquals(99, later.champions.first().food.value)
        assertEquals(20, later.champions.first().hitPoints.current, "growing hungry hurt them")
    }

    /**
     * A mouthful a minute, so a full stomach is an hour and three quarters.
     *
     * The number is the game's own. It is only usable because the clocks stop
     * when nobody is looking — on a clock that ran regardless, an afternoon's
     * work in another tab emptied a party's stores, which is why this was
     * once counted in squares walked instead.
     */
    @Test
    fun `a mouthful lasts about a minute`() {
        assertEquals(Ticks(1080), GameState.A_MOUTHFUL_LASTS)
        assertEquals(59_400, GameState.A_MOUTHFUL_LASTS.inMilliseconds)

        val wholeStomach = GameState.A_MOUTHFUL_LASTS.inMilliseconds * 100
        assertEquals(99, wholeStomach / 60_000, "a full stomach is about a hundred minutes")
    }

    /** An empty stomach cannot be emptied further. */
    @Test
    fun `hunger stops at nothing left`() {
        val empty = world(champion(0, HitPoints(20, 20)))

        assertEquals(0, empty.hungrier().champions.first().food.value)
    }
}
