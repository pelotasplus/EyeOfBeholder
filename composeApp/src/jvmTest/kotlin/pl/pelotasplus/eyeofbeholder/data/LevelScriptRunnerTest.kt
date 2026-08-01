package pl.pelotasplus.eyeofbeholder.data

import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.LevelScriptRunner
import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.PartyState
import pl.pelotasplus.eyeofbeholder.data.model.ScriptEvent
import pl.pelotasplus.eyeofbeholder.data.model.ScriptOutcome
import pl.pelotasplus.eyeofbeholder.data.model.Trigger
import pl.pelotasplus.eyeofbeholder.data.model.TriggerFlags
import pl.pelotasplus.eyeofbeholder.data.model.script.Conditional
import pl.pelotasplus.eyeofbeholder.data.model.script.End
import pl.pelotasplus.eyeofbeholder.data.model.script.Eval
import pl.pelotasplus.eyeofbeholder.data.model.script.Goto
import pl.pelotasplus.eyeofbeholder.data.model.script.Message
import pl.pelotasplus.eyeofbeholder.data.model.script.NewLevelOrMonster
import pl.pelotasplus.eyeofbeholder.data.model.script.Script
import pl.pelotasplus.eyeofbeholder.data.model.script.ScriptToken
import pl.pelotasplus.eyeofbeholder.data.model.script.Teleport
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
        PartyState(position = here, facing = facing)

    // --- which triggers fire -------------------------------------------------

    @Test
    fun `flag bit 3 means the script runs when the party enters`() {
        assertEquals(changeToLevel(5), fire(flags = 0x08, event = ScriptEvent.PARTY_ENTERED))
    }

    @Test
    fun `a trigger for entering does not fire when the party leaves`() {
        assertEquals(ScriptOutcome.Nothing, fire(flags = 0x08, event = ScriptEvent.PARTY_LEFT))
    }

    @Test
    fun `flag bit 4 means the script runs when the party leaves`() {
        assertEquals(changeToLevel(5), fire(flags = 0x10, event = ScriptEvent.PARTY_LEFT))
        assertEquals(ScriptOutcome.Nothing, fire(flags = 0x10, event = ScriptEvent.PARTY_ENTERED))
    }

    @Test
    fun `flags of zero react to neither`() {
        assertEquals(ScriptOutcome.Nothing, fire(flags = 0x00, event = ScriptEvent.PARTY_ENTERED))
        assertEquals(ScriptOutcome.Nothing, fire(flags = 0x00, event = ScriptEvent.PARTY_LEFT))
    }

    @Test
    fun `a trigger on another square is ignored`() {
        val runner = LevelScriptRunner(listOf(Script(0, changeLevelToken(5))))
        val trigger = Trigger(Location(9, 9), TriggerFlags(0x08), Script(0, changeLevelToken(5)))

        assertEquals(
            ScriptOutcome.Nothing,
            runner.onEvent(listOf(trigger), ScriptEvent.PARTY_ENTERED, party())
        )
    }

    // --- control flow --------------------------------------------------------

    @Test
    fun `a true condition falls through to the next instruction`() {
        // oeob_eval: true continues, false jumps
        val outcome = run(
            0 to Eval(listOf(Conditional.ImmediateShort(1)), goto = 20),
            10 to changeLevelToken(5),
            20 to changeLevelToken(9),
        )
        assertEquals(changeToLevel(5), outcome)
    }

    @Test
    fun `a false condition jumps to the else offset`() {
        val outcome = run(
            0 to Eval(listOf(Conditional.ImmediateShort(0)), goto = 20),
            10 to changeLevelToken(5),
            20 to changeLevelToken(9),
        )
        assertEquals(changeToLevel(9), outcome)
    }

    @Test
    fun `conditions this project cannot answer yet are taken as true`() {
        val outcome = run(
            0 to Eval(listOf(Conditional.GetTriggerFlag), goto = 20),
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
                goto = 20,
            ),
            10 to changeLevelToken(6),
            20 to Teleport.MoveParty(Location(0, 0), Location(9, 9)),
        )

        assertEquals(changeToLevel(6), run(*script, facing = Direction.NORTH))
        assertEquals(
            ScriptOutcome.MoveParty(Location(9, 9)),
            run(*script, facing = Direction.SOUTH),
        )
    }

    @Test
    fun `goto jumps to the target offset`() {
        val outcome = run(
            0 to Goto(30),
            10 to changeLevelToken(5),
            30 to changeLevelToken(9),
        )
        assertEquals(changeToLevel(9), outcome)
    }

    @Test
    fun `end stops the script`() {
        assertEquals(ScriptOutcome.Nothing, run(0 to End, 10 to changeLevelToken(5)))
    }

    @Test
    fun `instructions that are not modelled are skipped`() {
        val outcome = run(
            0 to Message(messageId = 1, color = 0),
            10 to changeLevelToken(5),
        )
        assertEquals(changeToLevel(5), outcome)
    }

    @Test
    fun `move party reports the destination`() {
        val outcome = run(0 to Teleport.MoveParty(Location(0, 0), Location(7, 8)))
        assertEquals(ScriptOutcome.MoveParty(Location(7, 8)), outcome)
    }

    @Test
    fun `moving the party does not stop the script`() {
        // the level 5 stairs step the party onto the staircase and only then
        // change level; oeob_movePartyOrObject restores _abortScript so that
        // the script survives the move
        val outcome = run(
            0 to Teleport.MoveParty(Location(0, 0), Location(10, 6)),
            10 to changeLevelToken(6),
            20 to End,
        )
        assertEquals(changeToLevel(6), outcome)
    }

    @Test
    fun `the last move wins when no level change follows`() {
        val outcome = run(
            0 to Teleport.MoveParty(Location(0, 0), Location(1, 1)),
            10 to Teleport.MoveParty(Location(0, 0), Location(2, 2)),
            20 to End,
        )
        assertEquals(ScriptOutcome.MoveParty(Location(2, 2)), outcome)
    }

    @Test
    fun `a move survives a jump to a missing offset`() {
        val outcome = run(
            0 to Teleport.MoveParty(Location(0, 0), Location(4, 4)),
            10 to Goto(999),
        )
        assertEquals(ScriptOutcome.MoveParty(Location(4, 4)), outcome)
    }

    @Test
    fun `a script that loops for ever gives up instead of hanging`() {
        assertEquals(ScriptOutcome.Nothing, run(0 to Goto(0)))
    }

    @Test
    fun `a jump to a missing offset stops the script`() {
        assertEquals(ScriptOutcome.Nothing, run(0 to Goto(999)))
    }

    @Test
    fun `running off the end of the script stops`() {
        assertEquals(ScriptOutcome.Nothing, run(0 to Message(messageId = 1, color = 0)))
    }

    @Test
    fun `the direction from the script is carried through`() {
        val outcome = run(0 to changeLevelToken(5, Direction.WEST))
        assertTrue(outcome is ScriptOutcome.ChangeLevel)
        assertEquals(Direction.WEST, outcome.direction)
    }

    // --- helpers -------------------------------------------------------------

    private fun changeLevelToken(level: Int, direction: Direction? = Direction.WEST) =
        NewLevelOrMonster.ChangeLevel(
            level = level,
            subLevel = 0,
            location = Location(14, 9),
            direction = direction,
        )

    private fun changeToLevel(level: Int) = ScriptOutcome.ChangeLevel(
        level = level,
        subLevel = 0,
        location = Location(14, 9),
        direction = Direction.WEST,
    )

    private fun run(
        vararg script: Pair<Int, ScriptToken>,
        facing: Direction = Direction.NORTH,
    ): ScriptOutcome {
        val instructions = script.map { (offset, token) -> Script(offset, token) }
        val runner = LevelScriptRunner(instructions)
        val trigger = Trigger(here, TriggerFlags(0x08), instructions.first())
        return runner.onEvent(listOf(trigger), ScriptEvent.PARTY_ENTERED, party(facing))
    }

    private fun fire(flags: Int, event: ScriptEvent): ScriptOutcome {
        val instruction = Script(0, changeLevelToken(5))
        val runner = LevelScriptRunner(listOf(instruction))
        return runner.onEvent(listOf(Trigger(here, TriggerFlags(flags), instruction)), event, party())
    }
}
