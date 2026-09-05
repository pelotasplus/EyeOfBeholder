package pl.pelotasplus.eyeofbeholder.data.model

import kotlinx.serialization.Serializable

/**
 * AD&D dice roll notation: [times]d[pips]+[base].
 *
 * Example: DamageDice(2, 6, 1) means "roll 2d6+1" (2 six-sided dice plus 1).
 * Minimum result is always [times] + [base], maximum is [times] × [pips] + [base].
 *
 * Used for monster damage rolls, hit point generation, and spell effects.
 *
 * @property times Number of dice to roll (the "N" in NdM+B)
 * @property pips Number of faces per die (the "M" in NdM+B)
 * @property base Flat bonus added after rolling (the "B" in NdM+B)
 */
@Serializable
data class DamageDice(
    val times: Int,
    val pips: Int,
    val base: Int,
)
