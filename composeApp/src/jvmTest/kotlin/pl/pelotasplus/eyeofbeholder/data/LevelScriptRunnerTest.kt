package pl.pelotasplus.eyeofbeholder.data

import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.GameState
import pl.pelotasplus.eyeofbeholder.data.model.MessageId
import pl.pelotasplus.eyeofbeholder.data.model.MonsterInstance
import pl.pelotasplus.eyeofbeholder.data.model.MonsterTypeId
import pl.pelotasplus.eyeofbeholder.data.model.LevelScriptRunner
import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.PartyState
import pl.pelotasplus.eyeofbeholder.data.model.ScriptEvent
import pl.pelotasplus.eyeofbeholder.data.model.ScriptRun
import pl.pelotasplus.eyeofbeholder.data.model.ScriptStop
import pl.pelotasplus.eyeofbeholder.data.model.Trigger
import pl.pelotasplus.eyeofbeholder.data.model.TriggerFlags
import pl.pelotasplus.eyeofbeholder.data.model.script.Conditional
import pl.pelotasplus.eyeofbeholder.data.model.script.CreateMonster
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
    fun `a trigger on another square is ignored`() {
        val runner = LevelScriptRunner(listOf(Script(ScriptOffset(0), changeLevelToken(5))))
        val trigger = Trigger(Location(9, 9), TriggerFlags(0x08), Script(ScriptOffset(0), changeLevelToken(5)))

        assertEquals(
            null,
            runner.onEvent(listOf(trigger), ScriptEvent.PARTY_ENTERED, party()).stoppedTo
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
        assertEquals(null, run.stoppedTo)
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
        assertTrue(run.stoppedTo is ScriptStop.ChangeLevel)
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
        assertTrue(outcome is ScriptStop.ChangeLevel)
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

    private fun changeToLevel(level: Int) = ScriptStop.ChangeLevel(
        level = level,
        subLevel = 0,
        location = Location(14, 9),
        direction = Direction.WEST,
    )

    /** What the script stopped for, which is what most of these tests are about. */
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
    ): ScriptStop? = runFully(*script, facing = facing).stoppedTo

    private fun runFully(
        vararg script: Pair<Int, ScriptToken>,
        facing: Direction = Direction.NORTH,
        world: GameState = party(facing),
    ): ScriptRun {
        val instructions = script.map { (offset, token) -> Script(ScriptOffset(offset), token) }
        val runner = LevelScriptRunner(instructions)
        val trigger = Trigger(here, TriggerFlags(0x08), instructions.first())
        return runner.onEvent(listOf(trigger), ScriptEvent.PARTY_ENTERED, world)
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

    private fun fire(flags: Int, event: ScriptEvent): ScriptStop? {
        val instruction = Script(ScriptOffset(0), changeLevelToken(5))
        val runner = LevelScriptRunner(listOf(instruction))
        return runner
            .onEvent(listOf(Trigger(here, TriggerFlags(flags), instruction)), event, party())
            .stoppedTo
    }
}
