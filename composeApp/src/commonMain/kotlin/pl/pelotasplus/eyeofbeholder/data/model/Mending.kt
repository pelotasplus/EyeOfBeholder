package pl.pelotasplus.eyeofbeholder.data.model

/**
 * What a spell cast on one of the party gives back.
 *
 * A spell of this kind is not thrown anywhere. It asks which champion first —
 * the asking is the spell, near enough — and what it does when answered is one
 * of three shapes: a roll, a filling up, or a number worked out from whoever
 * cast it.
 */
sealed interface Mending {

    /** What it gives [toWhom], cast by [byWhom], with [dice] to roll it on. */
    fun given(byWhom: Champion, toWhom: Champion, dice: Dice): Int

    /** The three cures, each a handful of eight-sided dice. */
    data class Rolled(val dice: DamageDice) : Mending {
        override fun given(byWhom: Champion, toWhom: Champion, dice: Dice) =
            dice.roll(this.dice.times, this.dice.pips, this.dice.base)
    }

    /**
     * Up to whatever they started the day able to take, and nothing if they
     * are already there — which is worth saying rather than silently spending
     * the casting on somebody who needed nothing.
     */
    data object ToTheBrim : Mending {
        override fun given(byWhom: Champion, toWhom: Champion, dice: Dice) =
            toWhom.hitPoints.max - toWhom.hitPoints.current
    }

    /**
     * Twice the level of whoever laid them on, which is the one mending that
     * is worth more in a practised hand than in a new one.
     *
     * The caster's own level and not the reader's, so this is the one of the
     * three that a scroll cannot flatter: a fighter reading it lays on twice
     * their own fighting.
     */
    data object TwiceWhatTheCasterHasLearnt : Mending {
        override fun given(byWhom: Champion, toWhom: Champion, dice: Dice) =
            (byWhom.levels.firstOrNull()?.level ?: 0) * 2
    }
}
