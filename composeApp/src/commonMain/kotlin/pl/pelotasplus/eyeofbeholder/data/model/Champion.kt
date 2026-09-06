package pl.pelotasplus.eyeofbeholder.data.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.jvm.JvmInline
import pl.pelotasplus.eyeofbeholder.data.model.CharacterClass.CLERIC
import pl.pelotasplus.eyeofbeholder.data.model.CharacterClass.FIGHTER
import pl.pelotasplus.eyeofbeholder.data.model.CharacterClass.MAGE
import pl.pelotasplus.eyeofbeholder.data.model.CharacterClass.PALADIN
import pl.pelotasplus.eyeofbeholder.data.model.CharacterClass.RANGER
import pl.pelotasplus.eyeofbeholder.data.model.CharacterClass.THIEF

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
    val race: Race? = null,
    val sex: Sex? = null,
    @Serializable(with = CharacterClassAsTheGameWritesIt::class)
    val characterClass: CharacterClass? = null,
    @Serializable(with = AlignmentAsTheGameWritesIt::class)
    val alignment: Alignment? = null,
    /** One per class the champion has levels in, so a multi-class has several. */
    val levels: List<ClassLevel>,
    val carrying: List<ItemIndex>,
    private val flags: ChampionFlags,
) {
    val inTheParty: Boolean get() = flags.inTheParty

    /**
     * Whether the panel draws this one in red — the name and the numbers
     * redden for anything wrong with the champion rather than only for
     * death.
     */
    val inTrouble: Boolean get() = flags.inTrouble

    val dead: Boolean get() = hitPoints.current <= 0

    /** Held or paralysed: still standing, but able to do nothing about it. */
    val heldFast: Boolean get() = flags.heldFast

    /** Whether something venomous has got them and nothing has cured it. */
    val poisoned: Boolean get() = flags.poisoned

    fun poisoned(yes: Boolean) = copy(flags = flags.poisoned(yes))

    /** Held where they stand by something that will let go in its own time. */
    val paralysed: Boolean get() = flags.paralysed

    fun paralysed(yes: Boolean) = copy(flags = flags.paralysed(yes))

    /** Stone, which no clock undoes. */
    val petrified: Boolean get() = flags.petrified

    fun turnedToStone() = copy(flags = flags.turnedToStone())

    /**
     * Which classes this champion counts as when an item asks who may hold
     * it — a fighter/thief counts as both, and may hold whatever either of
     * them may.
     */
    val countsAs: Set<CharacterClass> get() = characterClass?.countsAs.orEmpty()

    /** What is in one of this champion's slots. */
    fun holding(slot: CarrySlot): ItemIndex =
        carrying.getOrElse(slot.index) { ItemIndex(ItemIndex.NOTHING) }

    /** Past the point a cleric can bring them back. */
    val deadForGood: Boolean get() = hitPoints.current <= BEYOND_RAISING

    /**
     * Whether a message can be put in this one's mouth: in the party, still
     * raisable, and not stone. Being knocked out is no bar: those three
     * things are all that is asked of whoever speaks.
     */
    val canSpeak: Boolean get() = inTheParty && !deadForGood && !flags.petrified

    /**
     * Whether this one can put a shoulder to something: in the party, still
     * standing, and free to move. More is asked here than of a speaker —
     * being knocked out does not stop a champion talking, but it does stop
     * them forcing a door.
     */
    val canAct: Boolean get() = inTheParty && !dead && !heldFast

    /**
     * Whether this one is still on their feet, which is what the party are
     * counted by when the question is whether they are lost.
     *
     * Less is asked than of somebody who can act — being held is a trouble a
     * party recover from, and does not count as being down. More is asked than
     * of somebody who can be raised: knocked out counts, and so does stone.
     *
     * That last part reads like a fault and is not. A champion at less than
     * nothing could be rested back up, so a party all senseless look as though
     * they should be able to save themselves — but the game asks this question
     * on every frame it draws, and answers it before a menu can be opened. The
     * camp they would need is never reachable, and softening this to only
     * count the past-raising would hand the party a way out they never had.
     */
    val onTheirFeet: Boolean get() = inTheParty && !dead && !flags.petrified

    /**
     * Whether this one can be fed: in the party, still standing, and not stone.
     *
     * Less is asked here than of somebody putting a shoulder to a door. Being
     * held is one of the troubles that stops a champion using their hands, and
     * it does not stop them being fed — only being turned to stone does.
     */
    val canEat: Boolean get() = inTheParty && !dead && !flags.petrified

    /**
     * Whether there is anything left of this one to hurt: in the party, not
     * already past raising, and not stone.
     *
     * Less is asked here than anywhere else — being knocked out is no
     * protection, and a champion lying at nothing goes on losing hit points
     * until they are past raising.
     */
    val canBeHurt: Boolean get() = inTheParty && !deadForGood && !flags.petrified

    companion object {
        /** How many champions the party has room for, filled or not. */
        const val PARTY_SLOTS = 6

        /** The first two of [carrying] are the hands, in the order they are drawn. */
        const val HANDS = CarrySlot.HANDS

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
            race = null,
            sex = null,
            characterClass = null,
            alignment = null,
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

    /** The two of the troubles that leave a champion unable to use their hands. */
    val heldFast: Boolean get() = value and HELD_FAST != 0

    /** Held where they stand, which wears off on its own in time. */
    val paralysed: Boolean get() = value and PARALYSED != 0

    fun paralysed(yes: Boolean) =
        ChampionFlags(if (yes) value or PARALYSED else value and PARALYSED.inv())

    /**
     * Turned to stone, which takes everything else with it: a champion made
     * stone keeps only their place in the party, and comes back out of it
     * poisoned by nothing and held by nothing.
     */
    fun turnedToStone() = ChampionFlags((value and IN_THE_PARTY) or PETRIFIED)

    /**
     * Poisoned, which takes a little off them again and again until it is
     * cured. It is not one of the troubles that stops them acting — a poisoned
     * champion fights on, and dies of it if nobody sees to them.
     */
    val poisoned: Boolean get() = value and POISONED != 0

    fun poisoned(yes: Boolean) =
        ChampionFlags(if (yes) value or POISONED else value and POISONED.inv())

    private companion object {
        const val IN_THE_PARTY = 0x01
        const val TROUBLE = 0x0E
        const val POISONED = 0x02
        const val PARALYSED = 0x04
        const val HELD_FAST = 0x0C
        const val PETRIFIED = 0x08
    }
}

/**
 * What a champion is. The first six are the classes there are; the rest are
 * the ways of being more than one at once, which is why a class is not simply
 * a set of them.
 *
 * @property bit which bit names this class where the game writes a set of
 *   them as one number. Only a single class has one; a combination is named
 *   by the classes it [countsAs].
 */
enum class CharacterClass(val bit: Int = NOT_A_SINGLE_CLASS) {
    FIGHTER(0x01),
    RANGER(0x20),
    PALADIN(0x10),
    MAGE(0x02),
    CLERIC(0x04),
    THIEF(0x08),
    FIGHTER_CLERIC,
    FIGHTER_THIEF,
    FIGHTER_MAGE,
    FIGHTER_MAGE_THIEF,
    THIEF_MAGE,
    CLERIC_THIEF,
    FIGHTER_CLERIC_MAGE,
    RANGER_CLERIC,
    CLERIC_MAGE;

    /**
     * Which classes this counts as when an item asks who may hold it.
     *
     * Not the same as which it is [levelledIn], and the game's own tables keep
     * the two apart: a ranger/cleric may hold whatever a fighter may, though
     * neither of the classes they are levelled in is a fighter's.
     */
    val countsAs: Set<CharacterClass> get() = allowances.getValue(this)

    /**
     * Which classes this is levelled in, in the order the levels and the
     * experience are kept — one for most, two or three for a multi-class.
     */
    val levelledIn: List<CharacterClass> get() = levelled.getValue(this)

    companion object {
        fun of(index: Int): CharacterClass? = entries.getOrNull(index)

        /** The classes a number names, one bit each. */
        fun setOf(bits: Int): Set<CharacterClass> =
            entries.filter { it.bit != NOT_A_SINGLE_CLASS && bits and it.bit != 0 }.toSet()

        private const val NOT_A_SINGLE_CLASS = 0
    }
}

private val allowances: Map<CharacterClass, Set<CharacterClass>> = mapOf(
    CharacterClass.FIGHTER to setOf(FIGHTER),
    CharacterClass.RANGER to setOf(RANGER),
    CharacterClass.PALADIN to setOf(PALADIN),
    CharacterClass.MAGE to setOf(MAGE),
    CharacterClass.CLERIC to setOf(CLERIC),
    CharacterClass.THIEF to setOf(THIEF),
    CharacterClass.FIGHTER_CLERIC to setOf(FIGHTER, CLERIC),
    CharacterClass.FIGHTER_THIEF to setOf(FIGHTER, THIEF),
    CharacterClass.FIGHTER_MAGE to setOf(FIGHTER, MAGE),
    CharacterClass.FIGHTER_MAGE_THIEF to setOf(FIGHTER, MAGE, THIEF),
    CharacterClass.THIEF_MAGE to setOf(THIEF, MAGE),
    CharacterClass.CLERIC_THIEF to setOf(CLERIC, THIEF),
    CharacterClass.FIGHTER_CLERIC_MAGE to setOf(FIGHTER, CLERIC, MAGE),
    CharacterClass.RANGER_CLERIC to setOf(FIGHTER, CLERIC),
    CharacterClass.CLERIC_MAGE to setOf(CLERIC, MAGE),
)

private val levelled: Map<CharacterClass, List<CharacterClass>> = mapOf(
    CharacterClass.FIGHTER to listOf(FIGHTER),
    CharacterClass.RANGER to listOf(RANGER),
    CharacterClass.PALADIN to listOf(PALADIN),
    CharacterClass.MAGE to listOf(MAGE),
    CharacterClass.CLERIC to listOf(CLERIC),
    CharacterClass.THIEF to listOf(THIEF),
    CharacterClass.FIGHTER_CLERIC to listOf(FIGHTER, CLERIC),
    CharacterClass.FIGHTER_THIEF to listOf(FIGHTER, THIEF),
    CharacterClass.FIGHTER_MAGE to listOf(FIGHTER, MAGE),
    CharacterClass.FIGHTER_MAGE_THIEF to listOf(FIGHTER, MAGE, THIEF),
    CharacterClass.THIEF_MAGE to listOf(THIEF, MAGE),
    CharacterClass.CLERIC_THIEF to listOf(CLERIC, THIEF),
    CharacterClass.FIGHTER_CLERIC_MAGE to listOf(FIGHTER, CLERIC, MAGE),
    CharacterClass.RANGER_CLERIC to listOf(RANGER, CLERIC),
    CharacterClass.CLERIC_MAGE to listOf(CLERIC, MAGE),
)

/**
 * A champion's class and alignment are written into a save as the numbers the
 * game itself uses, not as the names given here.
 *
 * Two reasons, and the first is why these exist at all: a save written before
 * either had a type of its own holds a number, and it should still be
 * readable. The second is that a name written into a save is a name that can
 * no longer be changed without breaking one.
 */
object CharacterClassAsTheGameWritesIt : KSerializer<CharacterClass?> {
    override val descriptor = PrimitiveSerialDescriptor("CharacterClass", PrimitiveKind.INT)

    override fun serialize(encoder: Encoder, value: CharacterClass?) =
        encoder.encodeInt(value?.ordinal ?: NONE)

    override fun deserialize(decoder: Decoder): CharacterClass? =
        CharacterClass.of(decoder.decodeInt())
}

object AlignmentAsTheGameWritesIt : KSerializer<Alignment?> {
    override val descriptor = PrimitiveSerialDescriptor("Alignment", PrimitiveKind.INT)

    override fun serialize(encoder: Encoder, value: Alignment?) =
        encoder.encodeInt(value?.ordinal ?: NONE)

    override fun deserialize(decoder: Decoder): Alignment? =
        Alignment.of(decoder.decodeInt())
}

/** What is written for somebody who has no class or alignment: nobody. */
private const val NONE = -1

/** One of the six races a champion can be. */
enum class Race {
    HUMAN, ELF, HALF_ELF, DWARF, GNOME, HALFLING;

    companion object {
        /**
         * Race and sex are one number with the sex in its lowest bit, so the
         * race is what is left when that is taken off.
         */
        fun of(raceAndSex: Int): Race? = entries.getOrNull(raceAndSex shr 1)
    }
}

enum class Sex {
    MALE, FEMALE;

    companion object {
        fun of(raceAndSex: Int): Sex? = entries.getOrNull(raceAndSex and 1)
    }
}

/** Where a champion stands on the two axes, as one of the nine. */
enum class Alignment {
    LAWFUL_GOOD, NEUTRAL_GOOD, CHAOTIC_GOOD,
    LAWFUL_NEUTRAL, TRUE_NEUTRAL, CHAOTIC_NEUTRAL,
    LAWFUL_EVIL, NEUTRAL_EVIL, CHAOTIC_EVIL;

    companion object {
        fun of(index: Int): Alignment? = entries.getOrNull(index)
    }
}

/** Which of the six places in the party, filled or not. */
@JvmInline
@Serializable
value class PartySlot(val index: Int) {

    /**
     * Which quarter of their square this champion stands in, as they see it.
     *
     * A square has four quarters and a party has six, so the back rank stand
     * behind the middle two rather than in quarters of their own: a champion
     * behind is loosed from the quarter of whoever stands in front of them.
     */
    val standsIn: ViewPlace
        get() = ViewPlace.entries[if (index > LAST_WITH_ITS_OWN) index - BEHIND_THEM else index]

    private companion object {
        /** The last slot with a quarter to itself; past this is the back rank. */
        const val LAST_WITH_ITS_OWN = 3
        const val BEHIND_THEM = 2
    }
}

/**
 * Which of a champion's twenty-seven slots: the two hands, the fourteen
 * pockets of the pack, and what is worn.
 */
@JvmInline
@Serializable
value class CarrySlot(val index: Int) {
    /** The hands are the first two, and are the only ones a curse sticks to. */
    val isAHand: Boolean get() = index < HANDS

    companion object {
        const val HANDS = 2

        /** How many a champion has, filled or not. */
        const val ALL_OF_THEM = 27

        /** A champion carrying nothing at all, with every slot still there. */
        val NOTHING_IN_ANY = List(ALL_OF_THEM) { ItemIndex(ItemIndex.NOTHING) }

        val WORN_ARMOUR = CarrySlot(17)
        val QUIVER = CarrySlot(16)
        val WORN_HELMET = CarrySlot(18)

        /** The two a ring goes on, which are worth armour between them. */
        val RINGS = listOf(CarrySlot(25), CarrySlot(26))
    }
}

/** Which of the 44 faces in CHARGENA.CPS a champion wears. */
@JvmInline
@Serializable
value class PortraitId(val value: Int)

/** How badly hurt a champion is; [current] can go negative, and -10 is dead for good. */
@Serializable
data class HitPoints(val current: Int, val max: Int)

/**
 * Hit points taken off by one blow, which champions and monsters lose alike.
 * Nothing heals by dealing a negative one: none is none.
 */
@Serializable
@JvmInline
value class Damage(val points: Int) {
    val landed: Boolean get() = points > 0
}

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
data class ClassLevel(val level: Int, val experience: XpPoints)

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
