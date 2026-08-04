package pl.pelotasplus.eyeofbeholder.data.model

import kotlin.jvm.JvmInline

/**
 * Which button the player pressed, counted from one — scripts compare it
 * against literal 1, 2 and 3.
 *
 * Build it with [forButton] rather than by hand: buttons are drawn from a
 * zero-based list, and the off-by-one belongs in one place.
 */
@JvmInline
value class DialogAnswer(val number: Int) {
    companion object {
        fun forButton(index: Int) = DialogAnswer(index + 1)

        /**
         * What comes back from something nobody had to click. Zero is no
         * button, and no script tests for it.
         */
        val UNASKED = DialogAnswer(0)
    }
}
