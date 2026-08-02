package pl.pelotasplus.eyeofbeholder

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLElement
import pl.pelotasplus.eyeofbeholder.di.initKoin

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    initKoin()
    ComposeViewport {
        App()
    }
    handTheKeyboardToTheCanvas()
}

/**
 * Gives the browser's focus to the canvas as soon as there is one.
 *
 * Compose listens for key presses on the canvas it draws into, and a freshly
 * loaded page has the document focused instead — so until something is
 * clicked the keys go nowhere, and asking for focus inside the composition
 * does not help, because that is a different focus entirely.
 *
 * The canvas is built once the graphics stack is ready rather than by the time
 * [ComposeViewport] returns, and it is put inside a shadow root, so it is
 * watched for. If it has not appeared within [framesLeft] frames the watch is
 * given up, and a click will still do it.
 */
private fun handTheKeyboardToTheCanvas(framesLeft: Int = FRAMES_TO_WATCH_FOR_THE_CANVAS) {
    val canvas = document.body?.shadowRoot?.querySelector("canvas") as? HTMLElement

    when {
        canvas != null -> canvas.focus()
        framesLeft > 0 -> window.requestAnimationFrame {
            handTheKeyboardToTheCanvas(framesLeft - 1)
        }
    }
}

private const val FRAMES_TO_WATCH_FOR_THE_CANVAS = 120
