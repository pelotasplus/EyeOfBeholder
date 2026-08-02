package pl.pelotasplus.eyeofbeholder.data.model

/**
 * Whether the player can type into the game's own screen.
 *
 * A field painted into the play field is only a picture. On a desktop or in a
 * browser the key presses arrive anyway, but a phone raises its keyboard for a
 * real text control and for nothing else, so there would be nowhere for the
 * letters to come from. Where that is so, a save names itself instead — which
 * is what the original's own console versions do.
 */
expect val canTypeIntoTheGame: Boolean

/** What a key press means while a save is being named. */
sealed interface Typing {
    data class Letter(val character: Char) : Typing
    data object Rubout : Typing
    data object Accept : Typing
    data object Abandon : Typing
}

data class Naming(val slot: Int, val typed: String) {

    fun after(typing: Typing): Naming = when (typing) {
        is Typing.Letter -> if (typed.length < LONGEST) copy(typed = typed + typing.character) else this
        Typing.Rubout -> copy(typed = typed.dropLast(1))
        else -> this
    }

    val nameable: Boolean get() = typed.isNotBlank()

    companion object {
        /** As many as the original's twenty byte description holds, less its terminator. */
        const val LONGEST = 19
    }
}
