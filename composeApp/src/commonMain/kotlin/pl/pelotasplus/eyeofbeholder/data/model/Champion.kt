package pl.pelotasplus.eyeofbeholder.data.model

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

/**
 * One of the six who make up the party.
 *
 * The six slots always exist — the panel draws an empty box for a slot nobody
 * fills — so a slot with nobody in it is [Champion] with [inTheParty] false
 * rather than a null, and the roster is always six long.
 *
 * Only what can be named is modelled. A record also carries memorised spells,
 * running timers and spell effects, which are read past rather than kept:
 * there is no spellcasting to give them meaning yet, and a field nobody reads
 * is a field nobody notices going wrong.
 */
@Serializable
data class Champion(
    val name: String,
    val portrait: PortraitId,
    val abilities: Abilities,
    val hitPoints: HitPoints,
    val armorClass: ArmorClass,
    val food: Food,
    val raceAndSex: Int,
    val characterClass: Int,
    val alignment: Int,
    /** One per class the champion has levels in, so a multi-class has several. */
    val levels: List<ClassLevel>,
    val carrying: List<ItemIndex>,
    private val flags: ChampionFlags,
) {
    val inTheParty: Boolean get() = flags.inTheParty

    /**
     * Whether the panel draws this one in red — the original reddens the name
     * and the numbers for anything wrong with the champion rather than only
     * for death.
     */
    val inTrouble: Boolean get() = flags.inTrouble

    val dead: Boolean get() = hitPoints.current <= 0

    /** Past the point a cleric can bring them back. */
    val deadForGood: Boolean get() = hitPoints.current <= BEYOND_RAISING

    /**
     * Whether a message can be put in this one's mouth: in the party, still
     * raisable, and not stone. Being knocked out is no bar — the original asks
     * only these three things of whoever it picks to speak.
     */
    val canSpeak: Boolean get() = inTheParty && !deadForGood && !flags.petrified

    companion object {
        /** How many champions the party has room for, filled or not. */
        const val PARTY_SLOTS = 6

        /** The first two of [carrying] are the hands, in the order they are drawn. */
        const val HANDS = 2

        /** Hit points at which nothing short of a resurrection will do. */
        const val BEYOND_RAISING = -10

        /** The slot nobody has been rolled up into yet. */
        val NOBODY = Champion(
            name = "",
            portrait = PortraitId(0),
            abilities = Abilities(),
            hitPoints = HitPoints(0, 0),
            armorClass = ArmorClass(0),
            food = Food(0),
            raceAndSex = 0,
            characterClass = 0,
            alignment = 0,
            levels = emptyList(),
            carrying = emptyList(),
            flags = ChampionFlags(0),
        )
    }
}

/**
 * A champion's state bits.
 *
 * Bit 0 says the slot is filled at all. The rest are the ways a champion can
 * be in trouble — held, paralysed and so on — which the panel does not tell
 * apart: it reddens for any of them.
 */
@JvmInline
@Serializable
value class ChampionFlags(val value: Int) {
    val inTheParty: Boolean get() = value and IN_THE_PARTY != 0
    val inTrouble: Boolean get() = value and TROUBLE != 0

    /** Turned to stone, which only stone to flesh undoes. */
    val petrified: Boolean get() = value and PETRIFIED != 0

    private companion object {
        const val IN_THE_PARTY = 0x01
        const val TROUBLE = 0x0E
        const val PETRIFIED = 0x08
    }
}

/** Which of the 44 faces in CHARGENA.CPS a champion wears. */
@JvmInline
@Serializable
value class PortraitId(val value: Int)

/** How badly hurt a champion is; [current] can go negative, and -10 is dead for good. */
@Serializable
data class HitPoints(val current: Int, val max: Int)

/** Lower is better, which is why it is not an Int. */
@JvmInline
@Serializable
value class ArmorClass(val value: Int)

/** How full a champion is, 0 to 100. */
@JvmInline
@Serializable
value class Food(val value: Int)

/** A level in one class, for a champion who may have levels in three. */
@Serializable
data class ClassLevel(val level: Int, val experience: Long)

/** A slot in the world's item list, or [NOTHING] for an empty hand or pack slot. */
@JvmInline
@Serializable
value class ItemIndex(val value: Int) {
    val isSomething: Boolean get() = value != NOTHING

    companion object {
        const val NOTHING = 0
    }
}

/**
 * The six ability scores, each as it stands now and at most — a score can be
 * drained and restored. Strength alone has an extra percentile for the
 * eighteens, which is why it is two numbers rather than one.
 */
@Serializable
data class Abilities(
    val strength: Ability = Ability(0, 0),
    val strengthPercentile: Ability = Ability(0, 0),
    val intelligence: Ability = Ability(0, 0),
    val wisdom: Ability = Ability(0, 0),
    val dexterity: Ability = Ability(0, 0),
    val constitution: Ability = Ability(0, 0),
    val charisma: Ability = Ability(0, 0),
)

@Serializable
data class Ability(val current: Int, val max: Int)
