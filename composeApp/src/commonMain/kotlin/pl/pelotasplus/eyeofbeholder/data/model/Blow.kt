package pl.pelotasplus.eyeofbeholder.data.model

/**
 * What one swing of a hand weapon comes to.
 *
 * Every number here is transcribed, and the rules behind them are AD&D: a
 * champion needs a roll of a twenty-sided die at or above a target worked out
 * from what they are, how experienced they are and how well armoured the
 * monster is.
 */
sealed interface Blow {

    /** Nothing was in reach, so there was nothing to swing at. */
    data object Nothing : Blow

    /** Too far back in the party to reach anything with a hand weapon. */
    data object OutOfReach : Blow

    /** The hand has not come back to rest since the last swing. */
    data object StillRecovering : Blow

    /**
     * The champion is in no state to swing — down, or held where they stand.
     * Their slots are drawn barred over, and this is the same answer given to
     * anything that asks them to strike anyway.
     */
    data object Unable : Blow

    data class Missed(val monster: Int) : Blow

    data class Hit(val monster: Int, val damage: Int) : Blow
}

/**
 * The roll a champion needs to land a blow on armour of [monsterArmorClass],
 * before anything is added to the die.
 *
 * A champion improves in steps rather than smoothly, and how big the steps are
 * and how often they come is what tells the four progressions apart: a fighter
 * gains one point of it every level, a mage one every three.
 */
fun Champion.needsToHit(monsterArmorClass: Int): Int {
    val progression = (characterClass ?: CharacterClass.FIGHTER).toHitProgression
    val level = levels.firstOrNull()?.level ?: 1

    return 20 - ((level - 1) / progression.everyLevels) * progression.byPoints - monsterArmorClass
}

/**
 * What a champion adds to the die when striking with a hand weapon, which is
 * their strength; a thrown or fired one asks their dexterity instead.
 */
val Champion.strikingBonus: Int get() = abilities.strengthToHitBonus

/**
 * How fast a class's chance of hitting improves: [byPoints] better every
 * [everyLevels] levels. All four transcribed.
 */
enum class ToHitProgression(val everyLevels: Int, val byPoints: Int) {
    AS_A_FIGHTER(everyLevels = 1, byPoints = 1),
    AS_A_MAGE(everyLevels = 3, byPoints = 1),
    AS_A_CLERIC(everyLevels = 3, byPoints = 2),
    AS_A_THIEF(everyLevels = 2, byPoints = 1),
}

/**
 * Which of the four a class improves by. A champion of several classes swings
 * as the best of them, which is why every combination with a fighter in it is
 * a fighter's.
 */
val CharacterClass.toHitProgression: ToHitProgression
    get() = when (this) {
        CharacterClass.MAGE -> ToHitProgression.AS_A_MAGE
        CharacterClass.CLERIC,
        CharacterClass.CLERIC_THIEF,
        CharacterClass.CLERIC_MAGE -> ToHitProgression.AS_A_CLERIC

        CharacterClass.THIEF,
        CharacterClass.THIEF_MAGE -> ToHitProgression.AS_A_THIEF

        else -> ToHitProgression.AS_A_FIGHTER
    }

/**
 * What strength adds to a swing, from a transcribed table: nothing at all
 * through the middle of the range, a penalty below it, and a bonus above.
 *
 * Eighteen is a special case, being the only score with a percentile roll
 * behind it, and the bands of that roll are their own table.
 */
val Abilities.strengthToHitBonus: Int
    get() {
        val plain = STRENGTH_TO_HIT.getOrElse(strength.current - 1) { 0 }
        if (strengthPercentile.current == 0) return plain

        return EXCEPTIONAL_STRENGTH.lastOrNull { strengthPercentile.current >= it.first }
            ?.second ?: plain
    }

/** And what dexterity adds to a thrown or fired one. */
val Abilities.dexterityToHitBonus: Int
    get() = DEXTERITY_TO_HIT.getOrElse(dexterity.current - 1) { 0 }

/**
 * What strength adds to the damage, which is a different table again and far
 * steeper at the top: an eighteen with a hundred behind it swings for fourteen
 * more than it rolls.
 */
val Abilities.strengthDamageBonus: Int
    get() {
        val plain = STRENGTH_DAMAGE.getOrElse(strength.current - 1) { 0 }
        if (strengthPercentile.current == 0) return plain

        return EXCEPTIONAL_STRENGTH_DAMAGE.lastOrNull { strengthPercentile.current >= it.first }
            ?.second ?: plain
    }

/**
 * What a weapon rolls against [target], which is not one roll but two: every
 * weapon carries a set of dice for something a champion's size and another for
 * something bigger, and which is used is the monster's own business.
 *
 * The flat addition is the small set's for both, which is not a slip.
 */
fun ItemType.damageAgainst(target: MonsterProperty?, dice: Dice): Int =
    if (target?.isLarge == true) dice.roll(dmgNumDiceL, dmgNumPipsL, dmgIncS)
    else dice.roll(dmgNumDiceS, dmgNumPipsS, dmgIncS)

private val STRENGTH_TO_HIT = listOf(
    -4, -3, -3, -2, -2, -1, -1, 0, 0, 0, 0, 0, 0,
    0, 0, 0, 1, 1, 3, 3, 4, 4, 5, 6, 7,
)

/** Each band of an eighteen's percentile roll, and what it adds instead. */
private val EXCEPTIONAL_STRENGTH = listOf(1 to 1, 51 to 2, 76 to 2, 91 to 2, 100 to 3)

private val DEXTERITY_TO_HIT = listOf(
    -5, -4, -3, -2, -1, 0, 0, 0, 0, 0, 0, 1, 2, 2, 3, 3, 4, 4, 4,
)

private val STRENGTH_DAMAGE = listOf(
    -3, -2, -1, -1, -1, 0, 0, 0, 0, 0, 0, 0, 0,
    0, 0, 1, 1, 2, 7, 8, 9, 10, 11, 12, 14,
)

private val EXCEPTIONAL_STRENGTH_DAMAGE = listOf(1 to 3, 51 to 3, 76 to 4, 91 to 5, 100 to 6)
