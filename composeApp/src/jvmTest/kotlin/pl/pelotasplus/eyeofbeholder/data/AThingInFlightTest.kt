package pl.pelotasplus.eyeofbeholder.data

import kotlinx.coroutines.runBlocking
import pl.pelotasplus.eyeofbeholder.data.model.Champion
import pl.pelotasplus.eyeofbeholder.data.model.ChampionFlags
import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.Flight
import pl.pelotasplus.eyeofbeholder.data.model.GameState
import pl.pelotasplus.eyeofbeholder.data.model.HitPoints
import pl.pelotasplus.eyeofbeholder.data.model.Inf
import pl.pelotasplus.eyeofbeholder.data.model.LevelScriptRunner
import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.PartyState
import pl.pelotasplus.eyeofbeholder.data.model.Projectile
import pl.pelotasplus.eyeofbeholder.data.model.ScriptEvent
import pl.pelotasplus.eyeofbeholder.data.model.SquarePlace
import pl.pelotasplus.eyeofbeholder.data.repository.CpsRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.DecRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.InfRepositoryImpl
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
 * The fireball trap on level 2, which is the whole mechanism end to end.
 *
 * Pulling the lever at 2x11 looses something from 3x8 heading south. It
 * crosses 3x9, 3x10 and 3x11, and crossing 3x11 is what puts the Skull Key
 * into the niche at 2x10 — the trap does its work further down the corridor
 * than it stands, and the thing in the air is what carries it there.
 */
class AThingInFlightTest {

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
        ).loadInf("LEVEL2.INF").getOrThrow()
    }

    private val items = runBlocking { ItemsRepositoryImpl(resources).loadItems().getOrThrow().items }

    private val lever = Location(2, 11)

    private fun standingAt(where: Location) = GameState(
        party = PartyState(where, Direction.WEST),
        items = items,
    )

    /** Runs the level's script for [event] at [at], as the game would. */
    private fun fire(world: GameState, at: Location, event: ScriptEvent): GameState = runBlocking {
        LevelScriptRunner(level.script, level = 2).onEvent(
            triggers = level.triggers,
            event = event,
            state = world,
            stage = RecordingStage(),
            at = at,
        ).state
    }

    /** One square of flight for everything in the air. */
    private fun onward(world: GameState): Flight.Moved =
        Flight(sublevel = level.subLevels[0], level = 2).onward(world)

    @Test
    fun `pulling the lever puts something in the air`() {
        val after = fire(standingAt(Location(3, 11)), lever, ScriptEvent.WALL_CLICKED)

        val loosed = after.inFlight.singleOrNull()
        assertNotNull(loosed, "the lever loosed nothing")
        assertEquals(Location(3, 8), loosed.at, "it did not start where the trap is")
        assertEquals(Direction.SOUTH, loosed.going, "it is not going down the corridor")
    }

    /**
     * The corridor runs south from the trap, so the thing crosses one square
     * per turn of the clock and 3x11 is the third of them.
     */
    @Test
    fun `it crosses the corridor a square at a time`() {
        var world = fire(standingAt(Location(4, 11)), lever, ScriptEvent.WALL_CLICKED)

        val crossed = buildList {
            repeat(4) {
                val moved = onward(world)
                world = moved.world
                addAll(moved.flewOnto)
            }
        }

        assertEquals(
            listOf(Location(3, 9), Location(3, 10), Location(3, 11), Location(3, 12)),
            crossed,
            "it did not travel down the corridor square by square",
        )
    }

    /**
     * The point of the whole thing: crossing 3x11 runs that square's script,
     * and that script is what stocks the niche.
     */
    @Test
    fun `crossing 3x11 puts the skull key in the niche`() {
        var world = fire(standingAt(Location(4, 11)), lever, ScriptEvent.WALL_CLICKED)

        repeat(4) {
            val moved = onward(world)
            world = moved.world
            moved.flewOnto.forEach { where ->
                world = fire(world, where, ScriptEvent.SOMETHING_FLEW_IN)
            }
        }

        val inTheNiche = world.items.filter {
            it.level == 2 &&
                it.location == Location(2, 10) &&
                it.place == SquarePlace.IN_A_NICHE
        }

        assertTrue(
            inTheNiche.any { it.type.value == SKULL_KEY_KIND && it.value == SKULL_KEY_WORTH },
            "nothing was put in the niche; found $inTheNiche",
        )
    }

    /**
     * A party standing in the corridor take it instead. Nobody aims a trap, so
     * there is no roll to be missed by — what it reaches, it hits.
     */
    @Test
    fun `it hurts whoever is standing in the way`() {
        val standing = standingAt(Location(3, 10)).copy(
            champions = List(6) { slot ->
                if (slot == 0) {
                    Champion.NOBODY.copy(
                        name = "One",
                        flags = ChampionFlags(IN_THE_PARTY),
                        hitPoints = HitPoints(HEARTY, HEARTY),
                    )
                } else {
                    Champion.NOBODY
                }
            },
        )

        var world = fire(standing, lever, ScriptEvent.WALL_CLICKED)

        val hurt = buildList {
            repeat(3) {
                val moved = onward(world)
                world = moved.world
                addAll(moved.hurt)
            }
        }

        assertTrue(hurt.isNotEmpty(), "it went straight through the party")
        assertTrue(
            world.champions[0].hitPoints.current < HEARTY,
            "nobody was any the worse for standing in front of it",
        )
    }

    /**
     * A burst takes the whole square. Every champion standing there is rolled
     * for separately — there is nothing to get behind on your own square, and
     * the fireball on the first complaint only ever singed one of them.
     */
    @Test
    fun `a burst takes the whole party, not one of them`() {
        val party = standingAt(Location(3, 10)).copy(champions = aFullParty())

        var world = fire(party, lever, ScriptEvent.WALL_CLICKED)

        val hurt = buildList {
            repeat(3) {
                val moved = onward(world)
                world = moved.world
                addAll(moved.hurt)
            }
        }

        val struck = hurt.filterIsInstance<Flight.Hurt.AChampion>().map { it.slot }.toSet()

        assertEquals(6, struck.size, "a fireball found only some of the party")
        assertTrue(
            world.champions.all { it.hitPoints.current < HEARTY },
            "somebody standing in a fireball came out of it untouched",
        )
    }

    /**
     * And it is a die of six for each level of whatever threw it, which for a
     * trap on this floor is five of them. One die would be the whole party
     * shrugging a fireball off.
     */
    @Test
    fun `a trap's burst rolls a die of six per level of it`() {
        val party = standingAt(Location(3, 10)).copy(champions = aFullParty())

        var world = fire(party, lever, ScriptEvent.WALL_CLICKED)
        repeat(3) { world = onward(world).world }

        val taken = HEARTY - world.champions[0].hitPoints.current

        assertTrue(taken in TRAP_LEVELS..(TRAP_LEVELS * 6), "a fireball took $taken, which is not 5d6")
    }

    /** And it stops when it has hit something rather than flying on. */
    @Test
    fun `it stops once it has hit`() {
        var world = fire(standingAt(Location(3, 10)).copy(
            champions = List(6) { slot ->
                if (slot == 0) {
                    Champion.NOBODY.copy(
                        name = "One",
                        flags = ChampionFlags(IN_THE_PARTY),
                        hitPoints = HitPoints(HEARTY, HEARTY),
                    )
                } else {
                    Champion.NOBODY
                }
            },
        ), lever, ScriptEvent.WALL_CLICKED)

        repeat(3) { world = onward(world).world }

        assertEquals(emptyList<Projectile>(), world.inFlight, "it carried on after hitting")
    }

    /** All six of them on their feet and hale, so damage is visible on each. */
    private fun aFullParty() = List(6) {
        Champion.NOBODY.copy(
            name = "One",
            flags = ChampionFlags(IN_THE_PARTY),
            hitPoints = HitPoints(HEARTY, HEARTY),
        )
    }

    private companion object {
        const val SKULL_KEY_KIND = 38
        const val SKULL_KEY_WORTH = 5

        const val IN_THE_PARTY = 0x01

        /** Enough hit points that the trap cannot finish anybody off. */
        const val HEARTY = 200

        /** What the game counts a trap as being worth, above the seventh floor. */
        const val TRAP_LEVELS = 5
    }
}
