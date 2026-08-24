package pl.pelotasplus.eyeofbeholder.data.model

import kotlin.jvm.JvmInline

/**
 * A speech, in the parts it stops between.
 *
 * The clerics ask whether the party has seen Amber and stop there; only once
 * that has been read does Joril answer. The break is written into the speech
 * rather than worked out from how much fits on screen, so it lands where the
 * pause belongs and not merely where the box fills up.
 */
@JvmInline
value class DialogueText(val pages: List<String>) {
    val first: String get() = pages.firstOrNull().orEmpty()

    companion object {
        val EMPTY = DialogueText(listOf(""))
    }
}
