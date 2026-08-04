package pl.pelotasplus.eyeofbeholder.data

import pl.pelotasplus.eyeofbeholder.data.model.ItemIndex
import pl.pelotasplus.eyeofbeholder.data.model.nudgeOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The small offset that keeps two things in one place from covering each
 * other exactly.
 */
class ItemNudgeTest {

    /**
     * The whole point: things that come to rest together are drawn apart.
     * Any two of the first eight in the table differ, which is as far as the
     * nudge can tell them apart before it comes round again.
     */
    @Test
    fun `no two of eight are nudged alike`() {
        val nudges = (0 until 8).map { nudgeOf(ItemIndex(it)) }

        assertEquals(8, nudges.toSet().size, "two of $nudges are the same")
    }

    /** It is the thing's own place in the table, so it never moves about. */
    @Test
    fun `a thing is nudged the same way every time`() {
        assertEquals(nudgeOf(ItemIndex(3)), nudgeOf(ItemIndex(3)))
    }

    /** Every eighth thing shares a nudge, the table being eight long. */
    @Test
    fun `the nudges come round again after eight`() {
        assertEquals(nudgeOf(ItemIndex(1)), nudgeOf(ItemIndex(17)))
    }

    /**
     * Sideways is taken in twos and along the floor in ones, so nothing is
     * shifted far enough to leave the place it is meant to be in.
     */
    @Test
    fun `a nudge is only a few pixels either way`() {
        (0 until 64).forEach { at ->
            val nudge = nudgeOf(ItemIndex(at))

            assertTrue(nudge.across in -4..4, "$at moved ${nudge.across} across")
            assertTrue(nudge.down in -2..2, "$at moved ${nudge.down} down")
        }
    }
}
