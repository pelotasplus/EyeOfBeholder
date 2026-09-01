package pl.pelotasplus.eyeofbeholder.data

import kotlinx.coroutines.runBlocking
import org.junit.experimental.categories.Category
import pl.pelotasplus.eyeofbeholder.NeedsGameData
import pl.pelotasplus.eyeofbeholder.data.model.Champion
import pl.pelotasplus.eyeofbeholder.data.model.CarrySlot
import pl.pelotasplus.eyeofbeholder.data.model.ChampionFlags
import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.FlagBit
import pl.pelotasplus.eyeofbeholder.data.model.GameState
import pl.pelotasplus.eyeofbeholder.data.model.Inf
import pl.pelotasplus.eyeofbeholder.data.model.ItemIndex
import pl.pelotasplus.eyeofbeholder.data.model.LevelScriptRunner
import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.NpcMeeting
import pl.pelotasplus.eyeofbeholder.data.model.PartyState
import pl.pelotasplus.eyeofbeholder.data.model.ScriptEvent
import pl.pelotasplus.eyeofbeholder.data.model.TrackIndex
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The fighter dying in the catacombs on the second floor, at 13x26.
 *
 * Her meeting is two questions like the dwarf's, and unlike his the first one
 * decides nothing by chance: talking to her leads straight to being asked
 * along, and walking away is remembered so that she is never met again.
 *
 * Everything asserted here is read off the game's own branches rather than off
 * what the code does with them — which speech, which two words, which bit, and
 * on which answer the bit is written.
 */
@Category(NeedsGameData::class)
class MeetingCalandraTest {

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

    private val items = runBlocking {
        ItemsRepositoryImpl(resources).loadItems().getOrThrow().items
    }

    private val met = Location(13, 26)

    private fun arriving(answers: List<Int>, party: List<Champion> = twoOfThem()): Meeting {
        val stage = RecordingStage(answers)

        val world = runBlocking {
            LevelScriptRunner(level.script, level = 2).onEvent(
                triggers = level.triggers,
                event = ScriptEvent.PARTY_ENTERED,
                state = GameState(
                    party = PartyState(met, Direction.NORTH),
                    champions = party,
                    items = items,
                ),
                stage = stage,
                at = met,
            ).state
        }

        return Meeting(stage, world)
    }

    private data class Meeting(val stage: RecordingStage, val world: GameState)

    /** Room for her without anybody being asked to leave. */
    private fun twoOfThem() = List(6) { slot ->
        if (slot < 2) named("One$slot") else Champion.NOBODY
    }

    /** A full party, so that making room has to be asked about. */
    private fun sixOfThem() = List(6) { slot -> named("Name$slot") }

    private fun named(name: String) = Champion.NOBODY.copy(
        name = name,
        flags = ChampionFlags(1),
        carrying = CarrySlot.NOTHING_IN_ANY,
    )

    // --- the meeting itself ---------------------------------------------------

    @Test
    fun `she is heard, and speaks before she is asked along`() {
        val meeting = arriving(answers = listOf(LEAVE))

        assertTrue(TrackIndex(53) in meeting.stage.played, "nothing was heard of her")

        val asked = meeting.stage.questions.first()
        assertEquals(4, asked.textId?.number, "she says the wrong piece")
        assertEquals(
            listOf("talk", "leave"),
            asked.words,
            "the two answers are not the ones she offers",
        )
    }

    /**
     * Walking away writes the bit and nothing else happens: she asks nothing
     * further, and the party keep the six or two they came with.
     */
    @Test
    fun `walking away from her is remembered`() {
        val meeting = arriving(answers = listOf(LEAVE))

        assertEquals(1, meeting.stage.questions.size, "she went on talking")
        assertTrue(
            meeting.world.isGlobalFlagSet(FlagBit(5)),
            "walking away was not remembered, so she would be met again",
        )
        assertTrue(
            meeting.world.champions.none { it.name == "Calandra" },
            "she came along after being left",
        )
    }

    /** Talking leads to the other question, which is the ordinary one. */
    @Test
    fun `talking to her leads to being asked along`() {
        val meeting = arriving(answers = listOf(TALK, NO))

        assertEquals(
            listOf(4, 5, 7),
            meeting.stage.questions.mapNotNull { it.textId?.number },
            "she should say her piece, ask, and answer being turned down",
        )
        assertFalse(
            meeting.world.isGlobalFlagSet(FlagBit(5)),
            "the bit is for walking away, not for turning her down",
        )
    }

    @Test
    fun `let along she joins as a ninth level fighter nearly dead`() {
        val meeting = arriving(answers = listOf(TALK, YES))

        val her = meeting.world.champions.first { it.name == "Calandra" }

        assertEquals(4, her.hitPoints.current, "she is found nearly dead")
        assertEquals(76, her.hitPoints.max)
        assertEquals(9, her.levels.first().level)
        assertTrue(her.inTheParty)
    }

    // --- a party with no room -------------------------------------------------

    /**
     * Six is the whole party, so somebody has to go first. The question is
     * their six names and a way out of it, and it is put only after she has
     * been told she may come — which is the order the game puts them in.
     */
    @Test
    fun `a full party are asked which of them leaves`() {
        val meeting = arriving(answers = listOf(TALK, YES, OK, ABORT), party = sixOfThem())

        val choice = meeting.stage.questions.last()

        assertEquals(
            listOf("Name0", "Name1", "Name2", "Name3", "Name4", "Name5", NpcMeeting.ABORT),
            choice.words,
            "the choice is the six of them and a way out",
        )
        assertEquals(NpcMeeting.ONLY_SIX, choice.spoken)
        assertEquals(null, choice.textId, "no level has this sentence to give")
    }

    @Test
    fun `taking the way out leaves the party as they were`() {
        val meeting = arriving(answers = listOf(TALK, YES, OK, ABORT), party = sixOfThem())

        assertEquals(
            sixOfThem().map { it.name },
            meeting.world.champions.map { it.name },
            "somebody was dropped after the party refused to drop anybody",
        )
        assertTrue(meeting.world.champions.none { it.name == "Calandra" })
    }

    @Test
    fun `dropping somebody makes room and she takes their place`() {
        val meeting = arriving(answers = listOf(TALK, YES, OK, THIRD), party = sixOfThem())

        assertTrue(
            meeting.world.champions.none { it.name == "Name2" && it.inTheParty },
            "the one chosen is still in the party",
        )
        assertTrue(
            meeting.world.champions.any { it.name == "Calandra" && it.inTheParty },
            "she did not take the place that was made",
        )
        assertEquals(
            6,
            meeting.world.champions.count { it.inTheParty },
            "the party should still be six",
        )
    }

    /**
     * What the dropped champion carried is not destroyed with them: it lands
     * on the square the party are standing on, so it can be picked back up.
     */
    @Test
    fun `what the dropped champion carried lands underfoot`() {
        val carried = ItemIndex(1)
        val party = sixOfThem().mapIndexed { slot, who ->
            if (slot == 2) {
                who.copy(carrying = who.carrying.toMutableList().also { it[0] = carried })
            } else {
                who
            }
        }

        val meeting = arriving(answers = listOf(TALK, YES, OK, THIRD), party = party)
        val dropped = meeting.world.item(carried)

        assertEquals(met, dropped?.location, "it did not land where the party stand")
        assertEquals(2, dropped?.level, "it landed on the wrong floor")
        assertTrue(
            meeting.world.champions.none { carried in it.carrying },
            "somebody is still holding it",
        )
    }

    private companion object {
        // Buttons are counted from one, the way an answer comes back.
        const val TALK = 1
        const val LEAVE = 2
        const val YES = 1
        const val NO = 2

        /** Reading her answer, which is a question with one button on it. */
        const val OK = 1

        /** The third of the six names, and the seventh button. */
        const val THIRD = 3
        const val ABORT = 7
    }
}
