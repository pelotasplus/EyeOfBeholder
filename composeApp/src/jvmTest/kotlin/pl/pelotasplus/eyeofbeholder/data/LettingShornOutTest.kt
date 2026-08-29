package pl.pelotasplus.eyeofbeholder.data

import kotlinx.coroutines.runBlocking
import pl.pelotasplus.eyeofbeholder.data.model.Champion
import pl.pelotasplus.eyeofbeholder.data.model.ChampionFlags
import pl.pelotasplus.eyeofbeholder.data.model.Dice
import pl.pelotasplus.eyeofbeholder.data.model.DialogueTextId
import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.FlagBit
import pl.pelotasplus.eyeofbeholder.data.model.GameState
import pl.pelotasplus.eyeofbeholder.data.model.Inf
import pl.pelotasplus.eyeofbeholder.data.model.LevelScriptRunner
import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.PartyState
import pl.pelotasplus.eyeofbeholder.data.model.ScriptEvent
import pl.pelotasplus.eyeofbeholder.data.model.TrackIndex
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
import kotlin.test.assertTrue

/**
 * The dwarf shut in a cell on the second floor, at 10x23.
 *
 * His meeting is two questions rather than one. He says his piece and asks to
 * be let out; only if he is does the game toss for whether he offers to come
 * along at all, and half the time he thanks the party and goes.
 *
 * The script around him is what the answers have to satisfy: it runs only
 * while the cell is unopened, and the moment the meeting is over it reads the
 * party's facing to decide whether to walk them up the corridor.
 */
class LettingShornOutTest {

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

    private val cell = Location(10, 23)

    /**
     * @param answers a button index per question, in the order they are put.
     * @param toss what a coin comes up as, which decides whether he offers to
     *   come along. One is the half where he goes his own way.
     */
    private fun arriving(answers: List<Int>, toss: Int = GOES_OWN_WAY): Meeting {
        val stage = RecordingStage(answers)

        val world = runBlocking {
            LevelScriptRunner(
                level.script,
                level = 2,
                dice = { _, _, _ -> toss },
            ).onEvent(
                triggers = level.triggers,
                event = ScriptEvent.PARTY_ENTERED,
                state = GameState(
                    party = PartyState(cell, Direction.NORTH),
                    champions = aParty(),
                ),
                stage = stage,
                at = cell,
            ).state
        }

        return Meeting(stage, world)
    }

    private data class Meeting(val stage: RecordingStage, val world: GameState)

    /** Two champions, so there is room for him without asking anybody to leave. */
    private fun aParty() = List(6) { slot ->
        if (slot < 2) {
            Champion.NOBODY.copy(name = "One", flags = ChampionFlags(1))
        } else {
            Champion.NOBODY
        }
    }

    @Test
    fun `he is heard, and asks to be let out rather than to come along`() {
        val met = arriving(answers = listOf(LEAVE))

        assertTrue(TrackIndex(55) in met.stage.played, "nothing was heard of him")

        val asked = met.stage.questions.first()
        assertEquals(DialogueTextId(8), asked.textId, "he says the wrong piece")
        assertEquals(
            listOf("release him", "leave"),
            asked.words,
            "the answers are not the two the cell offers",
        )
    }

    /**
     * Left where he is, he says nothing at all — and the party end up facing
     * north, which is the only thing the script has to go on: it tests their
     * facing straight afterwards and walks them off up the corridor unless it
     * finds them turned.
     */
    @Test
    fun `leaving him turns the party instead of answering`() {
        val met = arriving(answers = listOf(LEAVE))

        assertEquals(1, met.stage.questions.size, "he said something to being left")
        assertEquals(Direction.NORTH, met.world.party.facing, "the party were not turned")
        assertFalse(
            met.world.isGlobalFlagSet(DEALT_WITH),
            "a cell nobody opened is remembered as opened",
        )
    }

    /**
     * The half of the tosses where being let out is the whole of it: he has
     * his piece to say about it and then goes, and the party are not asked
     * whether he can come.
     */
    @Test
    fun `freed, he thanks the party and goes on the toss that says so`() {
        val met = arriving(answers = listOf(RELEASE), toss = GOES_OWN_WAY)

        assertEquals(
            listOf(DialogueTextId(8), DialogueTextId(9)),
            met.stage.questions.map { it.textId },
            "he was asked along on the toss where he leaves",
        )
        assertTrue(met.world.isGlobalFlagSet(DEALT_WITH), "the cell is not remembered")
    }

    /** And the other half, where being let out is followed by the usual offer. */
    @Test
    fun `freed, he asks to come along on the other toss`() {
        val met = arriving(answers = listOf(RELEASE, YES), toss = COMES_ALONG)

        assertEquals(
            listOf(DialogueTextId(8), DialogueTextId(102), DialogueTextId(103)),
            met.stage.questions.map { it.textId },
        )
        assertTrue(
            met.world.champions.any { it.name == "Shorn" },
            "he was let along and did not turn up in the party",
        )
        assertTrue(met.world.isGlobalFlagSet(DEALT_WITH), "the cell is not remembered")
    }

    /** Turned down after all that, he says his piece about it and stays put. */
    @Test
    fun `freed and then refused, he is answered and does not join`() {
        val met = arriving(answers = listOf(RELEASE, NO), toss = COMES_ALONG)

        assertEquals(
            listOf(DialogueTextId(8), DialogueTextId(102), DialogueTextId(104)),
            met.stage.questions.map { it.textId },
        )
        assertFalse(met.world.champions.any { it.name == "Shorn" }, "he came along anyway")
    }

    private companion object {
        // Buttons are counted from one, the way an answer comes back.
        const val RELEASE = 1
        const val LEAVE = 2
        const val YES = 1
        const val NO = 2

        /** What the toss has to come up as for him to go his own way. */
        const val GOES_OWN_WAY = 1
        const val COMES_ALONG = 0

        val DEALT_WITH = FlagBit(3)
    }
}
