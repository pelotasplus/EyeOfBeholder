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
 * @property immunities What cannot harm it, weapons too plain to touch it
 *   included
 * @property capsFlags Capability flags (flying, invisible, etc.)
 * @property typeFlags Type classification (undead, dragon, giant, etc.)
 * @property experience XP awarded when defeated
 * @property size How much of a square one takes up, and so how many will
 *   share one
 * @property sound1 Attack sound effect index
 * @property sound2 Movement sound effect index
 * @property tuResist Turn undead resistance value
 * @property dmgModifierEvade Damage modifier / evasion chance
 * @property decorations Which overlay sets a monster of this type wears, from
 *   its sheet's .DCR file — up to three, and often none
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
    val immunities: MonsterImmunities,
    val capsFlags: Int,
    val typeFlags: Int,
    val experience: Int,
    val size: MonsterSize,
    val sound1: Int, // attack sound
    val sound2: Int, // move sound
    val numRemoteAttacks: Int,
    val remoteWeaponChangeMode: Int?,
    val numRemoteWeapons: Int?,
    val remoteWeapons: List<Int>,
    val tuResist: Int, // turn undead resist
    val dmgModifierEvade: Int,
    val decorations: List<MonsterDecorationSetId>,
) {
    /**
     * Bigger than a champion, which is what says which of a weapon's two sets
     * of damage dice it is struck with — a spear does more to a giant than to
     * a man.
     */
    val isLarge: Boolean get() = capsFlags and LARGE != 0

    /**
     * Whether it lands a blow in the turn it moved in.
     *
     * Most things spend a turn either going somewhere or swinging. One of
     * these does both, so stepping out of its way buys nothing: it follows and
     * hits in the same beat, and the party have to put a square between them
     * rather than merely a sidestep.
     */
    val hitsAsItMoves: Boolean get() = capsFlags and HITS_AS_IT_MOVES != 0

    /**
     * Whether a shut door stops it. One that can work a door spends a step
     * opening it rather than turning away and going round.
     */
    val opensDoors: Boolean get() = capsFlags and OPENS_DOORS != 0

    /**
     * Whether it swerves round the party rather than always taking the
     * shortest way in. Something with this only takes the sideways approach
     * three times in four, which is what stops a pack arriving as one body.
     */
    val comesInSideways: Boolean get() = capsFlags and COMES_IN_SIDEWAYS != 0

    /**
     * Whether a blow of its ruins something the champion was carrying.
     *
     * Not the thing it was struck with and not the thing worn: one slot of the
     * twenty-seven, found by starting somewhere at random and taking the first
     * that holds anything of the perishable kind. So a pack full of them
     * shields the armour that would otherwise go, and a champion carrying one
     * loses that one however deep it is buried.
     */
    val ruinsSomethingItHits: Boolean get() = capsFlags and RUINS_WHAT_IT_HITS != 0

    /**
     * Whether a blow of its poisons whoever it lands on. The spider on the
     * first floor does, and so do the ants and the wasps further down.
     */
    val poisonsWhatItHits: Boolean get() = capsFlags and POISONS != 0

    /**
     * Whether one swing of its comes down on everybody it can reach rather
     * than on the first of them.
     *
     * An ordinary monster picks the nearest champion still standing and stops
     * there, so a party's back rank is safe behind its front. One of these
     * takes its swing at all six in turn, each with its own roll to hit and
     * its own dice of damage — which is what puts a healer with no armour in
     * the same danger as the fighter standing in front of her. Two kinds in
     * the whole dungeon carry it, and they are the two worth being afraid of.
     */
    val strikesEveryoneItReaches: Boolean get() = capsFlags and STRIKES_THEM_ALL != 0

    /**
     * Whether being hurt at all bursts it instead of wounding it.
     *
     * The gas spores on the eighth floor are these: nine armour class, one
     * hit point, and no wound worth the name. Whatever lands on one kills it
     * outright and sets it off, and what that costs depends only on how close
     * it was standing — see [GameState.monsterHurt].
     */
    val burstsWhenHurt: Boolean get() = capsFlags and BURSTS_WHEN_HURT != 0

    /**
     * What a blow of its leaves on the champion besides the wound — see
     * [WhatABlowLeaves]. Nothing has more than one of them, but the marks are
     * separate bits and nothing says one is exclusive of another.
     */
    val whatItsBlowLeaves: List<WhatABlowLeaves>
        get() = buildList {
            if (capsFlags and POISONS != 0) add(WhatABlowLeaves.POISON)
            if (capsFlags and PARALYSES != 0) add(WhatABlowLeaves.PARALYSIS)
            if (capsFlags and PETRIFIES != 0) add(WhatABlowLeaves.PETRIFICATION)
        }

    private companion object {
        const val LARGE = 0x01
        const val POISONS = 0x10
        const val HITS_AS_IT_MOVES = 0x08
        const val RUINS_WHAT_IT_HITS = 0x80
        const val COMES_IN_SIDEWAYS = 0x200
        const val OPENS_DOORS = 0x1000
        const val PARALYSES = 0x20
        const val BURSTS_WHEN_HURT = 0x2000
        const val STRIKES_THEM_ALL = 0x4000
        const val PETRIFIES = 0x8000
    }
}
