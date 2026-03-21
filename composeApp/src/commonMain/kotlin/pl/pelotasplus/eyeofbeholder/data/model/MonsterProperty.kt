package pl.pelotasplus.eyeofbeholder.data.model

/**
 * Combat statistics and behavior definition for a monster type.
 *
 * These are parsed from the INF sublevel block and define the AD&D 2nd Edition
 * combat mechanics for each monster species on the level.
 *
 * ## AD&D combat mechanics
 * - Attack roll: d20 + hitChance ≥ target's AC → hit
 * - Damage: roll [dmgDc] dice (up to 3 attack types per round)
 * - HP: roll hpDcTimes d hpDcPips + hpDcBase at spawn
 * - Turn Undead: if [tuResist] < cleric's turn check → monster is turned/destroyed
 *
 * ## Remote attacks
 * Some monsters can throw projectiles (e.g. a kobold throwing darts).
 * [numRemoteAttacks] defines how many ranged attacks they get, and
 * [remoteWeapons] lists the item types used as projectiles.
 *
 * @property id Monster type ID (matches the byte in the INF block)
 * @property armorClass AD&D armor class (lower = harder to hit; can be negative)
 * @property hitChance THAC0 — "To Hit Armor Class 0" (lower = more accurate)
 * @property level Monster level (affects XP, turn undead checks)
 * @property hpDcTimes Number of hit dice (e.g. 4 in "4d8+2")
 * @property hpDcPips Hit die size (e.g. 8 in "4d8+2")
 * @property hpDcBase Hit point bonus (e.g. 2 in "4d8+2")
 * @property attacksPerRound Number of melee attacks per combat round
 * @property dmgDc 3 damage dice definitions for the monster's attacks
 * @property immunityFlags Bitmask of damage types the monster is immune to
 * @property capsFlags Capability flags (flying, invisible, etc.)
 * @property typeFlags Type classification (undead, dragon, giant, etc.)
 * @property experience XP awarded when defeated
 * @property sound1 Attack sound effect index
 * @property sound2 Movement sound effect index
 * @property tuResist Turn undead resistance value
 * @property dmgModifierEvade Damage modifier / evasion chance
 * @property decorations Monster decoration indices for rendering (3 entries)
 */
data class MonsterProperty(
    val id: Int,
    val armorClass: Int, // ac
    val hitChance: Int, // THAC0
    val level: Int,
    val hpDcTimes: Int,
    val hpDcPips: Int,
    val hpDcBase: Int,
    val attacksPerRound: Int,
    val dmgDc: List<DamageDice>,
    val immunityFlags: Int,
    val capsFlags: Int,
    val typeFlags: Int,
    val experience: Int,
    val u30: Int,
    val sound1: Int, // attack sound
    val sound2: Int, // move sound
    val numRemoteAttacks: Int,
    val remoteWeaponChangeMode: Int?,
    val numRemoteWeapons: Int?,
    val remoteWeapons: List<Int>,
    val tuResist: Int, // turn undead resist
    val dmgModifierEvade: Int,
    val decorations: List<Int>,
)
