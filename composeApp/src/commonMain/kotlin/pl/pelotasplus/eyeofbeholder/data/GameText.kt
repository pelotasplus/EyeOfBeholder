package pl.pelotasplus.eyeofbeholder.data

/**
 * A stored string with the instructions taken out of it, leaving what is
 * actually drawn.
 *
 * Bytes below 31 are directions to whatever is drawing rather than letters —
 * a page break, a colour with its argument, a tab, a line break — and the
 * ones with no meaning at all are drawn as nothing rather than as a
 * character. A reader that takes every byte for a letter puts a stray glyph
 * on the end of three of Khelben's messages, which end with byte 18.
 *
 * Only the line break survives, being the one of them that says something
 * about the shape of the text rather than about how to paint it.
 */
fun String.asTheGameDrawsIt(): String = buildString {
    var i = 0
    while (i < this@asTheGameDrawsIt.length) {
        val code = this@asTheGameDrawsIt[i].code
        i++
        when {
            code > LAST_INSTRUCTION -> append(this@asTheGameDrawsIt[i - 1])
            code == LINE_BREAK -> append('\n')
            // the byte after a colour is which colour, and is not a letter
            code == SET_COLOR_1 || code == SET_COLOR_2 -> i++
        }
    }
}

/** The highest byte that is an instruction rather than a letter. */
private const val LAST_INSTRUCTION = 30

private const val LINE_BREAK = 0x0D
private const val SET_COLOR_1 = 0x02
private const val SET_COLOR_2 = 0x06
