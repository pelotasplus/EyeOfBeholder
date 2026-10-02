package pl.pelotasplus.eyeofbeholder.data.model

internal actual fun thisTabIsHidden(): Boolean = documentHidden()

private fun documentHidden(): Boolean = js("document.hidden")
