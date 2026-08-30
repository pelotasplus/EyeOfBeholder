package pl.pelotasplus.eyeofbeholder.data

import kotlinx.coroutines.runBlocking
import org.junit.experimental.categories.Category
import pl.pelotasplus.eyeofbeholder.NeedsGameData
import pl.pelotasplus.eyeofbeholder.data.model.Direction
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
 * The square a party arrive on when a script walks them to another floor, and
 * what it is owed.
 *
 * Walking downstairs is two scripts, not one: the stairs say where the party
 * are going, and the square they land on says what happens when they get
 * there. The second only runs if arriving counts as stepping onto a square —
 * and the seventh floor's welcome is a script on the square the stairs come
 * up on, with nothing else in the game to set it off.
 */
@Category(NeedsGameData::class)
class ArrivingOnAFloorTest {

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

    private val fifth by lazy { load("LEVEL5.INF") }
    private val seventh by lazy { load("LEVEL7.INF") }

    private fun Inf.steppingOn(
        at: Location,
        facing: Direction,
        level: Int,
        state: GameState = GameState(party = PartyState(at, facing)),
        stage: RecordingStage = RecordingStage(),
    ) = runBlocking {
        LevelScriptRunner(script = script, level = level, subLevel = 0).onEvent(
            triggers = triggers,
            event = ScriptEvent.PARTY_ENTERED,
            state = state,
            stage = stage,
            at = at,
        ) to stage
    }

    /** The stairs down, which end the script by naming where the party go. */
    @Test
    fun `the stairs on the fifth floor send the party to the seventh`() {
        val (run, _) = fifth.steppingOn(THE_STAIRS, Direction.EAST, level = 5)

        assertEquals(7, run.changeLevel?.level)
        assertEquals(ARRIVING_AT, run.changeLevel?.location)
    }

    /**
     * And the square they land on has a scene of its own, which is the whole
     * point of this: the stairs know nothing about it.
     */
    @Test
    fun `the square they land on has something to say`() {
        val (_, stage) = seventh.steppingOn(ARRIVING_AT, Direction.EAST, level = 7)

        assertTrue(stage.beats.isNotEmpty(), "arriving should have shown something")
        assertTrue(
            stage.beats.any { it is RecordingStage.Beat.Said },
            "and said something: ${stage.beats}",
        )
        assertTrue(stage.played.isNotEmpty(), "and made a noise")
    }

    /** It is a once-only scene, and says so by the bit it sets. */
    @Test
    fun `and says it once`() {
        val first = seventh.steppingOn(ARRIVING_AT, Direction.EAST, level = 7)
        val (_, again) = seventh.steppingOn(
            ARRIVING_AT,
            Direction.EAST,
            level = 7,
            state = first.first.state,
        )

        assertEquals(emptyList(), again.beats)
    }

    private companion object {
        val THE_STAIRS = Location(14, 6)
        val ARRIVING_AT = Location(15, 6)
    }
}
