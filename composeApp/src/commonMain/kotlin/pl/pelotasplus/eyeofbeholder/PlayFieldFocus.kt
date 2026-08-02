package pl.pelotasplus.eyeofbeholder

import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.focus.FocusRequester

/**
 * Where the keyboard belongs while the party are walking about.
 *
 * A key event only reaches the node that holds focus, and the Debug menu is
 * drawn over every screen: pressing one of its buttons takes the focus and
 * closing it does not hand it back, so the arrows go dead until something is
 * clicked. Neither side can fix that alone — the play field does not know the
 * menu was opened, and the menu does not know what is underneath it.
 *
 * So the play field says where the keys go, and whoever closes the menu says
 * when to give them back. A screen with nothing to type on registers nothing,
 * and [takeBack] then has nowhere to send them, which is the right answer.
 */
@Stable
class PlayFieldFocus {

    private var keyboard: FocusRequester? = null

    fun goesTo(requester: FocusRequester) {
        keyboard = requester
    }

    fun noLongerGoesTo(requester: FocusRequester) {
        if (keyboard === requester) keyboard = null
    }

    fun takeBack() {
        keyboard?.requestFocus()
    }
}

val LocalPlayFieldFocus = staticCompositionLocalOf { PlayFieldFocus() }
