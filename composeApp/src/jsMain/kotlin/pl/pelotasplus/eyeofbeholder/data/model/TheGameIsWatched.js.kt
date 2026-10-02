package pl.pelotasplus.eyeofbeholder.data.model

import kotlinx.browser.document

internal actual fun thisTabIsHidden(): Boolean = document.asDynamic().hidden as Boolean
