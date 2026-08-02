package pl.pelotasplus.eyeofbeholder.features.view_cone_debug

import androidx.compose.ui.input.key.Key
import pl.pelotasplus.eyeofbeholder.data.model.Typing

/**
 * What a key means while a save is being named.
 *
 * The letters come from the code point rather than the key, so that whatever
 * layout the keyboard has, what appears is what was pressed. Anything that is
 * not a printable character and not one of the three keys that finish the job
 * is ignored, which is what keeps the arrows from walking the party about
 * behind the menu.
 */
fun typingFor(key: Key, character: Char): Typing? = when {
    key == Key.Enter || key == Key.NumPadEnter -> Typing.Accept
    key == Key.Escape -> Typing.Abandon
    key == Key.Backspace || key == Key.Delete -> Typing.Rubout
    character.code in TYPEABLE -> Typing.Letter(character)
    else -> null
}

/** Printable ASCII, which is exactly what the game's fonts have a glyph for. */
private val TYPEABLE = 32..126

/**
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
