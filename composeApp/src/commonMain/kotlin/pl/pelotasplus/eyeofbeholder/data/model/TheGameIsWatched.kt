package pl.pelotasplus.eyeofbeholder.data.model

import androidx.compose.runtime.Composable
import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whether anybody is looking at the game, which is whether its clocks may run.
 *
 * Everything in the dungeon that happens by itself happens on a clock: a
 * monster takes its turn, a hand comes back to rest, a spell runs down, a
 * stomach empties. None of that should happen to a party nobody is watching —
 * a tab left open for an afternoon's work would otherwise be an afternoon of
 * monsters swinging at champions whose player could neither see them nor
 * answer, and a party stood still in an empty corridor would starve with
 * nobody in the room.
 *
 * Held here rather than read out of the composition because the clocks are
 * the view model's and it has no composition to read. The screen sets it from
 * whatever its platform calls being looked at — a browser tab shown, a window
 * un-minimised, an app brought forward.
 *
 * Starts true. A game whose platform never says anything about this runs as
 * it always did, which is the right way round: a signal that never arrives
 * should not leave the dungeon frozen.
 */
class TheGameIsWatched {

    private val _watched = MutableStateFlow(true)

    val watched: StateFlow<Boolean> = _watched.asStateFlow()

    fun nowWatched(being: Boolean) {
        if (_watched.value == being) return

        Logger.i(TAG) { if (being) "Somebody is looking again" else "Nobody is looking" }
        _watched.value = being
    }

    private companion object {
        const val TAG = "TheGameIsWatched"
    }
}

/**
 * Whatever this platform has for saying that nobody is looking, wired up to
 * [watched] for as long as the game is on screen.
 *
 * It is a platform's own question and the platforms do not agree on it. A
 * browser answers it about the tab; a desktop window about being minimised;
 * a phone about the app being in front. There is no one place to read it
 * from, so each says it for itself.
 */
@Composable
expect fun WatchWhetherAnybodyIsLooking(watched: TheGameIsWatched)
