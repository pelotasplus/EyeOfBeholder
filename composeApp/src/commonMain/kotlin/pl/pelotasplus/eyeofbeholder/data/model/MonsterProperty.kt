package pl.pelotasplus.eyeofbeholder.data.model

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
