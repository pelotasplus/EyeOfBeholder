package pl.pelotasplus.eyeofbeholder.data

import kotlinx.coroutines.runBlocking
import org.junit.experimental.categories.Category
import pl.pelotasplus.eyeofbeholder.NeedsGameData
import pl.pelotasplus.eyeofbeholder.data.model.DialogAnswer
import pl.pelotasplus.eyeofbeholder.data.model.DialogueTextId
import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.GameState
import pl.pelotasplus.eyeofbeholder.data.model.Champion
import pl.pelotasplus.eyeofbeholder.data.model.Inf
import pl.pelotasplus.eyeofbeholder.data.model.LevelScriptRunner
import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.PartyState
import pl.pelotasplus.eyeofbeholder.data.model.ScriptEvent
import pl.pelotasplus.eyeofbeholder.data.model.Ticks
import pl.pelotasplus.eyeofbeholder.data.model.WallSide
import pl.pelotasplus.eyeofbeholder.data.model.canBeWalkedOnto
import pl.pelotasplus.eyeofbeholder.data.model.getWall
import pl.pelotasplus.eyeofbeholder.data.model.speakerFrom
import pl.pelotasplus.eyeofbeholder.data.model.spokenBy
import pl.pelotasplus.eyeofbeholder.data.model.script.NewLevelOrMonster
import pl.pelotasplus.eyeofbeholder.data.repository.CpsRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.DecRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.InfRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.ItemsRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.MazRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.PalRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.ResourceRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.OriginalSaveRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.VcnRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.VmpRepositoryImpl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The Block C trigger map and the script interpreter that moves the party
 * between levels.
 */
@Category(NeedsGameData::class)
class LevelTransitionTest {

    @Test
    fun `every level parses its trigger map`() {
        val names = runBlocking { resources.listResources(".INF").getOrThrow() }
        val counts = names.associateWith { runBlocking { load(it).triggers.size } }

        assertTrue(
            counts.values.any { it > 0 },
            "no level declared any trigger, Block C is probably being misparsed"
        )
        println("triggers per level: ${counts.entries.sortedBy { it.key }.joinToString()}")
    }

    @Test
    fun `the level 4 stairs ask before taking the party down`() = runBlocking {
        val level = load("LEVEL4.INF")
        val stage = RecordingStage()

        LevelScriptRunner(level.script).onEvent(
            triggers = level.triggers,
            event = ScriptEvent.PARTY_ENTERED,
            state = GameState(PartyState(Location(15, 10), Direction.NORTH)),
            stage = stage,
        )

        val question = stage.questions.firstOrNull()
        assertTrue(question != null, "expected entering (15,10) to ask the player")
        assertEquals(
            listOf("yes", "no"),
            question.buttons.mapNotNull { level.message(it) },
        )
    }

    @Test
    fun `saying yes to the level 4 stairs goes down and saying no does not`() = runBlocking {
        val level = load("LEVEL4.INF")
        val party = GameState(PartyState(Location(15, 10), Direction.NORTH))

        val yes = LevelScriptRunner(level.script).onEvent(
            level.triggers, ScriptEvent.PARTY_ENTERED, party, RecordingStage(answers = listOf(1)),
        )
        val goesDown = yes.changeLevel
        assertTrue(goesDown != null, "yes should go down")
        assertEquals(5, goesDown.level)
        assertEquals(Location(14, 9), goesDown.location)

        val no = LevelScriptRunner(level.script).onEvent(
            level.triggers, ScriptEvent.PARTY_ENTERED, party, RecordingStage(answers = listOf(2)),
        )
        assertEquals(null, no.changeLevel, "no should stay on this level")
        assertEquals(
            Location(16, 10),
            no.state.party.position,
            "no should step the party back off the stairs",
        )
    }

    /**
     * Asking the woman by the temple to lead the party there is the game's
     * longest scripted walk: four squares with a beat between each, her
     * parting word, and a last step that puts the party on the door — which
     * asks its own question, because arriving is arriving however you got
     * there.
     */
    @Test
    fun `the level 4 woman walks the party to the temple door`() = runBlocking {
        val level = load("LEVEL4.INF")

        // inquire, then lead us, then read her word, then decline the door
        val stage = RecordingStage(answers = listOf(1, 1, 1, 2))

        val run = LevelScriptRunner(level.script, level = 4).onEvent(
            level.triggers,
            ScriptEvent.PARTY_ENTERED,
            GameState(PartyState(Location(12, 11), Direction.NORTH), monsters = level.monsterInstances),
            stage,
        )

        assertEquals(
            listOf(Location(15, 14), Location(15, 13), Location(15, 12), Location(15, 11)),
            stage.shown.map { it.party.position },
            "she walks them a square at a time rather than putting them there",
        )
        assertEquals(List(4) { Ticks(15) }, stage.holds, "with a pause on each step")
        assertEquals(
            listOf(14, 16, 17, 18),
            stage.questions.map { it.textId.number },
            "she asks, answers, says where they are, and the door asks them in",
        )
        assertEquals(null, run.changeLevel, "declining the door stays on this level")
        assertEquals(
            Location(15, 11),
            run.state.party.position,
            "and leaves the party standing in front of it, not back in the wood",
        )
    }

    /**
     * The staircase down from level 6 tests which way the party face before it
     * lets them past: they must be looking down it. Anyone who wanders on
     * sideways is put back on the square they came from.
     */
    @Test
    fun `the level 6 staircase only takes the party who face it`() = runBlocking {
        val level = load("LEVEL6.INF")
        val onTheStairs = Location(10, 4)

        suspend fun step(facing: Direction) = LevelScriptRunner(level.script, level = 6).onEvent(
            level.triggers,
            ScriptEvent.PARTY_ENTERED,
            GameState(PartyState(onTheStairs, facing), monsters = level.monsterInstances),
        )

        val down = step(Direction.SOUTH)
        assertEquals(5, down.changeLevel?.level, "facing down the stairs goes down them")
        assertEquals(Location(10, 7), down.changeLevel?.location)
        assertEquals(Direction.SOUTH, down.changeLevel?.direction)

        Direction.entries.filter { it != Direction.SOUTH }.forEach { facing ->
            val turned = step(facing)
            assertEquals(null, turned.changeLevel, "facing $facing should not go down")
            assertEquals(
                Location(10, 3),
                turned.state.party.position,
                "facing $facing should be put back off the stairs",
            )
        }
    }

    /**
     * The stairs at the east end of level 1, which the party reach from 29x15.
     * They are drawn as a wall and are not one to the party: the step onto
     * 30x15 is what runs the script at all, and the script then asks the same
     * thing level 6's staircase asks — that they be looking up the flight and
     * not merely standing on it — before taking them to the forest at 17x15.
     */
    @Test
    fun `the level 1 stairs are walked into and take the party up`() = runBlocking {
        val level = load("LEVEL1.INF")
        val sub = level.subLevels[0]
        val stairs = Location(30, 15)

        assertTrue(
            sub.canBeWalkedOnto(sub.maz.square(stairs).getWall(WallSide.WEST)),
            "the party cannot walk into the stairs, so nothing runs",
        )

        suspend fun step(facing: Direction) = LevelScriptRunner(level.script, level = 1).onEvent(
            level.triggers,
            ScriptEvent.PARTY_ENTERED,
            GameState(PartyState(stairs, facing), monsters = level.monsterInstances),
        )

        val up = step(Direction.EAST)
        assertEquals(4, up.changeLevel?.level, "facing up the stairs goes up them")
        assertEquals(Location(17, 15), up.changeLevel?.location)

        Direction.entries.filter { it != Direction.EAST }.forEach { facing ->
            val turned = step(facing)
            assertEquals(null, turned.changeLevel, "facing $facing should not go up")
            assertEquals(
                Location(29, 15),
                turned.state.party.position,
                "facing $facing should be put back off the stairs",
            )
        }
    }

    @Test
    fun `a trigger that does not react to entering stays silent`() = runBlocking {
        val level = load("LEVEL4.INF")
        val stage = RecordingStage()

        // (17,4) has flags 0x0: it reacts to a wall click, not to the party
        LevelScriptRunner(level.script).onEvent(
            triggers = level.triggers,
            event = ScriptEvent.PARTY_ENTERED,
            state = GameState(PartyState(Location(17, 4), Direction.NORTH)),
            stage = stage,
        )

        assertEquals(emptyList(), stage.beats)
    }

    /**
     * The pair on (13,8) speaks when the party steps in front of them, and the
     * script asks by counting the monsters standing on their square.
     */
    @Test
    fun `the level 5 encounter speaks only while its monsters are alive`() = runBlocking {
        val level = load("LEVEL5.INF")
        val party = PartyState(Location(13, 9), Direction.NORTH)

        val alive = RecordingStage()
        LevelScriptRunner(level.script).onEvent(
            level.triggers,
            ScriptEvent.PARTY_ENTERED,
            GameState(party, monsters = level.monsterInstances),
            alive,
        )
        assertTrue(alive.questions.isNotEmpty(), "expected a question")

        val killed = RecordingStage()
        LevelScriptRunner(level.script).onEvent(
            level.triggers,
            ScriptEvent.PARTY_ENTERED,
            GameState(party, monsters = emptyList()),
            killed,
        )
        assertEquals(emptyList(), killed.questions)
    }

    /**
     * The clerics can be approached from three sides, and each square has a
     * flag of its own so that way in does not speak twice — the encounter is
     * not one flag saying "done".
     */
    @Test
    fun `every way in to the level 5 clerics speaks once`() = runBlocking {
        val level = load("LEVEL5.INF")
        val runner = LevelScriptRunner(level.script, level = 5)

        var state = GameState(
            PartyState(Location(13, 9), Direction.NORTH),
            monsters = level.monsterInstances,
        )

        suspend fun stepOnto(x: Int, y: Int): Boolean {
            val stage = RecordingStage()
            val run = runner.onEvent(
                level.triggers,
                ScriptEvent.PARTY_ENTERED,
                state.copy(party = state.party.copy(position = Location(x, y))),
                stage,
            )
            state = run.state
            return stage.questions.isNotEmpty()
        }

        val ways = listOf(13 to 9, 13 to 11, 11 to 9)

        ways.forEach { (x, y) ->
            assertTrue(stepOnto(x, y), "approaching from ${x}x$y should speak")
        }

        assertTrue(!stepOnto(13, 9), "the same way in should not speak twice")

        // and none of them waits on another having spoken first
        ways.forEach { (x, y) ->
            val stage = RecordingStage()
            LevelScriptRunner(level.script, level = 5).onEvent(
                level.triggers,
                ScriptEvent.PARTY_ENTERED,
                GameState(PartyState(Location(x, y), Direction.NORTH), monsters = level.monsterInstances),
                stage,
            )
            assertTrue(
                stage.questions.isNotEmpty(),
                "approaching from ${x}x$y first should speak",
            )
        }

        // leaving the level and coming back does not make them greet the party
        // again: the flags belong to the game, not to the runner
        val returned = RecordingStage()
        LevelScriptRunner(level.script, level = 5).onEvent(
            level.triggers,
            ScriptEvent.PARTY_ENTERED,
            state.copy(party = state.party.copy(position = Location(13, 9))),
            returned,
        )
        assertEquals(
            emptyList(),
            returned.questions,
            "the clerics should stay quiet on a return visit",
        )
    }

    /**
     * Inquiring gets an answer, and the answer waits to be read before the
     * script goes on — the clerics deny having seen Amber, then the
     * conversation ends.
     */
    @Test
    fun `the level 5 clerics reply and wait to be read`() = runBlocking {
        val level = load("LEVEL5.INF")
        val state = GameState(
            PartyState(Location(13, 9), Direction.NORTH),
            monsters = level.monsterInstances,
        )
        val stage = RecordingStage(answers = listOf(1))

        LevelScriptRunner(level.script)
            .onEvent(level.triggers, ScriptEvent.PARTY_ENTERED, state, stage)

        val (ask, reply) = stage.questions
        assertEquals(
            listOf("inquire", "attack", "leave"),
            ask.buttons.mapNotNull { level.message(it) },
        )

        assertEquals(DialogueTextId(23), reply.textId)
        assertEquals(listOf("ok"), reply.buttons.mapNotNull { level.message(it) })
        assertTrue(reply.waitsToBeRead, "the reply is read, not answered")

        assertEquals(
            2,
            stage.questions.size,
            "reading the reply should end the conversation",
        )
    }

    /**
     * Leaving is a two line exchange: the party asks to rest, and only once
     * that has been read does Nadia answer, on a box drawn clean.
     */
    @Test
    fun `taking leave of the level 5 clerics prints the party's line first`() = runBlocking {
        val level = load("LEVEL5.INF")
        val party = PartyState(Location(13, 9), Direction.NORTH)
        val state = GameState(party, monsters = level.monsterInstances)
        val stage = RecordingStage(answers = listOf(3))

        LevelScriptRunner(level.script)
            .onEvent(level.triggers, ScriptEvent.PARTY_ENTERED, state, stage)

        val (_, asked, replied) = stage.questions
        // the line names whoever the roll landed on, so the roll is fixed here
        val speaker = quickStartParty().speakerFrom(0)
        assertEquals(
            listOf("""PERICLES: "may we rest a moment in your temple?""""),
            asked.said.mapNotNull { level.message(it) }.map { it.spokenBy(speaker).trim() },
        )

        assertEquals(DialogueTextId(24), replied.textId)
        assertTrue(
            replied.said.isEmpty(),
            "drawing the box again should wipe the party's line, kept ${replied.said}",
        )
    }

    @Test
    fun `the level 4 change targets match the level data`() {
        val level = load("LEVEL4.INF")
        val targets = level.script
            .map { it.token }
            .filterIsInstance<NewLevelOrMonster.ChangeLevel>()
            .map { it.level }

        assertEquals(listOf(5, 1), targets, "level 4 should lead to levels 5 and 1")
    }

    private val resources = ResourceRepositoryImpl()

    private fun quickStartParty(): List<Champion> = runBlocking {
        OriginalSaveRepositoryImpl(resources)
            .loadOriginalSave(OriginalSaveRepositoryImpl.QUICK_START)
            .getOrThrow()
            .party
    }

    private fun load(name: String): Inf = runBlocking {
        val pal = PalRepositoryImpl(resources)
        InfRepositoryImpl(
            resourceRepository = resources,
            mazRepository = MazRepositoryImpl(resources),
            vmpRepository = VmpRepositoryImpl(resources),
            vcnRepository = VcnRepositoryImpl(resources),
            palRepository = pal,
            cpsRepository = CpsRepositoryImpl(resources),
            decRepository = DecRepositoryImpl(resources),
        ).loadInf(name).getOrThrow()
    }
}
