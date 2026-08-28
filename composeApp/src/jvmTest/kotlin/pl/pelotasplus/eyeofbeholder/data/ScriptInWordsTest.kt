package pl.pelotasplus.eyeofbeholder.data

import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.script.Conditional
import pl.pelotasplus.eyeofbeholder.data.model.script.Eval
import pl.pelotasplus.eyeofbeholder.data.model.script.ScriptOffset
import pl.pelotasplus.eyeofbeholder.data.model.script.Sound
import pl.pelotasplus.eyeofbeholder.data.model.script.inWords
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The trace is read to answer why a script went the way it did, so a step has
 * to say what it does without the reader holding the opcode table in their
 * head. The conditions are the half worth pinning: they are written on a
 * stack, and folding them back into an expression is the one place this can
 * quietly get an `and` the wrong way round.
 */
class ScriptInWordsTest {

    /** The keyhole on the second floor: either of two doorways counts as shut. */
    @Test
    fun `an either-or on two walls reads as one line`() {
        val door = Location(24, 12)
        val condition = listOf(
            Conditional.GetWallSide(wallIndex = 2, location = door),
            Conditional.ImmediateShort(8),
            Conditional.Equals,
            Conditional.GetWallSide(wallIndex = 2, location = door),
            Conditional.ImmediateShort(18),
            Conditional.Equals,
            Conditional.Or,
        )

        assertEquals(
            "wall 24x12 SOUTH == 8 or wall 24x12 SOUTH == 18",
            condition.inWords(),
        )
    }

    /** The same keyhole asking for one key rather than any key. */
    @Test
    fun `a pair of tests on the thing in hand reads as one line`() {
        val condition = listOf(
            Conditional.GetPointerItem.ItemType,
            Conditional.ImmediateShort(38),
            Conditional.Equals,
            Conditional.GetPointerItem.ItemValue,
            Conditional.ImmediateShort(4),
            Conditional.Equals,
            Conditional.And,
        )

        assertEquals("item kind == 38 and item worth == 4", condition.inWords())
    }

    /**
     * An operator with nothing to work on is a stream this cannot fold, and
     * printing the tokens as they came beats printing a wrong expression.
     */
    @Test
    fun `a condition that does not balance is printed as it stands`() {
        val condition = listOf(Conditional.Equals, Conditional.ImmediateShort(3))

        assertEquals("== 3", condition.inWords())
    }

    @Test
    fun `a step says what it does`() {
        assertEquals("play sound 81 at 25x12", Sound(81, Location(25, 12)).inWords())
        assertEquals(
            "if the party stand on 3x4",
            Eval(
                tokens = listOf(Conditional.IsPartyAtLocation.CheckCurrentBlock(Location(3, 4))),
                goto = ScriptOffset(0),
            ).inWords(),
        )
    }
}
