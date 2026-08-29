package pl.pelotasplus.eyeofbeholder.data.model

/**
 * What is in a potion, which its value says.
 *
 * The eight of them and the order they come in are the game's, and so are the
 * dice: healing is 2d4+2 and extra healing 3d8+3. [feels] is what the champion
 * is said to feel, which is the whole of what the player is told — the game
 * writes "%s feels %s!" and the second half is this.
 *
 * Two of them say the same word for different reasons: healing mends, and
 * curing poison takes away what was doing the harm. The table says "better"
 * for both, and that is not a mistake to be corrected here.
 */
enum class Potion(val feels: String) {
    GIANT_STRENGTH("much stronger"),
    HEALING("better"),
    EXTRA_HEALING("much better"),
    POISON("ill for a moment"),
    VITALITY("satiated"),
    SPEED("fast and agile"),
    INVISIBILITY("transparent"),
    CURE_POISON("better");

    /** How much this one mends, which for six of the eight is nothing. */
    fun mends(dice: Dice): Int = when (this) {
        HEALING -> dice.roll(2, 4, 2)
        EXTRA_HEALING -> dice.roll(3, 8, 3)
        else -> 0
    }

    companion object {
        /** What a potion of this value is, or null for a value that names none. */
        fun of(value: Int): Potion? = entries.getOrNull(value)

        /** The line the game writes when one is drunk. */
        fun said(whose: String, feels: String) = "$whose feels $feels!"
    }
}
