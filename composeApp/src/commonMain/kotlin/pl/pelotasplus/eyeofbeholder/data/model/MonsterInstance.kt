package pl.pelotasplus.eyeofbeholder.data.model

/**
 * One live monster placed on a level, parsed from the INF file's Block B
 * (up to 30 records of 14 bytes each; records starting with 0xFF are empty).
 *
 * @property index Monster slot index from the file (first byte of the record)
 * @property subLevelIndex Which sublevel ("unit") the monster is on
 * @property block Packed maze square: x = block and 0x1F, y = block shr 5
 * @property pos Position within the square (0-3 = quadrants, 4 = center)
 * @property direction Facing direction (0=north, 1=east, 2=south, 3=west)
 * @property type Index into [SubLevel.monsters] (the species' stats)
 * @property gfxIndex Index into [SubLevel.monsterGfx] (which sprite sheet)
 * @property mode Behavior mode at spawn
 * @property pause Movement pause counter
 * @property weapon Item type id of the held weapon (0 = none)
 * @property pocketItem Item type id carried as loot (0 = none)
 */
data class MonsterInstance(
    val index: Int,
    val subLevelIndex: Int,
    val block: Int,
    val pos: Int,
    val direction: Int,
    val type: Int,
    val gfxIndex: Int,
    val mode: Int,
    val pause: Int,
    val weapon: Int,
    val pocketItem: Int,
) {
    val x: Int get() = block and 0x1F
    val y: Int get() = block shr 5
}
