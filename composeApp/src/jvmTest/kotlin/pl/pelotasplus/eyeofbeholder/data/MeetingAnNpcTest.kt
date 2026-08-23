package pl.pelotasplus.eyeofbeholder.data

import kotlinx.coroutines.runBlocking
import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.DialogueTextId
import pl.pelotasplus.eyeofbeholder.data.model.GameState
import pl.pelotasplus.eyeofbeholder.data.model.Inf
import pl.pelotasplus.eyeofbeholder.data.model.LevelScriptRunner
import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.NpcMeeting
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
import kotlin.test.assertTrue

/**
 * The person waiting on level 1 at 15x11, who steps up as the party arrive.
 *
 * The square's script sets the flag that says the meeting has happened, turns
 * the party to face west, and hands over to the meeting. What the meeting is
 * made of is the original's: a sound, a piece that ends in asking to come
 * along, and one of two answers to what the party say.
 */
class MeetingAnNpcTest {

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
        ).loadInf("LEVEL1.INF").getOrThrow()
    }

    private val met = Location(15, 11)

    private fun arriving(answers: List<Int>): RecordingStage {
        val stage = RecordingStage(answers)

        runBlocking {
            LevelScriptRunner(level.script, level = 1).onEvent(
                triggers = level.triggers,
                event = ScriptEvent.PARTY_ENTERED,
                state = GameState(PartyState(met, Direction.NORTH)),
                stage = stage,
                at = met,
            )
        }

        return stage
    }

    @Test
    fun `arriving is heard, and the party are turned to face whoever it is`() {
        val stage = arriving(answers = listOf(NO))

        assertTrue(TrackIndex(57) in stage.played, "nothing was heard of them arriving")
        assertTrue(stage.tookTheParty, "the party were not turned to face them")
    }

    /**
     * The turn is on screen before they are: the party are faced round to
     * whoever it is, the view is drawn again, and only then do they step into
     * it and speak. Drawn the other way about, they stand in front of whatever
     * the party were looking at before.
     */
    @Test
    fun `the view is drawn again before they are in it`() {
        val beats = arriving(answers = listOf(NO)).beats

        val shown = beats.indexOfFirst { it is RecordingStage.Beat.Shown }
        val spoke = beats.indexOfFirst { it is RecordingStage.Beat.Asked }

        assertTrue(shown in 0 until spoke, "they spoke before the view was drawn again")
        assertEquals(
            Direction.WEST,
            (beats[shown] as RecordingStage.Beat.Shown).world.party.facing,
            "the view drawn for them is not the one the party were turned to",
        )
    }

    /** The piece they say is asked as a question, with two ways to answer it. */
    @Test
    fun `they ask to come along`() {
        val asked = arriving(answers = listOf(NO)).questions.first()

        assertEquals(DialogueTextId(1), asked.textId)
        assertEquals(listOf(NpcMeeting.YES, NpcMeeting.NO), asked.words)
    }

    @Test
    fun `saying yes is answered by what they say to being let along`() {
        val questions = arriving(answers = listOf(YES)).questions

        assertEquals(
            listOf(DialogueTextId(1), DialogueTextId(3)),
            questions.map { it.textId },
        )
    }

    @Test
    fun `and saying no by what they say to being turned down`() {
        val questions = arriving(answers = listOf(NO)).questions

        assertEquals(
            listOf(DialogueTextId(1), DialogueTextId(2)),
            questions.map { it.textId },
        )
    }

    /**
     * Both answers are read rather than answered: whatever they say to it is
     * the end of the meeting, and the word in the corner takes it down.
     */
    @Test
    fun `what they say to either is read and then done with`() {
        listOf(YES, NO).forEach { answer ->
            val said = arriving(answers = listOf(answer)).questions.last()

            assertTrue(said.waitsToBeRead, "answer $answer left something to answer")
        }
    }

    private companion object {
        const val YES = 1
        const val NO = 2
    }
}
