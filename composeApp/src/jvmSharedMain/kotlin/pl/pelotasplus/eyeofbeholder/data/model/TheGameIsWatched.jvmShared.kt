package pl.pelotasplus.eyeofbeholder.data.model

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState

/**
 * The lifecycle's answer, which is the right one here.
 *
 * On a phone it is the app being in front, and on a desktop the window being
 * up rather than minimised. Neither is focus: a window behind another one can
 * still be seen, so it still counts as watched.
 */
@Composable
actual fun WatchWhetherAnybodyIsLooking(watched: TheGameIsWatched) {
    val state by LocalLifecycleOwner.current.lifecycle.currentStateAsState()

    LaunchedEffect(state) {
        watched.nowWatched(state.isAtLeast(Lifecycle.State.STARTED))
    }
}
