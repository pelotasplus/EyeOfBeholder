package pl.pelotasplus.eyeofbeholder.features.view_cone_debug

import androidx.compose.ui.input.key.Key
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class KeyboardControlsTest {

    @Test
    fun `the arrows walk and turn`() {
        assertEquals(PlayFieldControl.FORWARD, playFieldControlFor(Key.DirectionUp))
        assertEquals(PlayFieldControl.BACKWARD, playFieldControlFor(Key.DirectionDown))
        assertEquals(PlayFieldControl.TURN_LEFT, playFieldControlFor(Key.DirectionLeft))
        assertEquals(PlayFieldControl.TURN_RIGHT, playFieldControlFor(Key.DirectionRight))
    }

    @Test
    fun `WASD sidesteps where the arrows turn`() {
        assertEquals(PlayFieldControl.FORWARD, playFieldControlFor(Key.W))
        assertEquals(PlayFieldControl.BACKWARD, playFieldControlFor(Key.S))
        assertEquals(PlayFieldControl.STRAFE_LEFT, playFieldControlFor(Key.A))
        assertEquals(PlayFieldControl.STRAFE_RIGHT, playFieldControlFor(Key.D))
    }

    @Test
    fun `the left hand turns with Q and E`() {
        assertEquals(PlayFieldControl.TURN_LEFT, playFieldControlFor(Key.Q))
        assertEquals(PlayFieldControl.TURN_RIGHT, playFieldControlFor(Key.E))
    }

    /**
     * Camp has no key because it has no screen yet; everything else the party
     * can do with the buttons they can do without reaching for the mouse.
     */
    @Test
    fun `every control but camp is on the keyboard`() {
        val bound = KEYS.mapNotNull { playFieldControlFor(it) }

        assertEquals(
            PlayFieldControl.entries.toSet() - PlayFieldControl.CAMP,
            bound.toSet(),
        )
        assertEquals(KEYS.size, bound.size, "every key listed here is bound to something")
    }

    @Test
    fun `a key nothing is bound to means nothing`() {
        assertNull(playFieldControlFor(Key.Spacebar))
        assertNull(playFieldControlFor(Key.Enter))
    }

    private val KEYS = listOf(
        Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight,
        Key.W, Key.S, Key.A, Key.D, Key.Q, Key.E,
    )
}
