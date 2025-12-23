package pl.pelotasplus.eyeofbeholder

import androidx.compose.ui.window.ComposeUIViewController
import pl.pelotasplus.eyeofbeholder.di.initKoin

fun MainViewController() = ComposeUIViewController(
    configure = {
        initKoin()
    }
) { App() }
