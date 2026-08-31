package pl.pelotasplus.eyeofbeholder.data

import pl.pelotasplus.eyeofbeholder.data.model.FloorClocks
import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.ScriptTimer
import pl.pelotasplus.eyeofbeholder.data.model.Ticks
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The countdown behind a floor's clocks, which is worth having on its own
 * because a waking is not a free thing to fire: it runs a script, and a script
 * starting cancels whatever was talking.
 */
class FloorClocksTest {

    private val step = Ticks(2)

    private fun clockOn(where: Location, every: Int) =
        ScriptTimer(watches = where, ticks = Ticks(every))

    private fun FloorClocks.run(steps: Int, held: Boolean = false) =
        (1..steps).map { stepped(step, held) }

    @Test
    fun `a clock comes round after its own interval and not before`() {
        val clocks = FloorClocks(listOf(clockOn(A_SQUARE, every = 6)))

        assertEquals(
            listOf(emptyList(), emptyList(), listOf(A_SQUARE)),
            clocks.run(steps = 3),
        )
    }

    @Test
    fun `and goes on coming round`() {
        val clocks = FloorClocks(listOf(clockOn(A_SQUARE, every = 6)))

        assertEquals(2, clocks.run(steps = 6).count { it.isNotEmpty() })
    }

    /** Walking onto a floor is not itself a waking. */
    @Test
    fun `a clock starts at its full interval`() {
        val clocks = FloorClocks(listOf(clockOn(A_SQUARE, every = 6)))

        assertEquals(emptyList(), clocks.stepped(step))
    }

    @Test
    fun `two clocks keep their own time`() {
        val clocks = FloorClocks(
            listOf(clockOn(A_SQUARE, every = 2), clockOn(ANOTHER_SQUARE, every = 4)),
        )

        assertEquals(
            listOf(listOf(A_SQUARE), listOf(A_SQUARE, ANOTHER_SQUARE)),
            clocks.run(steps = 2),
        )
    }

    // --- being held ----------------------------------------------------------

    /**
     * The one that matters: a script owns the screen while it runs, and a
     * waking that talked over it would take down what it had up. A floor met
     * with a vision showed it for the length of its clock and no longer.
     */
    @Test
    fun `a held clock does not come round`() {
        val clocks = FloorClocks(listOf(clockOn(A_SQUARE, every = 6)))

        assertEquals(
            List(10) { emptyList<Location>() },
            clocks.run(steps = 10, held = true),
        )
    }

    /** Held, it keeps its full interval rather than banking up wakings. */
    @Test
    fun `it comes round in its own time once let go`() {
        val clocks = FloorClocks(listOf(clockOn(A_SQUARE, every = 6)))
        clocks.run(steps = 10, held = true)

        assertEquals(
            listOf(emptyList(), emptyList(), listOf(A_SQUARE)),
            clocks.run(steps = 3),
        )
    }

    // --- a floor changed underfoot -------------------------------------------

    @Test
    fun `a floor changed underfoot brings its own clocks`() {
        val clocks = FloorClocks(listOf(clockOn(A_SQUARE, every = 6)))
        clocks.run(steps = 2)
        clocks.nowKeeping(listOf(clockOn(ANOTHER_SQUARE, every = 6)))

        assertEquals(
            listOf(emptyList(), emptyList(), listOf(ANOTHER_SQUARE)),
            clocks.run(steps = 3),
        )
    }

    /** The same floor is not a change, so its clocks are not wound back. */
    @Test
    fun `the same clocks go on counting`() {
        val clocks = FloorClocks(listOf(clockOn(A_SQUARE, every = 6)))
        clocks.run(steps = 2)
        clocks.nowKeeping(listOf(clockOn(A_SQUARE, every = 6)))

        assertEquals(listOf(listOf(A_SQUARE)), clocks.run(steps = 1))
    }

    @Test
    fun `a floor with no clocks does nothing`() {
        assertEquals(List(5) { emptyList<Location>() }, FloorClocks().run(steps = 5))
    }

    private companion object {
        val A_SQUARE = Location(17, 7)
        val ANOTHER_SQUARE = Location(3, 4)
    }
}
