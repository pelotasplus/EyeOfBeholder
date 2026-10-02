package pl.pelotasplus.eyeofbeholder.data.model

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import kotlinx.browser.document
import org.w3c.dom.events.Event

/**
 * The browser's own answer: whether this tab is the one being shown.
 *
 * Read off the page rather than off the lifecycle, because the lifecycle does
 * not carry it — a hidden tab is still STARTED as far as that is concerned,
 * so a game left in a background tab went on being played without anybody
 * there. The page says so directly and says so the moment it changes.
 *
 * Being shown is not the same as being in front of the person: a tab visible
 * in an unfocused window still counts as watched, which is right — it can be
 * seen, so what happens in it can be answered.
 */
@Composable
actual fun WatchWhetherAnybodyIsLooking(watched: TheGameIsWatched) {
    DisposableEffect(watched) {
        fun tell() = watched.nowWatched(!thisTabIsHidden())

        val listener: (Event) -> Unit = { tell() }
        document.addEventListener(VISIBILITY_CHANGED, listener)

        // Said once at the start as well as on every change, in case the game
        // was opened into a tab that was already in the background.
        tell()

        onDispose { document.removeEventListener(VISIBILITY_CHANGED, listener) }
    }
}

/**
 * Whether the page is out of sight, which the two web targets have to ask
 * for themselves: `document.hidden` is not in the externals they share.
 */
internal expect fun thisTabIsHidden(): Boolean

private const val VISIBILITY_CHANGED = "visibilitychange"
