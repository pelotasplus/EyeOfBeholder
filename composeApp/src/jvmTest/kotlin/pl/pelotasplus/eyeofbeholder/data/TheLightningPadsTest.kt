package pl.pelotasplus.eyeofbeholder.data

import kotlinx.coroutines.runBlocking
import org.junit.experimental.categories.Category
import pl.pelotasplus.eyeofbeholder.NeedsGameData
import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.FlagBit
import pl.pelotasplus.eyeofbeholder.data.model.GameState
import pl.pelotasplus.eyeofbeholder.data.model.Inf
import pl.pelotasplus.eyeofbeholder.data.model.LevelScriptRunner
import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.PartyState
import pl.pelotasplus.eyeofbeholder.data.model.ScriptEvent
import pl.pelotasplus.eyeofbeholder.data.model.ScriptTimer
import pl.pelotasplus.eyeofbeholder.data.model.Ticks
import pl.pelotasplus.eyeofbeholder.data.model.WallSide
import pl.pelotasplus.eyeofbeholder.data.model.script.ClearFlag
import pl.pelotasplus.eyeofbeholder.data.model.script.SetFlag
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
 * The array of lightning pads on the seventh floor, which is the one thing in
 * the game that happens with nobody near it.
 *
 * A floor keeps a clock of its own: a square, and how long between wakings.
 * The square it names is the array's, and each waking rearranges which pads
 * are lit and looks to see whether the party are standing on one.
 *
 * The square is named as one number across the whole maze rather than as a
 * pair, which is the only thing about the clock that is not obvious — and
 * getting it wrong points the clock at a square that does nothing, so the
 * floor simply stands still.
 */
@Category(NeedsGameData::class)
class TheLightningPadsTest {

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
        ).loadInf("LEVEL7.INF").getOrThrow()
    }

    private val clocks get() = level.subLevels[0].scriptTimers

    private fun world(standingOn: Location = OUT_OF_THE_WAY) = GameState(
        party = PartyState(standingOn, Direction.EAST),
    ).arrivingAt(level = LEVEL, places = emptyList(), maz = level.subLevels[0].maz)

    private fun GameState.clockComesRound(at: Location = THE_ARRAY) = runBlocking {
        LevelScriptRunner(script = level.script, level = LEVEL, subLevel = 0).onEvent(
            triggers = level.triggers,
            event = ScriptEvent.THE_CLOCK_CAME_ROUND,
            state = this@clockComesRound,
            at = at,
        ).state
    }

    private fun GameState.lit(square: Location) =
        wallByte(LEVEL, square, WallSide.NORTH).value == LIT

    /** Which pads are lit, as one line, so a rearrangement is one comparison. */
    private fun GameState.theArray() = PADS.map { lit(it) }

    // --- the clock itself ----------------------------------------------------

    @Test
    fun `the floor keeps one clock and it watches the array`() {
        assertEquals(1, clocks.size)
        assertEquals(THE_ARRAY, clocks.single().watches)
        assertEquals(Ticks(18), clocks.single().ticks)
    }

    /**
     * The square is packed as one number across the maze. Reading it as a
     * pair, or forgetting the width, points the clock somewhere harmless and
     * the array never moves.
     */
    @Test
    fun `a square number is unpacked across the width of the maze`() {
        assertEquals(Location(17, 7), ScriptTimer.of(block = 241, ticks = 1).watches)
        assertEquals(Location(0, 0), ScriptTimer.of(block = 0, ticks = 1).watches)
        assertEquals(Location(31, 31), ScriptTimer.of(block = 1023, ticks = 1).watches)
    }

    /** Every square answers the clock, whatever else its trigger is marked for. */
    @Test
    fun `the square the clock watches is marked only for the party arriving`() {
        val watched = level.triggers.single { it.location == THE_ARRAY }

        assertTrue(watched.flags.reactsTo(ScriptEvent.PARTY_ENTERED))
        assertTrue(
            watched.flags.reactsTo(ScriptEvent.THE_CLOCK_CAME_ROUND),
            "and answers the clock anyway, which is what lets a level point one anywhere",
        )
    }

    // --- what a waking does --------------------------------------------------

    /**
     * The array runs from the moment the floor is loaded — the bit is a brake
     * rather than a switch, and it starts off.
     */
    @Test
    fun `a waking rearranges the pads`() {
        val running = world()

        assertTrue(
            running.theArray() != running.clockComesRound().theArray(),
            "the clock should have moved the pads",
        )
    }

    /** And it goes on moving them: several arrangements, not one change. */
    @Test
    fun `it keeps rearranging them`() {
        var world = world()
        val seen = mutableSetOf(world.theArray())

        repeat(4) {
            world = world.clockComesRound()
            seen += world.theArray()
        }

        assertTrue(seen.size >= 3, "expected several arrangements, saw ${seen.size}")
    }

    /**
     * The doorways round the array put the brake on and take it off again as
     * the party pass, and while it is on a waking does nothing at all.
     */
    @Test
    fun `the brake stops it`() {
        val braked = world().copy(
            flags = world().flags.setting(LEVEL, FlagBit(THE_BRAKE)),
        )

        assertEquals(braked.theArray(), braked.clockComesRound().theArray())
    }

    /**
     * Which is most of the time: the party spend far longer off the array than
     * on it, and the clock goes on asking either way. A waking that changes
     * nothing must cost nothing — it happens for as long as the party stay on
     * the floor.
     */
    @Test
    fun `a braked waking leaves the world exactly as it was`() {
        val braked = world().copy(
            flags = world().flags.setting(LEVEL, FlagBit(THE_BRAKE)),
        )

        assertEquals(braked, braked.clockComesRound())
    }

    /** The squares that work it, so a renamed brake cannot go unnoticed. */
    @Test
    fun `the doorways round the array are what work the brake`() {
        val works = { bit: Int, set: Boolean ->
            level.script.filter {
                if (set) (it.token as? SetFlag.LevelFlag)?.bit?.index == bit
                else (it.token as? ClearFlag.LevelFlag)?.flag == bit
            }.map { touched -> squareWhoseScriptCovers(touched.offset.value) }.toSet()
        }

        assertEquals(
            setOf(Location(27, 5), Location(26, 9), Location(15, 13), Location(14, 12), THE_DOOR),
            works(THE_BRAKE, true),
        )
        assertEquals(
            setOf(Location(27, 7), Location(26, 8), Location(15, 12), THE_DOOR),
            works(THE_BRAKE, false),
        )
    }

    /** The square whose script an offset falls inside. */
    private fun squareWhoseScriptCovers(offset: Int) = level.triggers
        .filter { it.script.offset.value <= offset }
        .maxBy { it.script.offset.value }
        .location

    /** Standing off the array, a waking costs nothing. */
    @Test
    fun `somebody out of the way is not struck`() {
        val struck = world(OUT_OF_THE_WAY).clockComesRound()

        assertEquals(emptyMap(), struck.showingDamage.associate { it.whose to it.amount })
    }

    private companion object {
        const val LEVEL = 7

        /** The wall byte a pad wears while it is lit. */
        const val LIT = 60

        /**
         * The bit that holds the array still. It is a brake and not a switch:
         * clear, which is how a floor starts, the array runs.
         */
        const val THE_BRAKE = 17

        /** The square the floor's clock wakes. */
        val THE_ARRAY = Location(17, 7)

        /** Somewhere on the floor the array cannot reach. */
        val OUT_OF_THE_WAY = Location(1, 1)

        /** The one doorway that both puts the brake on and takes it off. */
        val THE_DOOR = Location(27, 9)

        /** The pads the routine rearranges, in the order it names them. */
        val PADS = listOf(
            Location(17, 7), Location(18, 7), Location(19, 7), Location(19, 8),
            Location(19, 9), Location(20, 9), Location(21, 9), Location(21, 8),
            Location(22, 8), Location(19, 6),
        )
    }
}
