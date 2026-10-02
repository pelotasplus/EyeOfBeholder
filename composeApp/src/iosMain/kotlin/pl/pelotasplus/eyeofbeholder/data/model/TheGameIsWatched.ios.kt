package pl.pelotasplus.eyeofbeholder.data.model

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState

/** The lifecycle's answer, which here is the app being the one in front. */
@Composable
actual fun WatchWhetherAnybodyIsLooking(watched: TheGameIsWatched) {
    val state by LocalLifecycleOwner.current.lifecycle.currentStateAsState()

    LaunchedEffect(state) {
        watched.nowWatched(state.isAtLeast(Lifecycle.State.STARTED))
    }
}
