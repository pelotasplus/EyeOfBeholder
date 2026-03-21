package pl.pelotasplus.eyeofbeholder.data.model.dungeon

import pl.pelotasplus.eyeofbeholder.data.model.Maz
import pl.pelotasplus.eyeofbeholder.data.model.WallSide

/**
 * Floor quadrant positions within a square, matching the original game's
 * item position encoding (pos field values 0-3).
 *
 * ```
 *  NW(0) | NE(1)
 *  ------+------
 *  SW(2) | SE(3)
 * ```
 */
enum class FloorQuadrant(val pos: Int) {
    NORTH_WEST(0),
    NORTH_EAST(1),
    SOUTH_WEST(2),
    SOUTH_EAST(3);

    companion object {
        fun fromPos(pos: Int): FloorQuadrant? = entries.firstOrNull { it.pos == pos }
    }
}

/**
 * Runtime-mutable state for a single square in the 32x32 dungeon grid.
 *
 * Created from a [Maz.Square] template at level initialization time.
 * Wall types, items, and monsters can all change during gameplay via
 * script execution, player actions, and monster AI.
 *
 * ## Item storage
 * Items are stored in two separate collections:
 * - [floorItems]: keyed by [FloorQuadrant] (NW/NE/SW/SE), for items lying on the ground
 * - [nicheItems]: keyed by [WallSide] (N/E/S/W), for items placed in wall alcoves/niches
 *
 * A square can have niches on multiple walls (e.g. an alcove on both north and east walls).
 * Which walls have niches is determined by which walls have [Maz.WallType.Decoration] types
 * with alcove decorations.
 */
class DungeonSquare(
    val x: Int,
    val y: Int,
    var north: Maz.WallType,
    var east: Maz.WallType,
    var south: Maz.WallType,
    var west: Maz.WallType,

    /** Items on the floor, keyed by quadrant position */
    val floorItems: MutableMap<FloorQuadrant, MutableList<DungeonItem>> = mutableMapOf(),

    /** Items in wall niches, keyed by wall side */
    val nicheItems: MutableMap<WallSide, MutableList<DungeonItem>> = mutableMapOf(),

    /** Active monsters on this square */
    val monsters: MutableList<DungeonMonster> = mutableListOf(),
) {
    override fun toString(): String {
        return "DungeonSquare(x=$x, y=$y, N=$north, E=$east, S=$south, W=$west, " +
                "floorItems=${floorItems.values.sumOf { it.size }}, " +
                "nicheItems=${nicheItems.values.sumOf { it.size }}, " +
                "monsters=${monsters.size})"
    }
}

/**
 * Get the wall type on the specified side of this square.
 */
fun DungeonSquare.getWall(side: WallSide): Maz.WallType {
    return when (side) {
        WallSide.NORTH -> north
        WallSide.EAST -> east
        WallSide.SOUTH -> south
        WallSide.WEST -> west
    }
}

/**
 * Set the wall type on the specified side of this square.
 */
fun DungeonSquare.setWall(side: WallSide, wallType: Maz.WallType) {
    when (side) {
        WallSide.NORTH -> north = wallType
        WallSide.EAST -> east = wallType
        WallSide.SOUTH -> south = wallType
        WallSide.WEST -> west = wallType
    }
}

/** All items on this square (floor + niches combined). */
val DungeonSquare.allItems: List<DungeonItem>
    get() = floorItems.values.flatten() + nicheItems.values.flatten()

/** True if all 4 sides are solid fixed walls. */
val DungeonSquare.blockedAllSides: Boolean
    get() = north is Maz.WallType.FixedWall && east is Maz.WallType.FixedWall
            && south is Maz.WallType.FixedWall && west is Maz.WallType.FixedWall
