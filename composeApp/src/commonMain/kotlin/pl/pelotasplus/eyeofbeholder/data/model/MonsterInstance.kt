package pl.pelotasplus.eyeofbeholder.data.model

import pl.pelotasplus.eyeofbeholder.data.model.script.CreateMonster

/**
 * One live monster placed on a level, parsed from the INF file's Block B
 * (up to 30 records of 14 bytes each; records starting with 0xFF are empty).
 *
 * @property index Monster slot index from the file (first byte of the record)
 * @property unit Which group the monster is updated with. NOT a sublevel — a
 *   monster's sublevel is not stored in the file at all, it is whichever one
 *   was being loaded when the monster was placed.
 * @property block Packed maze square: x = block and 0x1F, y = block shr 5
 * @property pos Position within the square (0-3 = quadrants, 4 = center)
 * @property direction Which way the monster faces
 * @property type Index into [SubLevel.monsters] (the species' stats)
 * @property gfxIndex Index into [SubLevel.monsterGfx] (which sprite sheet)
 * @property mode Behavior mode at spawn
 * @property pause Movement pause counter
 * @property weapon Item type id of the held weapon (0 = none)
 * @property pocketItem Item type id carried as loot (0 = none)
 */
data class MonsterInstance(
    val index: Int,
    val unit: Int,
    val block: Int,
    val pos: Int,
    val direction: Direction,
    val type: MonsterTypeId,
    val gfxIndex: Int,
    val mode: Int,
    val pause: Int,
    val weapon: Int,
    val pocketItem: Int,
) {
    val x: Int get() = block and 0x1F
    val y: Int get() = block shr 5

    /** Which of its sheet's color schemes this monster is painted in. */
    val colors: MonsterColors get() = MonsterColors.forSlot(index)

    companion object {
        /** The monster a script's [CreateMonster] asks for, in a free [slot]. */
        fun spawnedBy(spawn: CreateMonster, slot: Int) = MonsterInstance(
            index = slot,
            unit = spawn.unit,
            block = (spawn.location.y shl 5) or spawn.location.x,
            pos = spawn.pos,
            direction = spawn.direction,
            type = spawn.type,
            gfxIndex = spawn.gfxIndex,
            mode = spawn.mode,
            pause = spawn.pause,
            weapon = spawn.weapon,
            pocketItem = spawn.pocketItem,
        )
    }
}
