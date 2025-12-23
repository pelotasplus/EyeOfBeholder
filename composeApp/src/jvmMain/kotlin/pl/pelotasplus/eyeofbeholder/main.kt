package pl.pelotasplus.eyeofbeholder

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import pl.pelotasplus.eyeofbeholder.di.initKoin

fun main() {
    initKoin()
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "EyeOfBeholder",
        ) {
            App()
        }
    }
}
