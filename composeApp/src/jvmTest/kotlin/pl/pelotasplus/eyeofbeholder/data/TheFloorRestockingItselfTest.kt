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
import pl.pelotasplus.eyeofbeholder.data.model.TriggerFlags
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
 * A floor putting its monsters back, which it does by counting the party's
 * steps rather than by any clock.
 *
 * Every floor carries a number of steps, and every so many of them the square
 * at 0x0 is told so. Nobody can stand on that square, click it, or throw
 * anything at it: it is the floor talking to itself, and what it nearly always
 * says is to make more monsters.
 *
 * The thirteenth floor is the one that shows why it matters. It has no clock
 * of its own at all, so the step count is the only thing that ever reaches its
 * 0x0 script — and that script is the whole of where its medusae and its
 * spectral servants come from. Without it the floor is emptied for good the
 * first time it is cleared, which is not the game: the walkthrough's advice to
 * hang about killing medusae for experience depends on this and nothing else.
 */
@Category(NeedsGameData::class)
class TheFloorRestockingItselfTest {

    private val resources = ResourceRepositoryImpl()

    private fun load(name: String): Inf = runBlocking {
        InfRepositoryImpl(
            resourceRepository = resources,
            mazRepository = MazRepositoryImpl(resources),
            vmpRepository = VmpRepositoryImpl(resources),
            vcnRepository = VcnRepositoryImpl(resources),
            palRepository = PalRepositoryImpl(resources),
            cpsRepository = CpsRepositoryImpl(resources),
            decRepository = DecRepositoryImpl(resources),
        ).loadInf(name).getOrThrow()
    }

    // --- what the floors say about themselves ----------------------------------

    /** Each floor names its own count, and they are the files' own numbers. */
    @Test
    fun `a floor says how many steps it wants`() {
        assertEquals(100, load("LEVEL12.INF").subLevels[0].stepsUntilScriptCall)
        assertEquals(22, load("LEVEL4.INF").subLevels[0].stepsUntilScriptCall)
    }

    /**
     * And the thirteenth has no clock, so nothing but the step count can ever
     * set its 0x0 script off. This is the fact the whole feature rests on.
     */
    @Test
    fun `the thirteenth floor has no other way to reach its own script`() {
        val here = load("LEVEL13.INF").subLevels[0]

        assertTrue(here.stepsUntilScriptCall > 0, "it counts no steps")
        assertEquals(emptyList(), here.scriptTimers, "it has a clock after all")
    }

    // --- and that every square answers it --------------------------------------

    /**
     * The event reaches a square whatever that square says it answers: the top
     * bits of the accepted set are forced on. So the floor's own square needs
     * no mark of its own, and has none — its flags are zero.
     */
    @Test
    fun `every square answers it, however it is marked`() {
        assertTrue(TriggerFlags(0).reactsTo(ScriptEvent.ENOUGH_STEPS_WALKED))

        val floorsOwn = load("LEVEL13.INF").triggers.single { it.location == THE_FLOOR_ITSELF }
        assertEquals(0, floorsOwn.flags.raw, "the floor's own square is marked after all")
        assertTrue(floorsOwn.flags.reactsTo(ScriptEvent.ENOUGH_STEPS_WALKED))
    }

    // --- and what running it comes to ------------------------------------------

    /**
     * The thirteenth's own script, run once: the floor fills with medusae and
     * spectral servants, and stops filling at thirty because thirty is all a
     * floor holds.
     */
    @Test
    fun `running it peoples the thirteenth floor`() = runBlocking {
        val level = load("LEVEL13.INF")

        val after = LevelScriptRunner(level.script, level = LEVEL).onEvent(
            triggers = level.triggers,
            event = ScriptEvent.ENOUGH_STEPS_WALKED,
            state = emptyFloor(),
            at = THE_FLOOR_ITSELF,
        ).state

        assertTrue(after.monsters.isNotEmpty(), "the floor stayed empty")
        assertTrue(
            after.monsters.size <= MONSTERS_A_FLOOR_HOLDS,
            "it put ${after.monsters.size} out, and a floor holds $MONSTERS_A_FLOOR_HOLDS",
        )
    }

    /**
     * Medusae on a floor as it starts, and the spectral servants only once the
     * floor has marked itself.
     *
     * The two kinds are not put out together: the servants sit behind the
     * floor's own flag 18, so a party who have not done whatever sets it walk
     * a floor of medusae and nothing else. It is worth pinning because a
     * servant that never appears looks exactly like a servant that is drawn
     * wrong.
     */
    @Test
    fun `the medusae come first, and the servants only once the floor says so`() = runBlocking {
        val level = load("LEVEL13.INF")
        val runner = LevelScriptRunner(level.script, level = LEVEL)

        val before = runner.onEvent(
            triggers = level.triggers,
            event = ScriptEvent.ENOUGH_STEPS_WALKED,
            state = emptyFloor(),
            at = THE_FLOOR_ITSELF,
        ).state

        assertEquals(
            setOf(A_MEDUSA),
            before.monsters.map { it.type.value }.toSet(),
            "something other than medusae came out of an unmarked floor",
        )

        val after = runner.onEvent(
            triggers = level.triggers,
            event = ScriptEvent.ENOUGH_STEPS_WALKED,
            state = emptyFloor().levelFlagSet(LEVEL, WHAT_LETS_THE_SERVANTS_OUT),
            at = THE_FLOOR_ITSELF,
        ).state

        assertTrue(
            after.monsters.any { it.type.value == A_SERVANT },
            "the floor was marked and the servants still never came",
        )
    }

    /**
     * Run again with the floor already full, it adds nothing rather than
     * failing or growing past the limit. A party who pace a corridor do not
     * end up with a floor of two hundred medusae.
     */
    @Test
    fun `running it on a full floor does not grow it`() = runBlocking {
        val level = load("LEVEL13.INF")
        val runner = LevelScriptRunner(level.script, level = LEVEL)

        val once = runner.onEvent(
            triggers = level.triggers,
            event = ScriptEvent.ENOUGH_STEPS_WALKED,
            state = emptyFloor(),
            at = THE_FLOOR_ITSELF,
        ).state

        val twice = runner.onEvent(
            triggers = level.triggers,
            event = ScriptEvent.ENOUGH_STEPS_WALKED,
            state = once,
            at = THE_FLOOR_ITSELF,
        ).state

        assertTrue(
            twice.monsters.size <= MONSTERS_A_FLOOR_HOLDS,
            "a second run put the floor over its limit at ${twice.monsters.size}",
        )
    }

    private fun emptyFloor() = GameState(party = PartyState(STANDING, Direction.NORTH))

    private companion object {
        const val LEVEL = 13

        /** The corner a floor talks to itself through. */
        val THE_FLOOR_ITSELF = Location(0, 0)

        /** Well away from anything the script puts out. */
        val STANDING = Location(27, 15)

        /** Thirty, and the reason a floor cannot be paced into a swarm. */
        const val MONSTERS_A_FLOOR_HOLDS = 30

        /** The thirteenth's two species, in the order its own tables name them. */
        const val A_MEDUSA = 0
        const val A_SERVANT = 1

        /** Which of the floor's own marks the servants wait behind. */
        val WHAT_LETS_THE_SERVANTS_OUT = FlagBit(18)
    }
}
