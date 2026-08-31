package pl.pelotasplus.eyeofbeholder.data

import pl.pelotasplus.eyeofbeholder.data.model.Item
import pl.pelotasplus.eyeofbeholder.data.model.script.Conditional
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A number a script writes into a condition is signed.
 *
 * The engine keeps an item's worth in a signed byte and pushes it onto the
 * condition stack as it stands, and it reads the number written beside it as
 * a signed word. Both sides are therefore negative where the game means -1,
 * and -1 is the whole of how a script asks a question about something that
 * has no worth left.
 *
 * Read unsigned, the number comes out as 65535 and no such question is ever
 * answered yes. The mouth on the eighth floor is the one that gives it away:
 * it wants a ration that has gone off, asks for a kind of 31 with a worth of
 * -1, and refuses the rotten ration held out to it.
 */
class NumbersWrittenIntoConditionsTest {

    private fun immediate(low: Int, high: Int) = Conditional.ImmediateShort
        .read(ByteReader(ubyteArrayOf(low.toUByte(), high.toUByte())))
        .value

    @Test
    fun `the word that means minus one is minus one`() {
        assertEquals(-1, immediate(0xFF, 0xFF), "read unsigned, and no spoiled ration matches")
    }

    /**
     * What the mouth is really asking, which is only a question at all if the
     * two sides can meet.
     */
    @Test
    fun `a spoiled ration is worth what the mouth asks for`() {
        assertEquals(
            Item.ROTTEN,
            immediate(0xFF, 0xFF),
            "the worth a script asks for is not the worth a ration carries",
        )
    }

    @Test
    fun `smaller numbers are left alone`() {
        assertEquals(0, immediate(0x00, 0x00))
        assertEquals(31, immediate(0x1F, 0x00))
        assertEquals(773, immediate(0x05, 0x03), "a block number lost its high byte")
        assertEquals(32767, immediate(0xFF, 0x7F), "the largest number that is still itself")
    }

    /** The other end of the range, which a facing or a count never reaches. */
    @Test
    fun `and the rest of the top half is negative too`() {
        assertEquals(-2, immediate(0xFE, 0xFF))
        assertEquals(-32768, immediate(0x00, 0x80))
    }
}
