package pl.pelotasplus.eyeofbeholder.data.model.dungeon

import pl.pelotasplus.eyeofbeholder.data.model.Direction

/**
 * A live monster instance on the dungeon grid.
 *
 * Created by the [CreateMonster] script opcode or during level initialization.
 * Combat stats come from [MonsterProperty] in the sublevel (looked up via [typeIndex]).
 * Hit points are rolled at spawn from the MonsterProperty's HP dice.
 *
 * @property id Unique instance slot (0-29, matching INF monster data slots)
 * @property typeIndex Index into SubLevel.monsters ([MonsterProperty] list)
 * @property graphicUnit Which MonsterGfx sprite sheet to use for rendering
 * @property subPos Sub-position within the square (0-3 for quadrants)
 * @property facing Which direction the monster faces
 * @property hitPoints Current hit points (0 = dead)
 * @property animFrame Current animation frame for rendering
 * @property phase AI behavior phase (idle, patrol, aggressive, etc.)
 * @property pause Ticks remaining before the monster acts
 * @property pocketItemId Item dropped on death (-1 = none)
 * @property weaponItemId Wielded weapon item ID (-1 = none)
 * @property flags Per-monster script flags (set/cleared by SetFlag.MonsterFlag)
 */
data class DungeonMonster(
    val id: Int,
    val typeIndex: Int,
    val graphicUnit: Int,
    var subPos: Int,
    var facing: Direction,
    var hitPoints: Int,
    var animFrame: Int,
    var phase: Int,
    var pause: Int,
    val pocketItemId: Int,
    val weaponItemId: Int,
    var flags: Int = 0,
)
