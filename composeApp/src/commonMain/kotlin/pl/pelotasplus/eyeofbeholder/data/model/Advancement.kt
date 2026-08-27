package pl.pelotasplus.eyeofbeholder.data.model

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline
import pl.pelotasplus.eyeofbeholder.data.model.CharacterClass.CLERIC
import pl.pelotasplus.eyeofbeholder.data.model.CharacterClass.FIGHTER
import pl.pelotasplus.eyeofbeholder.data.model.CharacterClass.MAGE
import pl.pelotasplus.eyeofbeholder.data.model.CharacterClass.PALADIN
import pl.pelotasplus.eyeofbeholder.data.model.CharacterClass.RANGER
import pl.pelotasplus.eyeofbeholder.data.model.CharacterClass.THIEF

/** An amount of experience, earned from a kill and banked towards a level. */
@JvmInline
@Serializable
value class XpPoints(val count: Long) : Comparable<XpPoints> {
    operator fun plus(more: XpPoints) = XpPoints(count + more.count)

    /** Split [ways] even shares, whatever is left over dropped. */
    operator fun div(ways: Int) = XpPoints(count / ways)

    override fun compareTo(other: XpPoints) = count.compareTo(other.count)

    companion object {
        val NONE = XpPoints(0)
    }
}

/** The die a class rolls its hit points on each level it gains. */
enum class HitDie(val sides: Int) {
    D4(4), D6(6), D8(8), D10(10),
}

/**
 * Earning experience, and what it buys: a higher level and the hit points that
 * come with it.
 *
 * The numbers are a fixed table — the experience each class needs for each
 * level, the die it rolls its hit points on, and how constitution sways that
 * roll — and deriving them by eye is how one drifts a point out of true.
 *
 * A kill is worth what the slain thing's kind is worth, shared first among the
 * party and then, for a multi-class champion, among their classes — so three
 * classes advance at a third of the pace of one.
 */
enum class ClassProgression(
    val hitDie: HitDie,
    private val rollsHitDieThroughLevel: Int,
    private val flatHitPointsAfter: Int,
    private val thresholds: List<XpPoints>,
) {
    AS_A_FIGHTER(hitDie = HitDie.D10, rollsHitDieThroughLevel = 9, flatHitPointsAfter = 3, thresholds = FIGHTER_XP),
    AS_A_MAGE(hitDie = HitDie.D4, rollsHitDieThroughLevel = 10, flatHitPointsAfter = 1, thresholds = MAGE_XP),
    AS_A_CLERIC(hitDie = HitDie.D8, rollsHitDieThroughLevel = 9, flatHitPointsAfter = 2, thresholds = CLERIC_XP),
    AS_A_THIEF(hitDie = HitDie.D6, rollsHitDieThroughLevel = 10, flatHitPointsAfter = 2, thresholds = THIEF_XP),
    AS_A_RANGER(hitDie = HitDie.D10, rollsHitDieThroughLevel = 9, flatHitPointsAfter = 3, thresholds = RANGER_OR_PALADIN_XP),
    AS_A_PALADIN(hitDie = HitDie.D10, rollsHitDieThroughLevel = 9, flatHitPointsAfter = 3, thresholds = RANGER_OR_PALADIN_XP);

    /** The highest level [experience] has earned, counting from one. */
    fun levelFor(experience: XpPoints): Int =
        thresholds.indexOfLast { experience >= it } + 1

    /** What [experience] must reach for [level], or null past the last level. */
    fun neededFor(level: Int): XpPoints? = thresholds.getOrNull(level - 1)

    /**
     * The hit points [level] adds before they are shared among a champion's
     * classes: a roll of the class die up to a point, a flat gain past it, and
     * either way what constitution is worth.
     */
    fun hitPointsGained(level: Int, roll: Int, constitutionBonus: Int): Int {
        val base = if (level <= rollsHitDieThroughLevel) roll else flatHitPointsAfter
        return base + constitutionBonus
    }

    /** Whether [level] is still reached by rolling the die rather than a flat gain. */
    fun rollsHitDieAt(level: Int): Boolean = level <= rollsHitDieThroughLevel
}

/** How the single class advances, for the six a champion can hold levels in. */
val CharacterClass.progression: ClassProgression
    get() = when (this) {
        FIGHTER -> ClassProgression.AS_A_FIGHTER
        MAGE -> ClassProgression.AS_A_MAGE
        CLERIC -> ClassProgression.AS_A_CLERIC
        THIEF -> ClassProgression.AS_A_THIEF
        RANGER -> ClassProgression.AS_A_RANGER
        PALADIN -> ClassProgression.AS_A_PALADIN
        else -> error("$this is not a single class")
    }

/**
 * The champion with [points] of experience earned, levelled up and healthier
 * for it where that experience was enough.
 *
 * The experience is split evenly among the champion's classes, and each is
 * followed on its own: it takes its share, and if that carries it to its next
 * level it gains one — one only, however far the share reached past it. The
 * hit points those levels add go on both what the champion has and what they
 * can hold.
 */
fun Champion.earning(points: XpPoints, dice: Dice): Champion {
    val classes = characterClass?.levelledIn ?: return this
    if (classes.isEmpty() || points <= XpPoints.NONE) return this

    val each = points / classes.size
    val constitutionBonus = constitutionHitPointBonus()

    var gainedHitPoints = 0
    val grown = levels.mapIndexed { index, standing ->
        val progression = classes.getOrNull(index)?.progression ?: return@mapIndexed standing
        val earned = standing.experience + each

        val next = standing.level + 1
        val needed = progression.neededFor(next)
        if (needed == null || earned < needed) {
            return@mapIndexed standing.copy(experience = earned)
        }

        val roll = if (progression.rollsHitDieAt(next)) dice.roll(1, progression.hitDie.sides, 0) else 0
        val gain = progression.hitPointsGained(next, roll, constitutionBonus)
        gainedHitPoints += (gain / classes.size).coerceAtLeast(1)

        ClassLevel(level = next, experience = earned)
    }

    return copy(
        levels = grown,
        hitPoints = HitPoints(
            current = hitPoints.current + gainedHitPoints,
            max = hitPoints.max + gainedHitPoints,
        ),
    )
}

/**
 * What this champion's constitution adds to each hit die.
 *
 * The gain past two hit points is a warrior's alone — a fighter, paladin or
 * ranger, or a multi-class that is one of those — and everybody else is held
 * to two however hardy they are.
 */
private fun Champion.constitutionHitPointBonus(): Int {
    val score = abilities.constitution.current
    val raw = CONSTITUTION_HIT_POINTS.firstNotNullOfOrNull { (band, bonus) -> bonus.takeIf { score in band } } ?: 0
    val warrior = characterClass?.levelledIn.orEmpty().any { it in WARRIORS }
    return if (warrior || raw <= WARRIOR_ONLY_ABOVE) raw else WARRIOR_ONLY_ABOVE
}

/**
 * The world after a kill worth [points], with the experience shared among the
 * party.
 *
 * Everybody in the party who is not past raising takes an equal share, the
 * unconscious among them included — being down does not stop a champion
 * learning from a fight the others finish.
 */
fun GameState.partyEarns(points: XpPoints, dice: Dice): GameState {
    if (points <= XpPoints.NONE) return this

    val sharers = champions.count { it.inTheParty && !it.deadForGood }
    if (sharers == 0) return this

    val each = points / sharers
    if (each <= XpPoints.NONE) return this

    return copy(
        champions = champions.map {
            if (it.inTheParty && !it.deadForGood) it.earning(each, dice) else it
        },
    )
}

private val WARRIORS = setOf(FIGHTER, PALADIN, RANGER)

/** Above this a warrior keeps climbing and everyone else stops. */
private const val WARRIOR_ONLY_ABOVE = 2

/** What a band of constitution scores is worth to each hit die. */
private val CONSTITUTION_HIT_POINTS: Map<IntRange, Int> = mapOf(
    1..1 to -3,
    2..3 to -2,
    4..6 to -1,
    7..14 to 0,
    15..15 to 1,
    16..16 to 2,
    17..17 to 3,
    18..18 to 4,
    19..19 to 5,
    20..22 to 6,
    23..24 to 7,
)

private fun xpTable(vararg thresholds: Long): List<XpPoints> = thresholds.map { XpPoints(it) }

private val FIGHTER_XP = xpTable(
    0, 2_000, 4_000, 8_000, 16_000, 32_000, 64_000,
    125_000, 250_000, 500_000, 750_000, 1_000_000, 1_250_000,
)

private val MAGE_XP = xpTable(
    0, 2_500, 5_000, 10_000, 20_000, 40_000, 60_000,
    90_000, 135_000, 250_000, 375_000, 750_000, 1_125_000,
)

private val CLERIC_XP = xpTable(
    0, 1_500, 3_000, 6_000, 13_000, 27_500, 55_000,
    110_000, 225_000, 450_000, 675_000, 900_000, 1_125_000,
)

private val THIEF_XP = xpTable(
    0, 1_250, 2_500, 5_000, 10_000, 20_000, 40_000,
    70_000, 110_000, 160_000, 220_000, 440_000, 660_000,
)

private val RANGER_OR_PALADIN_XP = xpTable(
    0, 2_250, 4_500, 9_000, 18_000, 36_000, 75_000,
    150_000, 300_000, 600_000, 900_000, 1_200_000, 1_500_000,
)
