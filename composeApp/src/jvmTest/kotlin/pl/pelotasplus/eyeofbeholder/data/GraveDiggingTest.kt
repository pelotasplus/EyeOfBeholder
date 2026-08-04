package pl.pelotasplus.eyeofbeholder.data

import kotlinx.coroutines.runBlocking
import pl.pelotasplus.eyeofbeholder.data.model.DialogAnswer
import pl.pelotasplus.eyeofbeholder.data.model.CharacterClass
import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.FloorReach
import pl.pelotasplus.eyeofbeholder.data.model.GameState
import pl.pelotasplus.eyeofbeholder.data.model.LevelScriptRunner
import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.PartyState
import pl.pelotasplus.eyeofbeholder.data.model.SquarePlace
import pl.pelotasplus.eyeofbeholder.data.model.ScriptEvent
import pl.pelotasplus.eyeofbeholder.data.model.ScriptQuestion
import pl.pelotasplus.eyeofbeholder.data.model.ScriptStage
import pl.pelotasplus.eyeofbeholder.data.repository.CpsRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.DecRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.InfRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.MazRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.OriginalSaveRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.PalRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.ResourceRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.VcnRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.VmpRepositoryImpl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Digging the grave on level 4, which is where a script first hands the party
 * something.
 *
 * The grave is asked twice whether to dig and gives up three things when the
 * answer is yes both times. It gives them up once only: the level flag it
 * sets is what stops a party digging the same grave for ever.
 */
class GraveDiggingTest {

    private val resources = ResourceRepositoryImpl()

    private val level = runBlocking {
        InfRepositoryImpl(
            resourceRepository = resources,
            mazRepository = MazRepositoryImpl(resources),
            vmpRepository = VmpRepositoryImpl(resources),
            vcnRepository = VcnRepositoryImpl(resources),
            palRepository = PalRepositoryImpl(resources),
            cpsRepository = CpsRepositoryImpl(resources),
            decRepository = DecRepositoryImpl(resources),
        ).loadInf("LEVEL4.INF").getOrThrow()
    }

    /** The table a played game keeps, which is the one with spare slots in it. */
    private val save = runBlocking {
        OriginalSaveRepositoryImpl(resources)
            .loadOriginalSave(OriginalSaveRepositoryImpl.QUICK_START).getOrThrow()
    }

    private val grave = Location(19, 4)

    private val world = GameState(
        party = PartyState(Location(19, 5), Direction.NORTH),
        champions = save.party,
        items = save.items,
    )

    /** Whatever is lying on the grave, however many things and wherever on it. */
    private fun GameState.onTheGrave(): List<Int> = items.indices
        .filter { items[it].level == LEVEL && items[it].location == grave }
        .map { items[it].icon.value }

    private fun dig(from: GameState = world, answering: Int = YES) = runBlocking {
        LevelScriptRunner(level.script, level = LEVEL).onEvent(
            triggers = level.triggers,
            event = ScriptEvent.WALL_CLICKED,
            state = from,
            stage = ScriptStage.silent(DialogAnswer(answering)),
            at = grave,
        )
    }

    @Test
    fun `an undug grave holds nothing`() {
        assertEquals(emptyList(), world.onTheGrave())
    }

    /** Three things come out of it, which is what the script asks for. */
    @Test
    fun `digging the grave turns up three things`() {
        val dug = dig().state

        assertEquals(3, dug.onTheGrave().size, "the grave gave up ${dug.onTheGrave()}")
    }

    /**
     * Two of them are alike and one is not, and none of them is a thing that
     * was already lying somewhere else — each is a copy made for the occasion.
     */
    @Test
    fun `two of the three are alike`() {
        val icons = dig().state.onTheGrave()

        assertEquals(2, icons.toSet().size, "expected two kinds among $icons")
    }

    /** Nothing else in the world is disturbed by making them. */
    @Test
    fun `digging leaves everything else where it was`() {
        val dug = dig().state

        val untouched = world.items.indices.count { world.items[it] == dug.items[it] }
        assertEquals(world.items.size - 3, untouched, "more than three slots changed")
    }

    /** Saying no leaves the grave alone. */
    @Test
    fun `refusing to dig turns up nothing`() {
        assertEquals(emptyList(), dig(answering = NO).state.onTheGrave())
    }

    /**
     * Whether anybody objects at all depends on who the party are. The game
     * ships with a paladin and a cleric, so the second question is put; a
     * party of neither is asked once and digs.
     */
    @Test
    fun `only a cleric or a paladin objects to digging`() {
        val asked = mutableListOf<Int>()
        val counting = object : ScriptStage by ScriptStage.silent() {
            override suspend fun ask(question: ScriptQuestion): DialogAnswer {
                asked += 1
                return DialogAnswer(YES)
            }
        }

        fun timesAsked(party: List<pl.pelotasplus.eyeofbeholder.data.model.Champion>): Int {
            asked.clear()
            runBlocking {
                LevelScriptRunner(level.script, level = LEVEL).onEvent(
                    triggers = level.triggers,
                    event = ScriptEvent.WALL_CLICKED,
                    state = world.copy(champions = party),
                    stage = counting,
                    at = grave,
                )
            }
            return asked.size
        }

        assertEquals(2, timesAsked(save.party), "nobody objected to a paladin and a cleric present")

        val nobodyPious = save.party.map { it.copy(characterClass = CharacterClass.FIGHTER) }
        assertEquals(1, timesAsked(nobodyPious), "somebody objected who should not have")
    }

    /**
     * A cleric or a paladin in the party objects to the digging and asks
     * again, and answering no to that objection calls the whole thing off.
     *
     * The script does that by setting the flag that says to dig and then
     * clearing it again, so a runner that cannot clear a flag digs the grave
     * anyway however the party answer.
     */
    @Test
    fun `the objection can call off a dig already agreed to`() {
        val answers = ArrayDeque(listOf(YES, NO))
        val stage = object : ScriptStage by ScriptStage.silent() {
            override suspend fun ask(question: ScriptQuestion) =
                DialogAnswer(answers.removeFirstOrNull() ?: NO)
        }

        val run = runBlocking {
            LevelScriptRunner(level.script, level = LEVEL).onEvent(
                triggers = level.triggers,
                event = ScriptEvent.WALL_CLICKED,
                state = world,
                stage = stage,
                at = grave,
            )
        }

        assertEquals(emptyList(), run.state.onTheGrave(), "the grave was dug over an objection")
    }

    /**
     * The flag the script sets is what makes the grave worth digging only
     * once, so digging what has already been dug turns up nothing more.
     */
    @Test
    fun `a grave gives up its things only once`() {
        val dug = dig().state
        val again = dig(from = dug).state

        assertEquals(3, again.onTheGrave().size, "the grave gave up more on a second dig")
    }

    /** What comes out is on the floor of the grave, not shelved in it. */
    @Test
    fun `what is dug up lies on the floor`() {
        cornersDugInto().forEach { assertTrue(it.onTheFloor, "$it is not on the floor") }
    }

    /**
     * The script names the corner each thing lands in, and it names the two
     * furthest from a party standing where the grave is dug from. Only the
     * near half of a square can be reached from outside it, so the party have
     * to step onto the grave to pick up what they have turned up.
     */
    @Test
    fun `what is dug up is out of reach until the party stand on the grave`() {
        val dugInto = cornersDugInto()

        val fromOutside = setOf(
            FloorReach.AHEAD_LEFT.placeFacing(Direction.NORTH),
            FloorReach.AHEAD_RIGHT.placeFacing(Direction.NORTH),
        )
        val standingOnIt = setOf(
            FloorReach.OWN_LEFT.placeFacing(Direction.NORTH),
            FloorReach.OWN_RIGHT.placeFacing(Direction.NORTH),
        )

        assertTrue(dugInto.none { it in fromOutside }, "$dugInto was reachable from outside")
        assertTrue(dugInto.all { it in standingOnIt }, "$dugInto is out of reach even standing on it")
    }

    private fun cornersDugInto(): Set<SquarePlace> = dig().state.let { dug ->
        dug.items.indices
            .filter { dug.items[it].level == LEVEL && dug.items[it].location == grave }
            .map { dug.items[it].place }
            .toSet()
    }

    private companion object {
        const val LEVEL = 4
        const val YES = 1
        const val NO = 2
    }
}
