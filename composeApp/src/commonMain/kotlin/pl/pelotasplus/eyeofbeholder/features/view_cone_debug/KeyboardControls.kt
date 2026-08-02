package pl.pelotasplus.eyeofbeholder.features.view_cone_debug

import androidx.compose.ui.input.key.Key

/**
 * The keys that work the same six controls as the buttons under the view.
 *
 * The two hands do not agree about left and right, deliberately. The arrows
 * turn the party, the way the buttons under them are laid out; WASD sidesteps
 * with A and D and turns with Q and E, the way a hand on the left of a
 * keyboard expects. Forward and back are the same either way.
 */
fun playFieldControlFor(key: Key): PlayFieldControl? = when (key) {
    Key.DirectionUp, Key.W -> PlayFieldControl.FORWARD
    Key.DirectionDown, Key.S -> PlayFieldControl.BACKWARD
    Key.DirectionLeft -> PlayFieldControl.TURN_LEFT
    Key.DirectionRight -> PlayFieldControl.TURN_RIGHT
    Key.Q -> PlayFieldControl.TURN_LEFT
    Key.E -> PlayFieldControl.TURN_RIGHT
    Key.A -> PlayFieldControl.STRAFE_LEFT
    Key.D -> PlayFieldControl.STRAFE_RIGHT
    else -> null
}
