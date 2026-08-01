package pl.pelotasplus.eyeofbeholder.data

import kotlinx.coroutines.runBlocking
import pl.pelotasplus.eyeofbeholder.data.model.DialogAnswer
import pl.pelotasplus.eyeofbeholder.data.model.DialogueTextId
import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.GameState
import pl.pelotasplus.eyeofbeholder.data.model.Inf
import pl.pelotasplus.eyeofbeholder.data.model.LevelScriptRunner
import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.PartyState
import pl.pelotasplus.eyeofbeholder.data.model.ScriptEvent
import pl.pelotasplus.eyeofbeholder.data.model.ScriptStop
import pl.pelotasplus.eyeofbeholder.data.model.script.NewLevelOrMonster
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
import kotlin.test.assertTrue

/**
 * The Block C trigger map and the script interpreter that moves the party
 * between levels.
 */
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
    fun `the level 4 stairs ask before taking the party down`() {
        val level = load("LEVEL4.INF")
        val runner = LevelScriptRunner(level.script)

        val stop = runner.onEvent(
            triggers = level.triggers,
            event = ScriptEvent.PARTY_ENTERED,
            state = GameState(PartyState(Location(15, 10), Direction.NORTH)),
        ).stoppedTo

        assertTrue(
            stop is ScriptStop.AskThePlayer,
            "expected entering (15,10) to ask the player, got $stop"
        )
        assertEquals(
            listOf("yes", "no"),
            stop.buttons.mapNotNull { level.message(it) },
        )
    }

    @Test
    fun `saying yes to the level 4 stairs goes down and saying no does not`() {
        val level = load("LEVEL4.INF")
        val party = GameState(PartyState(Location(15, 10), Direction.NORTH))
        val ask = LevelScriptRunner(level.script).onEvent(
            level.triggers, ScriptEvent.PARTY_ENTERED, party,
        ).stoppedTo as ScriptStop.AskThePlayer

        val yes = LevelScriptRunner(level.script).answer(ask.resumeAt, party, DialogAnswer(1))
        val goesDown = yes.stoppedTo
        assertTrue(goesDown is ScriptStop.ChangeLevel, "yes should go down, got $goesDown")
        assertEquals(5, goesDown.level)
        assertEquals(Location(14, 9), goesDown.location)

        val no = LevelScriptRunner(level.script).answer(ask.resumeAt, party, DialogAnswer(2))
        assertEquals(null, no.stoppedTo, "no should stay on this level")
        assertEquals(
            Location(16, 10),
            no.state.party.position,
            "no should step the party back off the stairs",
        )
    }

    @Test
    fun `a trigger that does not react to entering stays silent`() {
        val level = load("LEVEL4.INF")
        val runner = LevelScriptRunner(level.script)

        // (17,4) has flags 0x0: it reacts to a wall click, not to the party
        val run = runner.onEvent(
            triggers = level.triggers,
            event = ScriptEvent.PARTY_ENTERED,
            state = GameState(PartyState(Location(17, 4), Direction.NORTH)),
        )

        assertEquals(null, run.stoppedTo)
    }

    /**
     * The pair on (13,8) speaks when the party steps in front of them, and the
     * script asks by counting the monsters standing on their square.
     */
    @Test
    fun `the level 5 encounter speaks only while its monsters are alive`() {
        val level = load("LEVEL5.INF")
        val party = PartyState(Location(13, 9), Direction.NORTH)

        val alive = LevelScriptRunner(level.script).onEvent(
            level.triggers,
            ScriptEvent.PARTY_ENTERED,
            GameState(party, level.monsterInstances),
        ).stoppedTo
        assertTrue(alive is ScriptStop.AskThePlayer, "expected a question, got $alive")

        val killed = LevelScriptRunner(level.script).onEvent(
            level.triggers,
            ScriptEvent.PARTY_ENTERED,
            GameState(party, monsters = emptyList()),
        )
        assertEquals(null, killed.stoppedTo)
    }

    /**
     * The clerics can be approached from three sides, and each square has a
     * flag of its own so that way in does not speak twice — the encounter is
     * not one flag saying "done".
     */
    @Test
    fun `every way in to the level 5 clerics speaks once`() {
        val level = load("LEVEL5.INF")
        val runner = LevelScriptRunner(level.script, level = 5)

        var state = GameState(
            PartyState(Location(13, 9), Direction.NORTH),
            level.monsterInstances,
        )

        fun stepOnto(x: Int, y: Int): ScriptStop? {
            val run = runner.onEvent(
                level.triggers,
                ScriptEvent.PARTY_ENTERED,
                state.copy(party = state.party.copy(position = Location(x, y))),
            )
            state = run.state
            return run.stoppedTo
        }

        val ways = listOf(13 to 9, 13 to 11, 11 to 9)

        ways.forEach { (x, y) ->
            assertTrue(
                stepOnto(x, y) is ScriptStop.AskThePlayer,
                "approaching from ${x}x$y should speak",
            )
        }

        assertEquals(null, stepOnto(13, 9), "the same way in should not speak twice")

        // and none of them waits on another having spoken first
        ways.forEach { (x, y) ->
            val fresh = LevelScriptRunner(level.script, level = 5)
            assertTrue(
                fresh.onEvent(
                    level.triggers,
                    ScriptEvent.PARTY_ENTERED,
                    GameState(PartyState(Location(x, y), Direction.NORTH), level.monsterInstances),
                ).stoppedTo is ScriptStop.AskThePlayer,
                "approaching from ${x}x$y first should speak",
            )
        }

        // leaving the level and coming back does not make them greet the party
        // again: the flags belong to the game, not to the runner
        val returned = LevelScriptRunner(level.script, level = 5).onEvent(
            level.triggers,
            ScriptEvent.PARTY_ENTERED,
            state.copy(party = state.party.copy(position = Location(13, 9))),
        )
        assertEquals(null, returned.stoppedTo, "the clerics should stay quiet on a return visit")
    }

    /**
     * Inquiring gets an answer, and the answer waits to be read before the
     * script goes on — the clerics deny having seen Amber, then the
     * conversation ends.
     */
    @Test
    fun `the level 5 clerics reply and wait to be read`() {
        val level = load("LEVEL5.INF")
        val state = GameState(
            PartyState(Location(13, 9), Direction.NORTH),
            level.monsterInstances,
        )
        val runner = LevelScriptRunner(level.script)

        val ask = runner.onEvent(level.triggers, ScriptEvent.PARTY_ENTERED, state)
            .stoppedTo as ScriptStop.AskThePlayer
        assertEquals(
            listOf("inquire", "attack", "leave"),
            ask.buttons.mapNotNull { level.message(it) },
        )

        val reply = runner.answer(ask.resumeAt, state, DialogAnswer(1)).stoppedTo
        assertTrue(reply is ScriptStop.AskThePlayer, "inquiring should reply, got $reply")
        assertEquals(DialogueTextId(23), reply.textId)
        assertEquals(listOf("ok"), reply.buttons.mapNotNull { level.message(it) })

        assertEquals(
            null,
            runner.answer(reply.resumeAt, state, DialogAnswer(1)).stoppedTo,
            "reading the reply should end the conversation",
        )
    }

    /**
     * Leaving is a two line exchange: the party asks to rest, and only once
     * that has been read does Nadia answer, on a box drawn clean.
     */
    @Test
    fun `taking leave of the level 5 clerics prints the party's line first`() {
        val level = load("LEVEL5.INF")
        val party = PartyState(Location(13, 9), Direction.NORTH)
        val state = GameState(party, level.monsterInstances)
        val runner = LevelScriptRunner(level.script)

        val ask = runner.onEvent(level.triggers, ScriptEvent.PARTY_ENTERED, state)
            .stoppedTo as ScriptStop.AskThePlayer

        val asked = runner.answer(ask.resumeAt, state, DialogAnswer(3))
            .stoppedTo as ScriptStop.AskThePlayer
        assertEquals(
            listOf("""Alex: "may we rest a moment in your temple?""""),
            asked.said.mapNotNull { level.message(it) }.map { party.fillIn(it).trim() },
        )

        val replied = runner.answer(asked.resumeAt, state, DialogAnswer(3))
            .stoppedTo as ScriptStop.AskThePlayer
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

    private fun load(name: String): Inf = runBlocking {
        val pal = PalRepositoryImpl(resources)
        val items = ItemsRepositoryImpl(resources).loadItems().getOrThrow()
        InfRepositoryImpl(
            resourceRepository = resources,
            mazRepository = MazRepositoryImpl(resources),
            vmpRepository = VmpRepositoryImpl(resources),
            vcnRepository = VcnRepositoryImpl(resources),
            palRepository = pal,
            cpsRepository = CpsRepositoryImpl(resources),
            decRepository = DecRepositoryImpl(resources),
        ).loadInf(name, items).getOrThrow()
    }
}
