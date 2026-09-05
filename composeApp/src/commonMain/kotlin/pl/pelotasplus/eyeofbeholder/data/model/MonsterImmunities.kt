package pl.pelotasplus.eyeofbeholder.data.model

import kotlin.jvm.JvmInline

/**
 * What a monster cannot be harmed by, transcribed from its entry on the level.
 *
 * Two of the bits say how good a weapon has to be before it reaches the
 * creature at all. That is not a penalty to the roll: a plainer weapon is
 * refused before any die is thrown, so a party carrying nothing good enough
 * cannot wear the thing down by swinging for longer. The floor 11 guardian is
 * one of these — plain steel and a bare fist go through it.
 *
 * Other bits name kinds of harm the creature shrugs off — see [shrugsOff] —
 * and the rest name spells it cannot be held or frightened by, which nothing
 * asks about yet.
 */
@JvmInline
value class MonsterImmunities(private val written: Int) {

    /**
     * Whether harm of these [kinds] does nothing at all to this.
     *
     * A spell is several kinds at once, and shrugging off any one of them is
     * enough: a creature immune to cold takes nothing from a cone of cold,
     * magical though that is too.
     */
    fun shrugsOff(kinds: Set<HarmKind>): Boolean =
        kinds.any { kind ->
            kind.asACreatureTurnsItAside?.let { written and it != 0 } == true
        }

    /**
     * Whether a weapon carrying [enchantment] can land on this at all — a
     * bare fist being an enchantment of nothing.
     *
     * Only a weapon is asked. Something swung that is not one is refused
     * earlier and never gets here, and a trap's bolt has nobody holding it.
     */
    fun canBeHitBy(enchantment: Int): Boolean = enchantment >= leastThatTells

    private val leastThatTells: Int
        get() = when {
            written and NOTHING_UNDER_PLUS_TWO != 0 -> 2
            written and NOTHING_UNDER_PLUS_ONE != 0 -> 1
            else -> 0
        }

    private companion object {
        const val NOTHING_UNDER_PLUS_ONE = 0x200
        const val NOTHING_UNDER_PLUS_TWO = 0x1000
    }
}
