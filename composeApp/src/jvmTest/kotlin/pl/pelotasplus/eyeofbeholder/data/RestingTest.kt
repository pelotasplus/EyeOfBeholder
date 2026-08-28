package pl.pelotasplus.eyeofbeholder.data

import kotlinx.coroutines.runBlocking
import pl.pelotasplus.eyeofbeholder.data.model.Champion
import pl.pelotasplus.eyeofbeholder.data.model.ChampionFlags
import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.Food
import pl.pelotasplus.eyeofbeholder.data.model.GameState
import pl.pelotasplus.eyeofbeholder.data.model.HitPoints
import pl.pelotasplus.eyeofbeholder.data.model.Inf
import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.MonsterInstance
import pl.pelotasplus.eyeofbeholder.data.model.MonsterSlot
import pl.pelotasplus.eyeofbeholder.data.model.MonsterStepping
import pl.pelotasplus.eyeofbeholder.data.model.PartySlot
import pl.pelotasplus.eyeofbeholder.data.model.PartyState
import pl.pelotasplus.eyeofbeholder.data.model.Rest
import pl.pelotasplus.eyeofbeholder.data.model.Resting
import pl.pelotasplus.eyeofbeholder.data.model.anybodyCanStillMend
import pl.pelotasplus.eyeofbeholder.data.model.anybodyStillHurt
import pl.pelotasplus.eyeofbeholder.data.model.anybodyStarving
import pl.pelotasplus.eyeofbeholder.data.model.starvedADay
import pl.pelotasplus.eyeofbeholder.data.model.somebodyRanOutOfFood
import pl.pelotasplus.eyeofbeholder.data.model.HOURS_A_MENDED_POINT
import pl.pelotasplus.eyeofbeholder.data.model.sleptAnHour
import pl.pelotasplus.eyeofbeholder.data.model.isBesideTheParty
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
 * A step of sleep is worth a hit point and costs five food: they sleep until
 * nobody is hurt or the food runs out. Whether anything stops them is played
 * out rather than measured — the monsters take their turns first, and the rest
 * is refused if any of them ends up beside the party.
 */
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
        assertEquals(Rest.NotHere, resting().rest(forbidden))
    }

    @Test
    fun `a monster already beside the party stops them`() {
        val hurt = world(champion(100, HitPoints(5, 20)), monstersOn = listOf(Location(13, 11)))

        assertIs<Rest.SomethingIsNear>(resting().rest(hurt))
    }

    /**
     * Standing still, a monster a few squares off is no trouble at all: the
     * party bed down and mend.
     */
    @Test
    fun `a monster that cannot come near does not stop them`() {
        val hurt = world(champion(100, HitPoints(5, 20)), monstersOn = listOf(Location(13, 8)))

        assertIs<Rest.Slept>(resting(theyWalk = false).rest(hurt))
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

        val stopped = resting(theyWalk = true).rest(hurt, walking())

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

        val slept = resting(theyWalk = true).rest(held, walking = null)

        val after = assertIs<Rest.Slept>(slept).world.monsters.first()
        assertEquals(Location(13, 9), Location(after.x, after.y), "it walked while rooted")
        assertEquals(null, after.striking, "it swung while rooted")
    }

    /** Whatever walked while they settled stays where it walked, slept or not. */
    @Test
    fun `the monsters keep the ground they covered`() {
        val hurt = world(champion(100, HitPoints(5, 20)), monstersOn = listOf(Location(13, 9)))
            .let { it.copy(monsters = it.monsters.map { m -> m.copy(provoked = true) }) }

        val after = resting(theyWalk = true).rest(hurt, walking())

        val moved = assertIs<Rest.SomethingIsNear>(after).world.monsters.first()
        assertTrue(
            Location(moved.x, moved.y) != Location(13, 9),
            "it stood still through five turns of walking",
        )
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

    // --- the sleep itself -----------------------------------------------------

    @Test
    fun `sleep mends hit points and eats food`() {
        val slept = resting().rest(world(champion(100, HitPoints(15, 20))))

        val who = assertIs<Rest.Slept>(slept).world.champions.first()
        assertEquals(20, who.hitPoints.current, "not fully mended")
        // one stretch carries them the last five, and eats one meal doing it
        assertEquals(98, who.food.value, "a stretch of sleep costs a meal")
    }

    /**
     * A rest does not go on for ever on an empty stomach: what the food buys is
     * all the mending there is, and a champion out of food wakes still hurt.
     */
    @Test
    fun `food running out stops the mending`() {
        // hurt far past what the food can mend, so the food is what runs out
        val slept = resting().rest(world(champion(4, HitPoints(2, 400))))

        val who = assertIs<Rest.Slept>(slept).world.champions.first()
        assertEquals(0, who.food.value)
        assertEquals(66, who.hitPoints.current, "four food is two stretches and no more")
    }

    /**
     * Being down is not being past mending. A champion at nothing left is
     * unconscious rather than gone, and a rest brings them round — only ten
     * below is past what sleep can do. Read the other way, a party carrying
     * somebody at nothing left are told they are fully rested and left to
     * carry them.
     */
    @Test
    fun `a champion at nothing left is mended by sleeping`() {
        val down = world(champion(100, HitPoints(0, 82)))

        assertTrue(down.anybodyStillHurt, "nobody is hurt with one of them at nothing")

        val woken = assertIs<Rest.Slept>(resting().rest(down)).world.champions.first()
        assertEquals(82, woken.hitPoints.current, "sleep left them where they lay")
    }

    /** Past raising is past a bed, though: that wants a cleric. */
    @Test
    fun `a champion past raising is not mended by sleeping`() {
        val gone = world(champion(100, HitPoints(Champion.BEYOND_RAISING, 82)))

        assertTrue(!gone.anybodyStillHurt, "a rest counted somebody past raising as mendable")

        val woken = assertIs<Rest.Slept>(resting().rest(gone)).world.champions.first()
        assertEquals(Champion.BEYOND_RAISING, woken.hitPoints.current)
    }

    @Test
    fun `eating fills a stomach and no further`() {
        val fed = world(champion(80, HitPoints(20, 20))).championFed(PartySlot(0), by = 50)
        assertEquals(100, fed.champions.first().food.value, "ate past full")
    }

    // --- the hours, as the box counts them ------------------------------------

    /**
     * A rest is watched rather than added up at once, so it goes a stretch at a
     * time — and a rest cut short after one stretch is worth exactly that much
     * mending and that much hunger.
     */
    @Test
    fun `a stretch of sleep mends a stretch's worth and eats one meal`() {
        // a wide gap to mend, so the stretch is not cut short by full health
        val begun = world(champion(100, HitPoints(10, 100)))

        val after = begun.sleptAnHour(HOURS_A_MENDED_POINT)

        assertEquals(42, after.champions.first().hitPoints.current)
        assertEquals(98, after.champions.first().food.value)
    }

    /** However much a stretch mends, it never mends past full. */
    @Test
    fun `mending stops at full health`() {
        val after = world(champion(100, HitPoints(19, 20))).sleptAnHour(HOURS_A_MENDED_POINT)

        assertEquals(20, after.champions.first().hitPoints.current, "mended past full")
    }

    /**
     * The three things a rest can end as, which are what the party are told.
     * Hurt with an empty stomach is not the same as rested, and saying so is
     * the difference between a party who know to go and eat and one who think
     * they are well.
     */
    @Test
    fun `hurt with nothing to eat is not the same as rested`() {
        val rested = world(champion(100, HitPoints(20, 20)))
        assertTrue(!rested.anybodyStillHurt, "nobody is hurt")

        val starving = world(champion(0, HitPoints(5, 20)))
        assertTrue(starving.anybodyStillHurt, "they are hurt")
        assertTrue(!starving.anybodyCanStillMend, "they cannot mend on an empty stomach")

        val fed = world(champion(100, HitPoints(5, 20)))
        assertTrue(fed.anybodyStillHurt && fed.anybodyCanStillMend, "they can mend")
    }

    /** A rest that mends nothing because there is no food leaves them hurt. */
    @Test
    fun `a starving party wake no better than they lay down`() {
        val hungry = world(champion(0, HitPoints(5, 20)))

        val slept = assertIs<Rest.Slept>(resting().rest(hungry)).world

        assertEquals(5, slept.champions.first().hitPoints.current, "mended on no food")
        assertTrue(slept.anybodyStillHurt && !slept.anybodyCanStillMend)
    }

    // --- starving through a rest ----------------------------------------------

    /**
     * Sleeping on an empty stomach costs rather than mends: a day of it is a
     * hit point gone. It is the one thing a rest takes away, which is why it is
     * asked for before it is done.
     */
    @Test
    fun `a day asleep on an empty stomach costs a hit point`() {
        val hungry = world(champion(0, HitPoints(10, 20)))

        val aDayOn = hungry.starvedADay()

        assertEquals(9, aDayOn.champions.first().hitPoints.current)
    }

    /** A full stomach sleeps through the same day untouched. */
    @Test
    fun `a fed champion does not starve`() {
        val fed = world(champion(100, HitPoints(10, 20)))

        assertEquals(10, fed.starvedADay().champions.first().hitPoints.current)
    }

    /**
     * Starving cannot carry a champion past raising: at ten below they are as
     * far gone as hunger takes them, and a party left sleeping does not grind
     * them any further.
     */
    @Test
    fun `starving stops at the point past raising`() {
        val gone = world(champion(0, HitPoints(Champion.BEYOND_RAISING, 20)))

        assertEquals(
            Champion.BEYOND_RAISING,
            gone.starvedADay().champions.first().hitPoints.current,
        )
    }

    // --- growing hungry as they go ------------------------------------------

    /**
     * Walking empties a stomach, and it costs nothing on its own — an empty
     * one is only paid for by sleeping on it.
     */
    @Test
    fun `covering ground makes the party hungrier`() {
        val fed = world(champion(100, HitPoints(20, 20)))

        val later = fed.hungrier()

        assertEquals(99, later.champions.first().food.value)
        assertEquals(20, later.champions.first().hitPoints.current, "growing hungry hurt them")
    }

    /** An empty stomach cannot be emptied further. */
    @Test
    fun `hunger stops at nothing left`() {
        val empty = world(champion(0, HitPoints(20, 20)))

        assertEquals(0, empty.hungrier().champions.first().food.value)
    }

    /**
     * The question is put when a party *become* starving, not for as long as
     * they are: asking on every stretch would put it up faster than anybody
     * could answer it. So the moment worth catching is the last mouthful going
     * in, which happens once for each champion.
     */
    @Test
    fun `running out of food is a moment and not a state`() {
        val fed = world(champion(2, HitPoints(5, 400)))
        val empty = fed.sleptAnHour(HOURS_A_MENDED_POINT)

        assertTrue(fed.somebodyRanOutOfFood(empty), "their last mouthful went unnoticed")
        assertTrue(empty.anybodyStarving, "they are starving now")

        // and the stretch after that is not another moment, only more of it
        assertTrue(
            !empty.somebodyRanOutOfFood(empty.sleptAnHour(HOURS_A_MENDED_POINT)),
            "it counted an empty stomach as emptying again",
        )
    }

    @Test
    fun `an empty stomach is what counts as starving`() {
        assertTrue(world(champion(0, HitPoints(20, 20))).anybodyStarving, "empty is starving")
        assertTrue(!world(champion(5, HitPoints(20, 20))).anybodyStarving, "five is not")
    }

    @Test
    fun `the party stop sleeping once nobody is mending`() {
        val resting = resting()

        assertTrue(resting.anybodyStillMending(world(champion(100, HitPoints(10, 20)))))
        assertTrue(!resting.anybodyStillMending(world(champion(100, HitPoints(20, 20)))), "still hurt?")
        assertTrue(!resting.anybodyStillMending(world(champion(0, HitPoints(10, 20)))), "slept on no food")
    }

    /**
     * The floor does not hold still while they sleep. Beginning a rest again
     * each hour is what lets a monster that wanders up wake them, and it is the
     * same check that refused the rest in the first place.
     */
    @Test
    fun `a monster that comes near mid-rest wakes them`() {
        val settled = world(champion(100, HitPoints(5, 20)), monstersOn = listOf(Location(13, 9)))
            .let { it.copy(monsters = it.monsters.map { m -> m.copy(provoked = true) }) }

        // the first hour is undisturbed at that distance
        assertIs<Rest.Slept>(resting(theyWalk = false).begin(settled))

        // but with it walking, asking again is what catches it arriving
        assertIs<Rest.SomethingIsNear>(resting(theyWalk = true).begin(settled, walking()))
    }
}
