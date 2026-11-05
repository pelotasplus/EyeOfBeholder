package pl.pelotasplus.eyeofbeholder

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import org.koin.core.context.GlobalContext
import pl.pelotasplus.eyeofbeholder.agent.AgentLink
import pl.pelotasplus.eyeofbeholder.di.initKoin

fun main() {
    initKoin()
    agentAsAsked()
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "EyeOfBeholder",
        ) {
            App()
        }
    }
}

/**
 * Starting the game with `EOB_AGENT=on`, or `EOB_AGENT=qa` for QA mode, is
 * the same consent as switching Agent on in the Debug menu: whoever starts
 * the game says whether an agent may play it.
 */
private fun agentAsAsked() {
    val asked = System.getenv("EOB_AGENT")?.lowercase() ?: return
    if (asked != "on" && asked != "qa") return

    val link = GlobalContext.get().get<AgentLink>()
    link.allow(true)
    if (asked == "qa") link.allowQa(true)
}
