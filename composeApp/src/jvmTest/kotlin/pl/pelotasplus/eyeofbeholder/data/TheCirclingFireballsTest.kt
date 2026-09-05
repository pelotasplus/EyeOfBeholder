package pl.pelotasplus.eyeofbeholder.data

import kotlinx.coroutines.runBlocking
import org.junit.experimental.categories.Category
import pl.pelotasplus.eyeofbeholder.NeedsGameData
import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.Flight
import pl.pelotasplus.eyeofbeholder.data.model.GameState
import pl.pelotasplus.eyeofbeholder.data.model.Inf
import pl.pelotasplus.eyeofbeholder.data.model.LevelScriptRunner
import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.PartyState
import pl.pelotasplus.eyeofbeholder.data.model.ScriptEvent
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
import kotlin.test.assertTrue

/**
 * The pair of fireballs that go round and round the plate on the twelfth
 * floor, which the party have to time their way past.
 *
 * The room does it with two pieces and no special case. Stepping onto 5x21
 * looses two fireballs into the ring around 9x23, one from the north-east
 * corner going south and one from the south going west. Then each of the four
 * corners of the ring carries a trigger that reacts to something flying in and
 * gives it a quarter turn — so what was loosed down one wall comes back along
 * the next, for ever.
 *
 * Nothing here is about fireballs in particular. It is a square saying "turn
 * whatever is over me", and it would send a thrown rock round just the same.
 */
@Category(NeedsGameData::class)
class TheCirclingFireballsTest {

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
        ).loadInf("LEVEL12.INF").getOrThrow()
    }

    /** Where the party have to tread to set it going. */
    private val setsItGoing = Location(5, 21)

    /** The plate they are trying to reach, and the eight squares round it. */
    private val thePlate = Location(9, 23)

    private val theRing = setOf(
        Location(8, 22), Location(9, 22), Location(10, 22),
        Location(8, 23), Location(10, 23),
        Location(8, 24), Location(9, 24), Location(10, 24),
    )

    @Test
    fun `stepping on the far square looses two of them`() {
        val world = walkedOn(setsItGoing)

        assertEquals(2, world.inFlight.size, "the room loosed nothing")
    }

    /**
     * They stay up. A thing loosed down a corridor gives out or hits a wall;
     * these are turned back into the ring at every corner, so they are still
     * going long after anything else would have stopped.
     */
    @Test
    fun `they are still going a long time later`() {
        val after = flownFor(TWO_LAPS).world

        assertEquals(2, after.inFlight.size, "they went out or flew off")
    }

    /**
     * And they stay in the ring: never over the plate itself, which is what
     * makes the puzzle a matter of timing rather than of luck, and never out
     * into the room.
     */
    @Test
    fun `they keep to the ring, and never cross the plate`() {
        val visited = flownFor(TWO_LAPS).visited

        assertEquals(theRing, visited, "they wandered off the ring")
        assertTrue(thePlate !in visited, "one of them crossed the plate")
    }

    /**
     * Round rather than back and forth: over two laps every square of the ring
     * takes very nearly the same share, which a pair bouncing along one wall
     * would not manage. Not exactly the same, because the clock is stopped
     * part-way round rather than at a corner.
     */
    @Test
    fun `each square of the ring takes its turn`() {
        val counted = flownFor(TWO_LAPS).crossings

        assertEquals(theRing, counted.keys, "some of the ring was never crossed")
        assertTrue(
            counted.values.max() - counted.values.min() <= 1,
            "the ring is not even: $counted",
        )
    }

    /**
     * And they survive being saved and loaded.
     *
     * What set them going is a square across the room, and treading on it is
     * the only thing that ever does. So a save that forgets what was in the
     * air does not merely lose a picture — it disarms the room, and leaves the
     * party walking onto the plate through a trap that has stopped.
     */
    @Test
    fun `a save keeps them going`() {
        val going = flownFor(HALF_A_LAP).world
        assertEquals(2, going.inFlight.size, "nothing was in the air to save")

        val loaded = GameState.restoredFrom(going.saved(), on = LEVEL)

        assertEquals(going.inFlight, loaded.inFlight, "the room came back quiet")
    }

    // --- the fixture ---------------------------------------------------------

    private class Flown(
        val world: GameState,
        val visited: Set<Location>,
        val crossings: Map<Location, Int>,
    )

    private val runner get() = LevelScriptRunner(level.script, level = LEVEL)

    /** The world just after the party tread on [where]. */
    private fun walkedOn(where: Location): GameState = runBlocking {
        val world = GameState(party = PartyState(where, Direction.SOUTH))
            .arrivingAt(level = LEVEL, places = emptyList(), maz = level.subLevels[0].maz)

        runner.onEvent(
            triggers = level.triggers,
            event = ScriptEvent.PARTY_ENTERED,
            state = world,
            at = where,
        ).state
    }

    /**
     * [ticks] turns of the clock after it is set going, with every square
     * something flies onto told so — which is what turns them.
     */
    private fun flownFor(ticks: Int): Flown = runBlocking {
        var world = walkedOn(setsItGoing)
        val flying = Flight(sublevel = level.subLevels[0], level = LEVEL)
        val runner = runner
        val crossings = mutableMapOf<Location, Int>()

        repeat(ticks) {
            val moved = flying.onward(world)
            world = moved.world

            moved.flewOnto.forEach { onto ->
                crossings[onto] = (crossings[onto] ?: 0) + 1

                world = runner.onEvent(
                    triggers = level.triggers,
                    event = ScriptEvent.SOMETHING_FLEW_IN,
                    state = world,
                    at = onto,
                ).state
            }
        }

        Flown(world, crossings.keys.toSet(), crossings)
    }

    private companion object {
        const val LEVEL = 12

        /**
         * Long enough for each of them to go round twice. One lap is eighteen
         * turns of the clock, and they are half a lap apart.
         */
        const val TWO_LAPS = 36

        /** Far enough in that both are somewhere awkward to be caught. */
        const val HALF_A_LAP = 9
    }
}
