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
 * Others take some of a blow off rather than all of it — see [softened] — or
 * name kinds of harm the creature shrugs off entirely — see [shrugsOff]. The
 * rest name spells it cannot be frightened or slain by, which nothing asks
 * about yet.
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

    /**
     * Whether it cannot be held at all, however the throw goes.
     *
     * Asked after the throw is made rather than before it, which costs a die
     * and changes nothing — the order is the game's.
     */
    val cannotBeHeld: Boolean get() = written and NEVER_HELD != 0

    /**
     * Whether this kind of magic passes it by entirely: no throw, no effect,
     * and nothing said.
     *
     * Distinct from [cannotBeHeld], which is about the holding; this is about
     * the spell reaching it at all.
     */
    val untouchedByThisMagic: Boolean get() = written and NOTHING_OF_THE_SORT != 0

    /**
     * What a blow of [damage] comes to once this has turned some of it aside.
     *
     * An edge can be halved, for a creature a blade barely cuts. Then a
     * creature that shrugs off weak weapons takes a quarter from anything
     * short of +3 and half from a +3; a blow it shrugs off entirely still
     * costs it the weapon's own bonus. Magic it takes by half.
     */
    fun softened(damage: Damage, by: DealtBy): Damage {
        var points = damage.points

        if (by is DealtBy.AWeapon && by.edged && written and BLADES_HALVED != 0) {
            points = points shr 1
        }

        if (written and WEAK_WEAPONS_SHRUGGED_OFF != 0) {
            points = when (by) {
                DealtBy.Magic -> points shr 1
                is DealtBy.AWeapon -> when {
                    by.enchantment < 3 -> points shr 2
                    by.enchantment == 3 -> points shr 1
                    else -> points
                }.let { if (it == 0) by.enchantment else it }
            }
        }

        return Damage(points.coerceAtLeast(0))
    }

    private val leastThatTells: Int
        get() = when {
            written and NOTHING_UNDER_PLUS_TWO != 0 -> 2
            written and NOTHING_UNDER_PLUS_ONE != 0 -> 1
            else -> 0
        }

    private companion object {
        const val NOTHING_UNDER_PLUS_ONE = 0x200
        const val NOTHING_UNDER_PLUS_TWO = 0x1000

        const val NEVER_HELD = 0x2
        const val NOTHING_OF_THE_SORT = 0x10

        const val BLADES_HALVED = 0x100
        const val WEAK_WEAPONS_SHRUGGED_OFF = 0x2000
    }
}

/** What a blow was dealt with, which is what some creatures care about. */
sealed interface DealtBy {
    /** A weapon swung or thrown, or a bare hand, which is a weapon of nothing. */
    data class AWeapon(val enchantment: Int, val edged: Boolean) : DealtBy

    data object Magic : DealtBy
}
