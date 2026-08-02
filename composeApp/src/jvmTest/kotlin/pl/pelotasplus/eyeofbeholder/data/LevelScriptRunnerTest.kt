package pl.pelotasplus.eyeofbeholder.data

import pl.pelotasplus.eyeofbeholder.data.model.DialogAnswer
import pl.pelotasplus.eyeofbeholder.data.model.DialogueTextId
import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.GameState
import pl.pelotasplus.eyeofbeholder.data.model.MessageId
import pl.pelotasplus.eyeofbeholder.data.model.MonsterInstance
import pl.pelotasplus.eyeofbeholder.data.model.MonsterTypeId
import pl.pelotasplus.eyeofbeholder.data.model.LevelScriptRunner
import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.PartyState
import pl.pelotasplus.eyeofbeholder.data.model.ScriptEvent
import pl.pelotasplus.eyeofbeholder.data.model.ChangeLevel
import pl.pelotasplus.eyeofbeholder.data.model.ScriptRun
import pl.pelotasplus.eyeofbeholder.data.model.ScriptStage
import pl.pelotasplus.eyeofbeholder.data.model.Ticks
import pl.pelotasplus.eyeofbeholder.data.model.Trigger
import pl.pelotasplus.eyeofbeholder.data.model.TriggerFlags
import pl.pelotasplus.eyeofbeholder.data.model.script.Conditional
import pl.pelotasplus.eyeofbeholder.data.model.script.CreateMonster
import pl.pelotasplus.eyeofbeholder.data.model.script.Dialog
import pl.pelotasplus.eyeofbeholder.data.model.script.GoSub
import pl.pelotasplus.eyeofbeholder.data.model.script.Return
import pl.pelotasplus.eyeofbeholder.data.model.script.End
import pl.pelotasplus.eyeofbeholder.data.model.script.Eval
import pl.pelotasplus.eyeofbeholder.data.model.script.Goto
import pl.pelotasplus.eyeofbeholder.data.model.script.Message
import pl.pelotasplus.eyeofbeholder.data.model.script.NewLevelOrMonster
import pl.pelotasplus.eyeofbeholder.data.model.script.Script
import pl.pelotasplus.eyeofbeholder.data.model.script.ScriptOffset
import pl.pelotasplus.eyeofbeholder.data.model.script.ScriptToken
import pl.pelotasplus.eyeofbeholder.data.model.script.SetWall
import pl.pelotasplus.eyeofbeholder.data.model.script.Teleport
import pl.pelotasplus.eyeofbeholder.data.model.script.UpdateScreen
import pl.pelotasplus.eyeofbeholder.data.model.script.Wait
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The interpreter's control flow, driven by hand-built scripts so each rule is
 * pinned independently of what any shipped level happens to contain.
 */
class LevelScriptRunnerTest {

    private val here = Location(3, 4)

    private fun party(facing: Direction = Direction.NORTH) =
        GameState(PartyState(position = here, facing = facing))

    // --- which triggers fire -------------------------------------------------

    @Test
    fun `flag bit 3 means the script runs when the party enters`() {
        assertEquals(changeToLevel(5), fire(flags = 0x08, event = ScriptEvent.PARTY_ENTERED))
    }

    @Test
    fun `a trigger for entering does not fire when the party leaves`() {
        assertEquals(null,fire(flags = 0x08, event = ScriptEvent.PARTY_LEFT))
    }

    @Test
    fun `flag bit 4 means the script runs when the party leaves`() {
        assertEquals(changeToLevel(5), fire(flags = 0x10, event = ScriptEvent.PARTY_LEFT))
        assertEquals(null,fire(flags = 0x10, event = ScriptEvent.PARTY_ENTERED))
    }

    @Test
    fun `flags of zero react to neither`() {
        assertEquals(null,fire(flags = 0x00, event = ScriptEvent.PARTY_ENTERED))
        assertEquals(null,fire(flags = 0x00, event = ScriptEvent.PARTY_LEFT))
    }

    @Test
    fun `a trigger on another square is ignored`() = runBlocking {
        val runner = LevelScriptRunner(listOf(Script(ScriptOffset(0), changeLevelToken(5))))
        val trigger = Trigger(Location(9, 9), TriggerFlags(0x08), Script(ScriptOffset(0), changeLevelToken(5)))

        assertEquals(
            null,
            runner.onEvent(listOf(trigger), ScriptEvent.PARTY_ENTERED, party()).changeLevel
        )
    }

    // --- control flow --------------------------------------------------------

    @Test
    fun `a true condition falls through to the next instruction`() {
        // oeob_eval: true continues, false jumps
        val outcome = run(
            0 to Eval(listOf(Conditional.ImmediateShort(1)), goto = ScriptOffset(20)),
            10 to changeLevelToken(5),
            20 to changeLevelToken(9),
        )
        assertEquals(changeToLevel(5), outcome)
    }

    @Test
    fun `a false condition jumps to the else offset`() {
        val outcome = run(
            0 to Eval(listOf(Conditional.ImmediateShort(0)), goto = ScriptOffset(20)),
            10 to changeLevelToken(5),
            20 to changeLevelToken(9),
        )
        assertEquals(changeToLevel(9), outcome)
    }

    @Test
    fun `conditions this project cannot answer yet are taken as true`() {
        val outcome = run(
            0 to Eval(listOf(Conditional.GetTriggerFlag), goto = ScriptOffset(20)),
            10 to changeLevelToken(5),
            20 to changeLevelToken(9),
        )
        assertEquals(changeToLevel(5), outcome)
    }

    @Test
    fun `the party's facing answers GetPartyDirection`() {
        // the level 5 stairs only let the party through facing the right way,
        // and north is 0
        val script = arrayOf(
            0 to Eval(
                listOf(Conditional.GetPartyDirection, Conditional.ImmediateShort(0), Conditional.Equals),
                goto = ScriptOffset(20),
            ),
            10 to changeLevelToken(6),
            20 to Teleport.MoveParty(Location(0, 0), Location(9, 9)),
        )

        assertEquals(changeToLevel(6), run(*script, facing = Direction.NORTH))
        assertEquals(
            Location(9, 9),
            runFully(*script, facing = Direction.SOUTH).state.party.position,
        )
    }

    @Test
    fun `goto jumps to the target offset`() {
        val outcome = run(
            0 to Goto(ScriptOffset(30)),
            10 to changeLevelToken(5),
            30 to changeLevelToken(9),
        )
        assertEquals(changeToLevel(9), outcome)
    }

    @Test
    fun `end stops the script`() {
        assertEquals(null,run(0 to End, 10 to changeLevelToken(5)))
    }

    @Test
    fun `instructions that are not modelled are skipped`() {
        val outcome = run(
            0 to Message(messageId = MessageId(1), color = 0),
            10 to changeLevelToken(5),
        )
        assertEquals(changeToLevel(5), outcome)
    }

    @Test
    fun `move party puts the party on the destination`() {
        val run = runFully(0 to Teleport.MoveParty(Location(0, 0), Location(7, 8)))
        assertEquals(Location(7, 8), run.state.party.position)
        assertEquals(null, run.changeLevel)
    }

    @Test
    fun `moving the party does not stop the script`() {
        // the level 5 stairs step the party onto the staircase and only then
        // change level, so the engine goes out of its way to keep running
        // after a move
        val outcome = run(
            0 to Teleport.MoveParty(Location(0, 0), Location(10, 6)),
            10 to changeLevelToken(6),
            20 to End,
        )
        assertEquals(changeToLevel(6), outcome)
    }

    @Test
    fun `the last move wins when no level change follows`() {
        val run = runFully(
            0 to Teleport.MoveParty(Location(0, 0), Location(1, 1)),
            10 to Teleport.MoveParty(Location(0, 0), Location(2, 2)),
            20 to End,
        )
        assertEquals(Location(2, 2), run.state.party.position)
    }

    @Test
    fun `a move survives a jump to a missing offset`() {
        val run = runFully(
            0 to Teleport.MoveParty(Location(0, 0), Location(4, 4)),
            10 to Goto(ScriptOffset(999)),
        )
        assertEquals(Location(4, 4), run.state.party.position)
    }

    @Test
    fun `the script turns the party where it says to`() {
        val run = runFully(
            0 to SetWall.ChangePartyDirection(Direction.EAST),
            10 to End,
            facing = Direction.NORTH,
        )
        assertEquals(Direction.EAST, run.state.party.facing)
    }

    /** The clerics are addressed face to face, before they are drawn. */
    @Test
    fun `a turn survives the script stopping to speak`() {
        val run = runFully(
            0 to SetWall.ChangePartyDirection(Direction.SOUTH),
            10 to changeLevelToken(6),
            facing = Direction.NORTH,
        )
        assertEquals(Direction.SOUTH, run.state.party.facing)
        assertEquals(changeToLevel(6), run.changeLevel)
    }

    @Test
    fun `a script that loops for ever gives up instead of hanging`() {
        assertEquals(null,run(0 to Goto(ScriptOffset(0))))
    }

    @Test
    fun `a jump to a missing offset stops the script`() {
        assertEquals(null,run(0 to Goto(ScriptOffset(999))))
    }

    @Test
    fun `running off the end of the script stops`() {
        assertEquals(null,run(0 to Message(messageId = MessageId(1), color = 0)))
    }

    @Test
    fun `the direction from the script is carried through`() {
        val outcome = run(0 to changeLevelToken(5, Direction.WEST))
        assertEquals(Direction.WEST, outcome?.direction)
    }

    // --- helpers -------------------------------------------------------------

    private fun changeLevelToken(level: Int, direction: Direction? = Direction.WEST) =
        NewLevelOrMonster.ChangeLevel(
            level = level,
            subLevel = 0,
            location = Location(14, 9),
            direction = direction,
        )

    private fun changeToLevel(level: Int) = ChangeLevel(
        level = level,
        subLevel = 0,
        location = Location(14, 9),
        direction = Direction.WEST,
    )

    /** What the script stopped for, which is what most of these tests are about. */
    // --- calling subroutines -------------------------------------------------

    @Test
    fun `a GoSub runs the subroutine and comes back to the call`() {
        val outcome = run(
            0 to GoSub(ScriptOffset(100)),
            10 to changeLevelToken(5),
            100 to Return,
            110 to changeLevelToken(9),
        )
        assertEquals(changeToLevel(5), outcome)
    }

    @Test
    fun `a Return with nothing to return to ends the script`() {
        val outcome = run(
            0 to Return,
            10 to changeLevelToken(5),
        )
        assertEquals(null, outcome)
    }

    @Test
    fun `a call the ten deep stack cannot hold is skipped rather than taken`() {
        // ten calls chained one into the next fill the stack, so the eleventh
        // is dropped and the script carries on past it
        val chain = (0 until 10).map { it * 10 to GoSub(ScriptOffset((it + 1) * 10)) }

        val outcome = run(
            *chain.toTypedArray(),
            100 to GoSub(ScriptOffset(200)),
            110 to changeLevelToken(5),
            200 to changeLevelToken(9),
        )
        assertEquals(changeToLevel(5), outcome)
    }

    @Test
    fun `a subroutine that asks still returns once the player has answered`() {
        // the calls a script is inside outlive the question it stopped at,
        // because they are the interpreter's own and never leave it
        val outcome = run(
            0 to GoSub(ScriptOffset(100)),
            10 to changeLevelToken(5),
            100 to askSomething(),
            110 to Return,
        )
        assertEquals(changeToLevel(5), outcome)
    }

    // --- showing the work ----------------------------------------------------

    @Test
    fun `a wait holds the screen for as many ticks as the script asks`() {
        val stage = RecordingStage()

        runFully(
            0 to Wait(15),
            10 to Wait(40),
            20 to End,
            stage = stage,
        )

        assertEquals(listOf(Ticks(15), Ticks(40)), stage.holds)
    }

    @Test
    fun `the party is shown where the script has moved it to`() {
        val stage = RecordingStage()

        runFully(
            0 to Teleport.MoveParty(Location(0, 0), Location(7, 8)),
            10 to UpdateScreen,
            20 to End,
            stage = stage,
        )

        assertEquals(listOf(Location(7, 8)), stage.shown.map { it.party.position })
    }

    @Test
    fun `an escort draws every step it takes, in order`() {
        val stage = RecordingStage()

        runFully(
            0 to Teleport.MoveParty(Location(0, 0), Location(15, 14)),
            10 to UpdateScreen,
            20 to Wait(15),
            30 to Teleport.MoveParty(Location(0, 0), Location(15, 13)),
            40 to UpdateScreen,
            50 to Wait(15),
            60 to End,
            stage = stage,
        )

        assertEquals(
            listOf(
                RecordingStage.Beat.Shown(party().partyMovedTo(Location(15, 14))),
                RecordingStage.Beat.Held(Ticks(15)),
                RecordingStage.Beat.Shown(party().partyMovedTo(Location(15, 13))),
                RecordingStage.Beat.Held(Ticks(15)),
            ),
            stage.beats,
        )
    }

    @Test
    fun `a speech acknowledged does not become the answer the script tests`() {
        // clicking "ok" on a reply hands back the first button, which must not
        // stand in for the answer the branch was chosen with
        val stage = RecordingStage(answers = listOf(2))

        val outcome = runFully(
            0 to askSomething(),
            10 to Dialog.DialogText(DialogueTextId(2), MessageId(4)),
            20 to Eval(
                listOf(
                    Conditional.DialogResult,
                    Conditional.ImmediateShort(2),
                    Conditional.Equals,
                ),
                goto = ScriptOffset(40),
            ),
            30 to changeLevelToken(5),
            40 to changeLevelToken(9),
            stage = stage,
        ).changeLevel

        assertEquals(changeToLevel(5), outcome)
    }

    @Test
    fun `what the script wrote into the box goes up with the question`() {
        val stage = RecordingStage()

        runFully(
            0 to Message(MessageId(7), color = 0),
            10 to askSomething(),
            20 to End,
            stage = stage,
        )

        assertEquals(listOf(MessageId(7)), stage.questions.single().said)
    }

    private fun askSomething() = Dialog.RunDialog(
        DialogueTextId(1),
        MessageId(0),
        MessageId(1),
        MessageId(2),
    )

    // --- arriving somewhere a script put you ---------------------------------

    @Test
    fun `a script that moves the party runs what is on the square they land on`() = runBlocking {
        val there = Location(9, 9)
        val instructions = listOf(
            Script(ScriptOffset(0), Teleport.MoveParty(Location(0, 0), there)),
            Script(ScriptOffset(10), End),
            Script(ScriptOffset(100), changeLevelToken(5)),
        )
        val runner = LevelScriptRunner(instructions)
        val triggers = listOf(
            Trigger(here, TriggerFlags(0x08), instructions[0]),
            Trigger(there, TriggerFlags(0x08), instructions[2]),
        )

        assertEquals(
            changeToLevel(5),
            runner.onEvent(triggers, ScriptEvent.PARTY_ENTERED, party()).changeLevel,
        )
    }

    @Test
    fun `squares that push the party at each other give up rather than recur for ever`() =
        runBlocking {
            val there = Location(9, 9)
            val instructions = listOf(
                Script(ScriptOffset(0), Teleport.MoveParty(Location(0, 0), there)),
                Script(ScriptOffset(10), End),
                Script(ScriptOffset(100), Teleport.MoveParty(Location(0, 0), here)),
                Script(ScriptOffset(110), End),
            )
            val runner = LevelScriptRunner(instructions)
            val triggers = listOf(
                Trigger(here, TriggerFlags(0x08), instructions[0]),
                Trigger(there, TriggerFlags(0x08), instructions[2]),
            )

            val run = runner.onEvent(triggers, ScriptEvent.PARTY_ENTERED, party())

            assertTrue(
                run.state.party.position in listOf(here, there),
                "it should stop somewhere, and it stopped at ${run.state.party.position}",
            )
        }

    // --- what a script leaves behind ----------------------------------------

    @Test
    fun `a spawn puts the monster it asks for into the world`() {
        val spawned = runFully(0 to spawnAt(Location(5, 6))).state.monsters.single()

        assertEquals(5, spawned.x)
        assertEquals(6, spawned.y)
        assertEquals(Direction.EAST, spawned.direction)
    }

    @Test
    fun `a spawn takes the lowest free slot, which is what decides its colors`() {
        val alreadyThere = MonsterInstance.spawnedBy(spawnAt(Location(1, 1)), slot = 0)

        val world = runFully(
            0 to spawnAt(Location(5, 6)),
            world = party().copy(monsters = listOf(alreadyThere)),
        ).state

        assertEquals(listOf(0, 1), world.monsters.map { it.index })
    }

    @Test
    fun `nothing is conjured onto the square the party stands on`() {
        assertTrue(runFully(0 to spawnAt(here)).state.monsters.isEmpty())
    }

    private fun run(
        vararg script: Pair<Int, ScriptToken>,
        facing: Direction = Direction.NORTH,
    ): ChangeLevel? = runFully(*script, facing = facing).changeLevel

    private fun runFully(
        vararg script: Pair<Int, ScriptToken>,
        facing: Direction = Direction.NORTH,
        world: GameState = party(facing),
        stage: ScriptStage = ScriptStage.silent(),
    ): ScriptRun = runBlocking {
        val instructions = script.map { (offset, token) -> Script(ScriptOffset(offset), token) }
        val runner = LevelScriptRunner(instructions)
        val trigger = Trigger(here, TriggerFlags(0x08), instructions.first())
        runner.onEvent(listOf(trigger), ScriptEvent.PARTY_ENTERED, world, stage)
    }

    private fun spawnAt(location: Location) = CreateMonster(
        unit = 0,
        location = location,
        pos = 4,
        direction = Direction.EAST,
        type = MonsterTypeId(0),
        gfxIndex = 0,
        mode = 0,
        pause = 0,
        weapon = 0,
        pocketItem = 0,
    )

    private fun fire(flags: Int, event: ScriptEvent): ChangeLevel? = runBlocking {
        val instruction = Script(ScriptOffset(0), changeLevelToken(5))
        val runner = LevelScriptRunner(listOf(instruction))
        runner
            .onEvent(listOf(Trigger(here, TriggerFlags(flags), instruction)), event, party())
            .changeLevel
    }
}
