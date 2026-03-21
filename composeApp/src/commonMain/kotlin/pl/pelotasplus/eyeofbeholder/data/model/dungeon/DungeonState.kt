package pl.pelotasplus.eyeofbeholder.data.model.dungeon

import pl.pelotasplus.eyeofbeholder.data.model.Inf
import pl.pelotasplus.eyeofbeholder.data.model.Maz
import pl.pelotasplus.eyeofbeholder.data.model.SubLevel
import pl.pelotasplus.eyeofbeholder.data.model.WallSide

/**
 * Complete mutable runtime state for a loaded dungeon level.
 *
 * Initialized from parsed [Inf] data via [fromInf], then mutated by game logic:
 * scripts, player actions, monster AI, etc.
 *
 * ## Design decisions
 * - **Not a data class**: intentionally mutable (like [ViewPort]). Contains a mutable
 *   array grid and mutable lists — `equals`/`copy` from data class would be misleading.
 * - **SubLevel references retained**: the rendering pipeline needs immutable resources
 *   (VMP, VCN, Palette, Doors, Decorations) that never change. These stay in SubLevel.
 * - **Flat array grid**: 32x32 = 1024 entries, indexed as `y * 32 + x`, matching [Maz]
 *   storage. O(1) access without Map overhead.
 * - **Separate floor/niche item storage**: floor items keyed by [FloorQuadrant] (0-3),
 *   niche items keyed by [WallSide] (N/E/S/W). A square can have niches on multiple walls.
 *
 * @property levelNumber Level number (1-16, matches LEVELn.INF)
 * @property subLevels Immutable rendering resources from the parsed INF
 * @property flags Script flag state (level flags, global flags, dialog/rest state)
 */
class DungeonState private constructor(
    val levelNumber: Int,
    val subLevels: List<SubLevel>,
    private val grid: Array<DungeonSquare>,
    val flags: FlagState,
) {
    val width = 32
    val height = 32

    /** Access a square by grid coordinates. */
    operator fun get(x: Int, y: Int): DungeonSquare {
        require(x in 0 until width && y in 0 until height) {
            "Out of bounds: ($x, $y)"
        }
        return grid[y * width + x]
    }

    // ---- Wall mutations ----

    /** Set one wall side at a location. */
    fun setWall(x: Int, y: Int, side: WallSide, wallType: Maz.WallType) {
        this[x, y].setWall(side, wallType)
    }

    /** Set all 4 walls at a location to the same type. */
    fun setAllWalls(x: Int, y: Int, wallType: Maz.WallType) {
        val square = this[x, y]
        square.north = wallType
        square.east = wallType
        square.south = wallType
        square.west = wallType
    }

    /** Toggle one wall side between two types. */
    fun toggleWall(x: Int, y: Int, side: WallSide, a: Maz.WallType, b: Maz.WallType) {
        val square = this[x, y]
        val current = square.getWall(side)
        square.setWall(side, if (current == a) b else a)
    }

    /** Toggle all 4 walls between two types (north wall used as reference). */
    fun toggleAllWalls(x: Int, y: Int, a: Maz.WallType, b: Maz.WallType) {
        val square = this[x, y]
        val newType = if (square.north == a) b else a
        square.north = newType
        square.east = newType
        square.south = newType
        square.west = newType
    }

    // ---- Door mutations ----

    /**
     * Open doors at a location. Finds door wall types on any side
     * and changes their state to 2 (open).
     */
    fun openDoor(x: Int, y: Int) {
        val square = this[x, y]
        for (side in WallSide.entries) {
            val opened = openDoorWall(square.getWall(side))
            if (opened != null) square.setWall(side, opened)
        }
    }

    /**
     * Close doors at a location. Changes door state to 0 (closed).
     */
    fun closeDoor(x: Int, y: Int) {
        val square = this[x, y]
        for (side in WallSide.entries) {
            val closed = closeDoorWall(square.getWall(side))
            if (closed != null) square.setWall(side, closed)
        }
    }

    private fun openDoorWall(wall: Maz.WallType): Maz.WallType? = when (wall) {
        is Maz.WallType.DoorTypeOneWithButton -> wall.copy(state = 2)
        is Maz.WallType.DoorTypeOneWithoutButton -> wall.copy(state = 2)
        is Maz.WallType.DoorTypeTwoWithButton -> wall.copy(state = 2)
        is Maz.WallType.DoorTypeTwoWithoutButton -> wall.copy(state = 2)
        else -> null
    }

    private fun closeDoorWall(wall: Maz.WallType): Maz.WallType? = when (wall) {
        is Maz.WallType.DoorTypeOneWithButton -> wall.copy(state = 0)
        is Maz.WallType.DoorTypeOneWithoutButton -> wall.copy(state = 0)
        is Maz.WallType.DoorTypeTwoWithButton -> wall.copy(state = 0)
        is Maz.WallType.DoorTypeTwoWithoutButton -> wall.copy(state = 0)
        else -> null
    }

    // ---- Floor item mutations ----

    /** Add an item to a floor quadrant. */
    fun addFloorItem(x: Int, y: Int, quadrant: FloorQuadrant, item: DungeonItem) {
        this[x, y].floorItems.getOrPut(quadrant) { mutableListOf() }.add(item)
    }

    /** Remove a specific item from a floor quadrant. */
    fun removeFloorItem(x: Int, y: Int, quadrant: FloorQuadrant, item: DungeonItem): Boolean {
        return this[x, y].floorItems[quadrant]?.remove(item) ?: false
    }

    /** Get all items at a floor quadrant. */
    fun getFloorItems(x: Int, y: Int, quadrant: FloorQuadrant): List<DungeonItem> {
        return this[x, y].floorItems[quadrant] ?: emptyList()
    }

    // ---- Niche item mutations ----

    /** Add an item to a wall niche. */
    fun addNicheItem(x: Int, y: Int, wallSide: WallSide, item: DungeonItem) {
        this[x, y].nicheItems.getOrPut(wallSide) { mutableListOf() }.add(item)
    }

    /** Remove a specific item from a wall niche. */
    fun removeNicheItem(x: Int, y: Int, wallSide: WallSide, item: DungeonItem): Boolean {
        return this[x, y].nicheItems[wallSide]?.remove(item) ?: false
    }

    /** Get all items in a wall niche. */
    fun getNicheItems(x: Int, y: Int, wallSide: WallSide): List<DungeonItem> {
        return this[x, y].nicheItems[wallSide] ?: emptyList()
    }

    // ---- Combined item queries ----

    /** Get all items at a square (floor + all niches). */
    fun getAllItemsAt(x: Int, y: Int): List<DungeonItem> {
        return this[x, y].allItems
    }

    /**
     * Remove the first item matching [itemType] at a location (checks all positions).
     * Returns the removed item, or null if not found.
     */
    fun removeItemByType(x: Int, y: Int, itemType: Int): DungeonItem? {
        val square = this[x, y]
        for ((_, itemList) in square.floorItems) {
            val found = itemList.firstOrNull { it.type == itemType }
            if (found != null) {
                itemList.remove(found)
                return found
            }
        }
        for ((_, itemList) in square.nicheItems) {
            val found = itemList.firstOrNull { it.type == itemType }
            if (found != null) {
                itemList.remove(found)
                return found
            }
        }
        return null
    }

    // ---- Monster mutations ----

    fun addMonster(x: Int, y: Int, monster: DungeonMonster) {
        this[x, y].monsters.add(monster)
    }

    fun removeMonster(x: Int, y: Int, monster: DungeonMonster): Boolean {
        return this[x, y].monsters.remove(monster)
    }

    fun moveMonster(fromX: Int, fromY: Int, toX: Int, toY: Int, monster: DungeonMonster) {
        removeMonster(fromX, fromY, monster)
        addMonster(toX, toY, monster)
    }

    fun getMonstersAt(x: Int, y: Int): List<DungeonMonster> {
        return this[x, y].monsters
    }

    // ---- Movement queries ----

    /**
     * Check if movement from one square to an adjacent square is possible.
     * Checks both the exit wall of the source square and the entry wall of the
     * destination square (they share the same wall, but may have different types
     * if the maze data is inconsistent).
     */
    fun canEnter(fromX: Int, fromY: Int, toX: Int, toY: Int): Boolean {
        if (toX !in 0 until width || toY !in 0 until height) return false

        val exitSide = when {
            toX > fromX -> WallSide.EAST
            toX < fromX -> WallSide.WEST
            toY > fromY -> WallSide.SOUTH
            toY < fromY -> WallSide.NORTH
            else -> return true // same square
        }
        val entrySide = when (exitSide) {
            WallSide.NORTH -> WallSide.SOUTH
            WallSide.SOUTH -> WallSide.NORTH
            WallSide.EAST -> WallSide.WEST
            WallSide.WEST -> WallSide.EAST
        }

        val exitWall = this[fromX, fromY].getWall(exitSide)
        val entryWall = this[toX, toY].getWall(entrySide)

        return isPassable(exitWall) && isPassable(entryWall)
    }

    private fun isPassable(wall: Maz.WallType): Boolean = when (wall) {
        is Maz.WallType.NoWall -> true
        is Maz.WallType.FixedWall -> false
        is Maz.WallType.DoorTypeOneWithButton -> wall.state == 2
        is Maz.WallType.DoorTypeOneWithoutButton -> wall.state == 2
        is Maz.WallType.DoorTypeTwoWithButton -> wall.state == 2
        is Maz.WallType.DoorTypeTwoWithoutButton -> wall.state == 2
        is Maz.WallType.StairUp -> true
        is Maz.WallType.StairDown -> true
        is Maz.WallType.Decoration -> true // decorations don't block movement
    }

    companion object {
        /**
         * Create a [DungeonState] from parsed level data.
         *
         * 1. Copies the [Maz] grid into mutable [DungeonSquare]s
         * 2. Distributes items from [Inf.items] into the appropriate squares:
         *    - pos 0-3 → floor quadrants
         *    - pos 8 → wall niche (assigned to the wall side that has a Decoration type)
         */
        fun fromInf(inf: Inf): DungeonState {
            val primarySubLevel = inf.subLevels.first()
            val levelNumber = primarySubLevel.level

            // Build the mutable grid from the Maz template
            val maz = primarySubLevel.maz
            val grid = Array(maz.width * maz.height) { index ->
                val template = maz.squares[index]
                DungeonSquare(
                    x = template.x,
                    y = template.y,
                    north = template.north,
                    east = template.east,
                    south = template.south,
                    west = template.west,
                )
            }

            val state = DungeonState(
                levelNumber = levelNumber,
                subLevels = inf.subLevels,
                grid = grid,
                flags = FlagState(),
            )

            // Distribute items into squares
            for (item in inf.items) {
                if (item.level != levelNumber) continue
                if (item.location.x < 0 || item.location.y < 0) continue
                if (item.location.x >= maz.width || item.location.y >= maz.height) continue

                val dungeonItem = DungeonItem.fromItem(item)
                val quadrant = FloorQuadrant.fromPos(item.pos)

                if (quadrant != null) {
                    // pos 0-3 → floor quadrant
                    state.addFloorItem(item.location.x, item.location.y, quadrant, dungeonItem)
                } else if (item.pos == 8) {
                    // pos 8 → wall niche: find which wall side has a Decoration
                    val square = state[item.location.x, item.location.y]
                    val nicheSide = findNicheWallSide(square)
                    if (nicheSide != null) {
                        state.addNicheItem(item.location.x, item.location.y, nicheSide, dungeonItem)
                    }
                }
                // pos 4-7 (inventory/equipment slots) are not placed on the grid
            }

            return state
        }

        /**
         * Find which wall side of a square has a Decoration (alcove/niche).
         * Returns the first decoration wall side found, or null if none.
         */
        private fun findNicheWallSide(square: DungeonSquare): WallSide? {
            if (square.north is Maz.WallType.Decoration) return WallSide.NORTH
            if (square.east is Maz.WallType.Decoration) return WallSide.EAST
            if (square.south is Maz.WallType.Decoration) return WallSide.SOUTH
            if (square.west is Maz.WallType.Decoration) return WallSide.WEST
            return null
        }
    }
}
