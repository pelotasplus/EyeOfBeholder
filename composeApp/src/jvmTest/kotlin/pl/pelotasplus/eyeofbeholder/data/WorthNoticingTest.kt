package pl.pelotasplus.eyeofbeholder.data

import pl.pelotasplus.eyeofbeholder.data.model.ChangeLevel
import pl.pelotasplus.eyeofbeholder.data.model.Damage
import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.GameState
import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.PartySlot
import pl.pelotasplus.eyeofbeholder.data.model.PartyState
import pl.pelotasplus.eyeofbeholder.data.model.ScriptRun
import pl.pelotasplus.eyeofbeholder.data.model.worthNoticing
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Whether a finished script run earns the frame that follows it, and the lines
 * it wrote getting there.
 *
 * The contract this pins is not the script's, it is the caller's: whoever sets
 * a trigger off hands the drawing over and does not draw for itself. So an
 * event somebody caused must be worth a frame even when its script did
 * nothing, or a step whose square declines moves the party and leaves the old
 * view on the screen.
 */
class WorthNoticingTest {

    private val before = GameState(party = PartyState(Location(5, 5), Direction.NORTH))

    private val declined = ScriptRun(before)

    // --- an event somebody caused --------------------------------------------

    /**
     * The one that matters. A square whose script looks at the party and does
     * nothing is the ordinary case of walking about, and the step still has to
     * be drawn.
     */
    @Test
    fun `an event somebody caused is worth a frame even when nothing came of it`() {
        assertTrue(declined.worthNoticing(before = before, byItself = false))
    }

    @Test
    fun `and so is one that did something`() {
        assertTrue(worldChanged().worthNoticing(before = before, byItself = false))
    }

    // --- what the floor does by itself ---------------------------------------

    @Test
    fun `a floor's own waking that left no mark is worth nothing`() {
        assertFalse(declined.worthNoticing(before = before, byItself = true))
    }

    @Test
    fun `a waking that changed the world is worth noticing`() {
        assertTrue(worldChanged().worthNoticing(before = before, byItself = true))
    }

    @Test
    fun `so is one that hurt somebody`() {
        val struck = ScriptRun(before, hurt = mapOf(PartySlot(0) to Damage(6)))

        assertTrue(struck.worthNoticing(before = before, byItself = true))
    }

    @Test
    fun `so is one that sent the party somewhere else`() {
        val sent = ScriptRun(
            before,
            changeLevel = ChangeLevel(8, 0, Location(1, 1), Direction.NORTH),
        )

        assertTrue(sent.worthNoticing(before = before, byItself = true))
    }

    /**
     * A box standing when the run began comes down with it, and the box is on
     * the screen rather than in the world — so the run cannot see that mark
     * and has to be told about it.
     */
    @Test
    fun `so is one that took a dialogue box down`() {
        assertTrue(declined.worthNoticing(before = before, byItself = true, aBoxWasUp = true))
    }

    private fun worldChanged() = ScriptRun(
        before.copy(party = PartyState(Location(6, 5), Direction.NORTH)),
    )
}
