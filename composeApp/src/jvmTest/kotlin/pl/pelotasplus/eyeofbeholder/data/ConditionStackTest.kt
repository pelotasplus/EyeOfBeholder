package pl.pelotasplus.eyeofbeholder.data

import kotlinx.coroutines.runBlocking
import pl.pelotasplus.eyeofbeholder.data.model.CharacterClass
import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.DialogueTextId
import pl.pelotasplus.eyeofbeholder.data.model.FlagBit
import pl.pelotasplus.eyeofbeholder.data.model.GameState
import pl.pelotasplus.eyeofbeholder.data.model.Item
import pl.pelotasplus.eyeofbeholder.data.model.ItemIconId
import pl.pelotasplus.eyeofbeholder.data.model.ItemIndex
import pl.pelotasplus.eyeofbeholder.data.model.ItemNameId
import pl.pelotasplus.eyeofbeholder.data.model.ItemTypeId
import pl.pelotasplus.eyeofbeholder.data.model.LevelScriptRunner
import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.MessageId
import pl.pelotasplus.eyeofbeholder.data.model.MonsterInstance
import pl.pelotasplus.eyeofbeholder.data.model.MonsterTypeId
import pl.pelotasplus.eyeofbeholder.data.model.PartyState
import pl.pelotasplus.eyeofbeholder.data.model.Race
import pl.pelotasplus.eyeofbeholder.data.model.ScriptEvent
import pl.pelotasplus.eyeofbeholder.data.model.SquarePlace
import pl.pelotasplus.eyeofbeholder.data.model.Trigger
import pl.pelotasplus.eyeofbeholder.data.model.TriggerFlags
import pl.pelotasplus.eyeofbeholder.data.model.WallByte
import pl.pelotasplus.eyeofbeholder.data.model.WallSide
import pl.pelotasplus.eyeofbeholder.data.model.script.Conditional
import pl.pelotasplus.eyeofbeholder.data.model.script.Dialog
import pl.pelotasplus.eyeofbeholder.data.model.script.End
import pl.pelotasplus.eyeofbeholder.data.model.script.Eval
import pl.pelotasplus.eyeofbeholder.data.model.script.Message
import pl.pelotasplus.eyeofbeholder.data.model.script.Script
import pl.pelotasplus.eyeofbeholder.data.model.script.ScriptOffset
import pl.pelotasplus.eyeofbeholder.data.repository.OriginalSaveRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.ResourceRepositoryImpl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The stack a condition is worked out on, which every branch in the game is
 * decided by.
 *
 * Written against hand-built conditions rather than against a level, because a
 * level exercises the stack only where it happens to — and a level that goes
 * wrong looks like a level that has nothing to say, which is why two of these
 * rules were wrong for as long as they were.
 *
 * The rules are the engine's: an operator takes both of its operands off the
 * stack whatever the first of them turns out to be, and the operand written
 * last is the left-hand side, so `x y less` asks whether y is less than x.
 */
class ConditionStackTest {

    private val here = Location(3, 4)

    private val standing = GameState(PartyState(here, Direction.NORTH))

    /** True where the condition held and the script carried on past it. */
    private fun holds(
        tokens: List<Conditional>,
        world: GameState = standing,
        event: ScriptEvent = ScriptEvent.PARTY_ENTERED,
    ): Boolean = whatHappened(
        instructions = listOf(
            Script(ScriptOffset(0), Eval(tokens, goto = ScriptOffset(20))),
            Script(ScriptOffset(10), Message(CARRIED_ON, color = 15)),
            Script(ScriptOffset(20), End),
        ),
        world = world,
        event = event,
    )

    private fun whatHappened(
        instructions: List<Script>,
        world: GameState,
        event: ScriptEvent = ScriptEvent.PARTY_ENTERED,
        answering: List<Int> = emptyList(),
    ): Boolean {
        val stage = RecordingStage(answers = answering)

        runBlocking {
            LevelScriptRunner(instructions).onEvent(
                triggers = listOf(Trigger(here, TriggerFlags(0x08), instructions.first())),
                event = event,
                state = world,
                stage = stage,
                at = here,
            )
        }

        return stage.beats
            .filterIsInstance<RecordingStage.Beat.Said>()
            .any { CARRIED_ON in it.speech.said }
    }

    private fun number(value: Int) = Conditional.ImmediateShort(value)

    private val yes = listOf(number(1), number(1), Conditional.Equals)
    private val no = listOf(number(0), number(1), Conditional.Equals)

    // --- the operators -------------------------------------------------------

    @Test
    fun `and is both of its operands`() {
        assertTrue(holds(yes + yes + Conditional.And))
        assertFalse(holds(yes + no + Conditional.And))
        assertFalse(holds(no + yes + Conditional.And))
        assertFalse(holds(no + no + Conditional.And))
    }

    @Test
    fun `or is either of its operands`() {
        assertTrue(holds(yes + yes + Conditional.Or))
        assertTrue(holds(yes + no + Conditional.Or))
        assertTrue(holds(no + yes + Conditional.Or))
        assertFalse(holds(no + no + Conditional.Or))
    }

    /**
     * And the operand an operator did not need is taken off all the same.
     *
     * This is the shape every plate in the game is written in — one clause for
     * the party, another for what has been set down, joined by an or. An
     * operator that stops at its first false leaves the second where it was,
     * and every operator after it reads one value along: this condition then
     * comes out false, and the door it opens never opens.
     */
    @Test
    fun `an operator that need not look at its second operand still takes it`() {
        val plateShaped = yes + yes + Conditional.And +
            no + no + Conditional.And +
            no + Conditional.And +
            Conditional.Or

        assertTrue(holds(plateShaped), "the stack was left one value out")
    }

    // --- the comparisons -----------------------------------------------------

    /**
     * The operand written last is the left-hand side. Reading it the other way
     * round is not a crash but a level quietly doing the opposite of what it
     * says, so each of the six is pinned both ways about.
     */
    @Test
    fun `a comparison reads the last operand as its left-hand side`() {
        assertTrue(holds(listOf(number(3), number(5), Conditional.MoreThan)), "5 > 3")
        assertFalse(holds(listOf(number(5), number(3), Conditional.MoreThan)), "3 > 5")

        assertTrue(holds(listOf(number(5), number(3), Conditional.LessThan)), "3 < 5")
        assertFalse(holds(listOf(number(3), number(5), Conditional.LessThan)), "5 < 3")
    }

    @Test
    fun `the comparisons that take equal values`() {
        assertTrue(holds(listOf(number(4), number(4), Conditional.MoreEqualsThan)))
        assertTrue(holds(listOf(number(3), number(4), Conditional.MoreEqualsThan)))
        assertFalse(holds(listOf(number(4), number(3), Conditional.MoreEqualsThan)))

        assertTrue(holds(listOf(number(4), number(4), Conditional.LessEqualsThan)))
        assertTrue(holds(listOf(number(4), number(3), Conditional.LessEqualsThan)))
        assertFalse(holds(listOf(number(3), number(4), Conditional.LessEqualsThan)))
    }

    @Test
    fun `equal and unequal`() {
        assertTrue(holds(listOf(number(7), number(7), Conditional.Equals)))
        assertFalse(holds(listOf(number(7), number(8), Conditional.Equals)))

        assertTrue(holds(listOf(number(7), number(8), Conditional.NotEquals)))
        assertFalse(holds(listOf(number(7), number(7), Conditional.NotEquals)))
    }

    /** A number on its own is the answer, no comparison needed. Zero is false. */
    @Test
    fun `a number stands for itself`() {
        assertTrue(holds(listOf(number(1))))
        assertTrue(holds(listOf(number(9))))
        assertFalse(holds(listOf(number(0))))
    }

    // --- what a condition can ask about ---------------------------------------

    /**
     * Which of the things a square reacts to has just happened. One script
     * serves them all, so this is the first thing most of them ask.
     */
    @Test
    fun `the event that fired the trigger`() {
        val steppedOn = listOf(
            Conditional.GetTriggerFlag,
            number(ScriptEvent.PARTY_ENTERED.mask),
            Conditional.Equals,
        )

        assertTrue(holds(steppedOn, event = ScriptEvent.PARTY_ENTERED))
        assertFalse(holds(steppedOn, event = ScriptEvent.ITEM_PUT_DOWN))
    }

    @Test
    fun `a wall is read as the byte a script would write`() {
        val world = standing.wallChanged(0, here, WallSide.NORTH, WallByte(7))

        val isSeven = listOf(
            Conditional.GetWallSide(wallIndex = WallSide.NORTH.ordinal, location = here),
            number(7),
            Conditional.Equals,
        )

        assertTrue(holds(isSeven, world))
        assertFalse(holds(isSeven, standing), "an unchanged wall is not that byte")
    }

    @Test
    fun `the flags a level keeps and the flags that outlive it`() {
        val bit = FlagBit(3)
        val levelFlag = listOf(Conditional.GetLevelFlag(bit))
        val globalFlag = listOf(Conditional.GetGlobalFlag(bit))

        assertFalse(holds(levelFlag))
        assertTrue(holds(levelFlag, standing.levelFlagSet(0, bit)))

        assertFalse(holds(globalFlag))
        assertTrue(holds(globalFlag, standing.globalFlagSet(bit)))

        assertFalse(
            holds(globalFlag, standing.levelFlagSet(0, bit)),
            "a level's own flag is not the one that outlives it",
        )
    }

    /** Which way the party face, counted the way [Direction] is. */
    @Test
    fun `which way the party are looking`() {
        val facingEast = listOf(
            Conditional.GetPartyDirection,
            number(Direction.EAST.ordinal),
            Conditional.Equals,
        )

        assertTrue(holds(facingEast, standing.partyTurnedTo(Direction.EAST)))
        assertFalse(holds(facingEast, standing.partyTurnedTo(Direction.WEST)))
    }

    /**
     * How many monsters stand on a square, which is what a script asks before
     * letting a speaker talk over the top of a fight.
     */
    @Test
    fun `how many monsters stand on a square`() {
        val oneThere = listOf(
            Conditional.IsMonsterAtLocation.BlockFlags(here),
            number(1),
            Conditional.Equals,
        )

        assertFalse(holds(oneThere))
        assertTrue(holds(oneThere, standing.copy(monsters = listOf(monsterOn(here)))))
    }

    /** What lies on a square, which is what a plate weighs. */
    @Test
    fun `how many things lie on a square`() {
        val somethingThere = listOf(
            Conditional.ItemCountAtLocation(
                type = null,
                countingWhatIsInTheAir = false,
                location = here,
            ),
            number(0),
            Conditional.NotEquals,
        )

        assertFalse(holds(somethingThere))
        assertTrue(holds(somethingThere, standing.copy(items = listOf(lyingOn(here)))))
    }

    /** A thing in the air over a square is passing across it, not lying on it. */
    @Test
    fun `what is in the air does not lie on the square`() {
        val inTheAir = standing.copy(
            items = listOf(lyingOn(here).copy(place = SquarePlace.MIDDLE)),
        )

        fun counted(countingWhatIsInTheAir: Boolean) = holds(
            listOf(
                Conditional.ItemCountAtLocation(null, countingWhatIsInTheAir, here),
                number(0),
                Conditional.NotEquals,
            ),
            inTheAir,
        )

        assertFalse(counted(countingWhatIsInTheAir = false))
        assertTrue(counted(countingWhatIsInTheAir = true))
    }

    /** And one particular thing, named by its place in the world's table. */
    @Test
    fun `whether one named thing lies on a square`() {
        val world = standing.copy(items = listOf(lyingOn(Location(9, 9)), lyingOn(here)))

        fun asksAfter(slot: Int) =
            holds(listOf(Conditional.IsItemAtLocation(ItemIndex(slot), here)), world)

        assertTrue(asksAfter(1), "the second thing is the one lying here")
        assertFalse(asksAfter(0), "the first lies somewhere else")
    }

    @Test
    fun `whether the party hold anybody of a class or a race`() {
        val party = runBlocking {
            OriginalSaveRepositoryImpl(ResourceRepositoryImpl())
                .loadOriginalSave(OriginalSaveRepositoryImpl.QUICK_START)
                .getOrThrow()
                .party
        }
        val world = standing.copy(champions = party)
        val theirs = party.first { it.inTheParty }.race
        val nobodyIs = Race.entries.first { race -> party.none { it.inTheParty && it.race == race } }

        assertTrue(holds(listOf(Conditional.HasClass(setOf(CharacterClass.PALADIN))), world))
        assertFalse(holds(listOf(Conditional.HasClass(setOf(CharacterClass.RANGER))), world))

        assertTrue(holds(listOf(Conditional.HasRace(theirs)), world))
        assertFalse(holds(listOf(Conditional.HasRace(nobodyIs)), world))
    }

    /**
     * What the player answered, which a script may test more than once and
     * well past the branch it chose. Nothing asked yet is nothing answered.
     */
    @Test
    fun `the answer to a question the script asked`() {
        fun answeredWith(button: Int): Boolean {
            val asks = Dialog.RunDialog(
                textId = DialogueTextId(1),
                button1 = MessageId(1),
                button2 = MessageId(2),
                button3 = MessageId(3),
            )
            val instructions = listOf(
                Script(ScriptOffset(0), asks),
                Script(
                    ScriptOffset(5),
                    Eval(
                        listOf(Conditional.DialogResult, number(2), Conditional.Equals),
                        goto = ScriptOffset(20),
                    ),
                ),
                Script(ScriptOffset(10), Message(CARRIED_ON, color = 15)),
                Script(ScriptOffset(20), End),
            )

            return whatHappened(instructions, standing, answering = listOf(button))
        }

        assertTrue(answeredWith(2))
        assertFalse(answeredWith(1))

        assertFalse(
            holds(listOf(Conditional.DialogResult, number(0), Conditional.NotEquals)),
            "no question was asked, so nothing was answered",
        )
    }

    /** Nothing on the stack at all is false rather than a crash. */
    @Test
    fun `a condition with nothing in it holds nothing`() {
        assertEquals(false, holds(emptyList()))
    }

    // --- what the party stand on ---------------------------------------------

    /** Whether the party stand on a square is asked about a named square. */
    @Test
    fun `where the party stand is answered about the square asked after`() {
        val standingHere = listOf(
            Conditional.IsPartyAtLocation.CheckCurrentBlock(here),
            number(1),
            Conditional.Equals,
        )

        assertTrue(holds(standingHere))
        assertFalse(holds(standingHere, GameState(PartyState(Location(9, 9), Direction.NORTH))))
    }

    /**
     * A plate asks the other way about — whether the square is clear of the
     * party — so that a thing left on it counts only once they have walked
     * off. Answering "somebody is standing there" whatever is asked leaves
     * every plate in the game unweighable.
     */
    @Test
    fun `a square the party have left reads as empty of them`() {
        val nobodyThere = listOf(
            Conditional.IsPartyAtLocation.CheckCurrentBlock(here),
            number(0),
            Conditional.Equals,
        )

        assertFalse(holds(nobodyThere))
        assertTrue(holds(nobodyThere, GameState(PartyState(Location(9, 9), Direction.NORTH))))
    }

    private fun monsterOn(at: Location) = MonsterInstance(
        index = 0,
        unit = 0,
        block = (at.y shl 5) or at.x,
        place = SquarePlace.MIDDLE,
        direction = Direction.NORTH,
        type = MonsterTypeId(0),
        gfxIndex = 0,
        mode = 0,
        pause = 0,
        weapon = 0,
        pocketItem = 0,
    )

    private fun lyingOn(at: Location) = Item(
        nameUnidentified = ItemNameId(0),
        nameIdentified = ItemNameId(0),
        flags = 0,
        icon = ItemIconId(1),
        type = ItemTypeId(0),
        place = SquarePlace.NORTH_WEST,
        location = at,
        next = 0,
        prev = 0,
        level = 0,
        value = 0,
    )

    private companion object {
        val CARRIED_ON = MessageId(1)
    }
}
