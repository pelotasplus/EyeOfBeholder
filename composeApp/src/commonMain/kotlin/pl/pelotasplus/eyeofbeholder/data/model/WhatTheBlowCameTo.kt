package pl.pelotasplus.eyeofbeholder.data.model

/**
 * What is written in a weapon hand for a moment after it swings, in place of
 * the weapon's own icon. It is the only report the player gets of how hard
 * they hit.
 *
 * A blow that landed is written as a number; everything else is a word. The
 * two that are said with a warning behind them rather than a splash of blood
 * are the two where nothing happened at all — the arm never reached, or there
 * was nothing to fire.
 *
 * The words are transcribed, and English here as [DialogueScene.MORE] and
 * [DialogueScene.OK] are: they are in the game's executable rather than in any
 * of its data files.
 */
sealed class WhatTheBlowCameTo(val lines: List<String>, val theArmDidSomething: Boolean) {

    /**
     * Whether it is written on a splash of blood or in a box of the colour
     * that means something is wrong. The same question decides both that and
     * [wait], which is deliberate rather than a simplification: the
     * two outcomes it warns about are the two where the arm never went
     * anywhere, and those are the two it does not charge the full wait for.
     */
    val onABloodySplash: Boolean get() = theArmDidSomething

    /** How long the hand is out of use for having come to this. */
    val wait: Ticks
        get() = if (theArmDidSomething) HandRecovering.AFTER_A_SWING
        else HandRecovering.REPORTING

    /** How much was taken off, which is the only outcome that is not a word. */
    class Damage(amount: Int) : WhatTheBlowCameTo(listOf("$amount"), theArmDidSomething = true)

    data object Missed : WhatTheBlowCameTo(listOf("MISS"), theArmDidSomething = true)

    /** A champion behind the front rank, whose arm does not get there. */
    data object CannotReach :
        WhatTheBlowCameTo(listOf("CAN'T", "REACH"), theArmDidSomething = false)

    /**
     * An edged weapon worked against a wall that gives, and the same with a
     * blunt one or a bare hand. Nothing strikes a wall yet, so neither of
     * these is reached; they are named because the slot has to say something
     * when it is.
     */
    data object Hacked : WhatTheBlowCameTo(listOf("HACK"), theArmDidSomething = true)
    data object Bashed : WhatTheBlowCameTo(listOf("BASH"), theArmDidSomething = true)

    /** Nothing left to fire, which wants something that fires. */
    data object NoAmmunition :
        WhatTheBlowCameTo(listOf("NO", "AMMO"), theArmDidSomething = false)

    companion object {
        /** What the slot says about a blow, or nothing for one it has nothing to say about. */
        fun of(blow: Blow): WhatTheBlowCameTo? = when (blow) {
            is Blow.Hit -> Damage(blow.damage)
            is Blow.Missed -> Missed

            // Swinging at an empty square is a miss like any other: the
            // two are told apart only where the square holds a wall that can
            // be worked, which is what Hacked and Bashed are for.
            Blow.Nothing -> Missed

            Blow.OutOfReach -> CannotReach
            Blow.StillRecovering -> null
        }
    }
}
