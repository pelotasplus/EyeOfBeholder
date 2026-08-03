package pl.pelotasplus.eyeofbeholder.data.model

import kotlinx.serialization.Serializable
import pl.pelotasplus.eyeofbeholder.data.model.script.CreateMonster

/**
 * One live monster placed on a level, parsed from the INF file's Block B
 * (up to 30 records of 14 bytes each; records starting with 0xFF are empty).
 *
 * @property index Monster slot index from the file (first byte of the record)
 * @property unit Which group the monster is updated with. NOT a sublevel — see
 *   [subLevel] for that.
 * @property subLevel Which sublevel this monster belongs to, and so which
 *   sublevel's tables say what it is. The file does not record it: a monster
 *   takes the sublevel that was being loaded when it was placed, which for the
 *   ones the file lists is whichever the party entered by. The same record is
 *   therefore a different creature in each — level 3's type 0 is a gelatinous
 *   cube read by its first sublevel and a worker ant by its second.
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
@Serializable
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
    val subLevel: Int = 0,
) {
    val x: Int get() = block and 0x1F
    val y: Int get() = block shr 5

    /** Which of its sheet's color schemes this monster is painted in. */
    val colors: MonsterColors get() = MonsterColors.forSlot(index)

    companion object {
        /**
         * The monster a script's [CreateMonster] asks for, in a free [slot],
         * belonging to the sublevel it was conjured in.
         */
        fun spawnedBy(spawn: CreateMonster, slot: Int, subLevel: Int = 0) = MonsterInstance(
            subLevel = subLevel,
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
