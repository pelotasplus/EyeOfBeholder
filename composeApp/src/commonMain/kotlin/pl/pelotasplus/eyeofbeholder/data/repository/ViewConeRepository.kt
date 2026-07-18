package pl.pelotasplus.eyeofbeholder.data.repository

import co.touchlab.kermit.Logger
import pl.pelotasplus.eyeofbeholder.data.model.Cps
import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.Inf
import pl.pelotasplus.eyeofbeholder.data.model.Item
import pl.pelotasplus.eyeofbeholder.data.model.Maz
import pl.pelotasplus.eyeofbeholder.data.model.MonsterInstance
import pl.pelotasplus.eyeofbeholder.data.model.SubLevel
import pl.pelotasplus.eyeofbeholder.data.model.ViewPort
import pl.pelotasplus.eyeofbeholder.data.model.WallSet
import pl.pelotasplus.eyeofbeholder.data.model.cutFrame
import pl.pelotasplus.eyeofbeholder.data.model.getWall
import pl.pelotasplus.eyeofbeholder.data.model.monsterBlockRows
import pl.pelotasplus.eyeofbeholder.data.model.monsterFrameRects
import pl.pelotasplus.eyeofbeholder.data.model.monsterFrameSelect
import pl.pelotasplus.eyeofbeholder.data.model.monsterPosIndex
import pl.pelotasplus.eyeofbeholder.data.model.viewSlots
import kotlin.math.abs

/**
 * Orchestrates level loading and 3D viewport rendering.
 *
 * This is the highest-level repository, combining data from all other
 * repositories to produce the final rendered viewport image.
 *
 * ## Level loading flow
 * [loadLevel] → loads items from ITEM.DAT, then delegates to [InfRepository]
 * which cascades to MAZ, VMP, VCN, PAL, DEC, CPS repositories.
 *
 * ## Rendering flow ([renderPosition])
 * Given a player position (x, y), facing direction, and sublevel:
 * 1. Draw the backdrop (floor/ceiling) from VMP+VCN
 * 2. For each of 25 wall positions (back-to-front):
 *    a. Transform relative coordinates by player direction
 *    b. Look up the maze square and wall type
 *    c. Draw wall/door/stairs/decoration as appropriate
 *    d. Draw any items at that location
 * 3. Return the completed ViewPort pixel buffer
 *
 * ## Wall type dispatch
 * - NoWall → skip (open passage)
 * - FixedWall → draw VCN wall tiles
 * - Door → draw door frame + CPS door panel (± button)
 * - StairUp/Down → draw stair tiles
 * - Decoration → look up decoration, optionally draw base wall, then overlay
 *   (specialType 5 = stuck door gets special treatment)
 */
interface ViewConeRepository {
    suspend fun loadLevel(name: String): Result<Inf>

    suspend fun renderPosition(
        items: List<Item>,
        monsters: List<MonsterInstance>,
        sublevel: SubLevel,
        playerX: Int,
        playerY: Int,
        direction: Direction
    ): Result<ViewPort>
}

class ViewConeRepositoryImpl(
    private val infRepository: InfRepository,
    private val itemsRepository: ItemsRepository,
    private val cpsRepository: CpsRepository,
) : ViewConeRepository {

    private var smallItemIcons: Cps? = null
    private var largeItemIcons: Cps? = null
    private val monsterFrameCache = mutableMapOf<String, List<Cps.ItemIcon>>()

    private suspend fun getSmallItemIcons(): Cps {
        return smallItemIcons ?: cpsRepository.loadCps("ITEMS1.CPS").getOrThrow().also {
            smallItemIcons = it
        }
    }

    private suspend fun getLargeItemIcons(): Cps {
        return largeItemIcons ?: cpsRepository.loadCps("ITEML1.CPS").getOrThrow().also {
            largeItemIcons = it
        }
    }

    override suspend fun renderPosition(
        items: List<Item>,
        monsters: List<MonsterInstance>,
        sublevel: SubLevel,
        playerX: Int,
        playerY: Int,
        direction: Direction
    ): Result<ViewPort> {
        Logger.d(TAG) { "Render position $playerX x $playerY level ${sublevel.level}"}

        val viewPort = ViewPort(
            vmp = sublevel.vmp,
            vcn = sublevel.vcn,
            palette = sublevel.palette
        )
        viewPort.drawBackdrop()

        val smallIcons = getSmallItemIcons()
        val largeIcons = getLargeItemIcons()
        val monsterFrames = loadMonsterFrames(sublevel)

        // Data-driven wall rendering using the viewSlots table
        viewSlots.forEachIndexed { wallPosition, slot ->
            // Monsters of a depth row draw after that row's walls and before
            // the next (nearer) row's walls, so closer walls occlude them.
            when (wallPosition) {
                11 -> drawMonstersAtRow(-3, viewPort, monsters, monsterFrames, sublevel, playerX, playerY, direction)
                18 -> drawMonstersAtRow(-2, viewPort, monsters, monsterFrames, sublevel, playerX, playerY, direction)
                23 -> drawMonstersAtRow(-1, viewPort, monsters, monsterFrames, sublevel, playerX, playerY, direction)
            }
            // Transform coordinates based on player direction
            val (dx, dy) = direction.transformCoordinates(
                slot.relativeX,
                slot.relativeY
            )

            // Calculate actual maze position
            val mazX = playerX + dx
            val mazY = playerY + dy

            // Bounds check
            if (mazX !in 0 until sublevel.maz.width || mazY !in 0 until sublevel.maz.height) {
                return@forEachIndexed
            }

            val matchingItems = items.filter {
                it.level == sublevel.level &&
                        it.location.x == mazX &&
                        it.location.y == mazY
            }

            // Get the maze square at the calculated position
            val square = sublevel.maz[mazX, mazY]

            // Transform wall side based on player direction
            val actualWallSide = direction.transformWallSide(slot.wallSide)
            val wallType = square.getWall(actualWallSide)

            Logger.d(TAG) { "Wall wallPosition $wallPosition type: $wallType for $mazX x $mazY originalSide ${slot.wallSide} actualWallSide $actualWallSide" }
            Logger.d(TAG) { "Matching items $matchingItems" }

            when (wallType) {
                is Maz.WallType.Decoration -> {
                    val levelDecoration =
                        sublevel.decorations.find { it.decorationWallIndex == wallType.decorationWallIndex }
                    Logger.d(TAG) { "Wall wallPosition $wallPosition matching decoration $levelDecoration" }
                    if (levelDecoration == null) {
                        Logger.e(TAG) { "Decoration not found for index ${wallType.decorationWallIndex}" }
                        return@forEachIndexed
                    }

                    // stuck door?
                    if (levelDecoration.specialType == 5) {
                        viewPort.drawDoor(
                            wallPosition = wallPosition,
                            door = sublevel.doors[0],
                            showButton = false,
                            stuckDoor = true
                        )
                    } else if ((levelDecoration.wallType - 1) >= 0) {
                        viewPort.drawWall(levelDecoration.wallType - 1, wallPosition)
                    }

                    viewPort.drawDecoration(
                        decoration = levelDecoration,
                        wallPosition = wallPosition,
                    )
                }

                is Maz.WallType.Door -> {
                    viewPort.drawDoor(
                        wallPosition = wallPosition,
                        door = sublevel.doors[wallType.doorIndex],
                        showButton = wallType.hasButton
                    )
                }

                is Maz.WallType.FixedWall -> {
                    viewPort.drawWall(wallType.wallType, wallPosition)
                }

                Maz.WallType.NoWall -> {
                    // no-wall to render
                }

                Maz.WallType.StairDown -> {
                    viewPort.drawWall(WallSet.STAIRS_DOWN, wallPosition)
                }

                Maz.WallType.StairUp -> {
                    viewPort.drawWall(WallSet.STAIRS_UP, wallPosition)
                }
            }

            for (item in matchingItems) {
                Logger.d(TAG) { "drawItem ${item.nameUnidentified} icon=${item.icon} type=${item.type} pos=${item.pos}" }
                if (item.pos != 8 && item.pos >= 4) {
                    Logger.w(TAG) { "Unexpected item position: ${item.pos}, skipping" }
                    continue
                }
                viewPort.drawItem(
                    wallPosition = wallPosition,
                    smallIcons = smallIcons,
                    largeIcons = largeIcons,
                    iconIdx = item.icon,
                    iconPosition = item.pos
                )
            }
        }

        val matchingItems = items.filter {
            it.level == sublevel.level &&
                    it.location.x == playerX &&
                    it.location.y == playerY
        }
        for (item in matchingItems) {
            Logger.d(TAG) { "drawItem ${item.nameUnidentified} icon=${item.icon} type=${item.type} pos=${item.pos}" }
            if (item.pos != 8 && item.pos >= 4) {
                Logger.w(TAG) { "Unexpected item position: ${item.pos}, skipping" }
                continue
            }
            viewPort.drawItem(
                wallPosition = 21,
                smallIcons = smallIcons,
                largeIcons = largeIcons,
                iconIdx = item.icon,
                iconPosition = item.pos
            )
        }

        return Result.success(viewPort)
    }

    override suspend fun loadLevel(
        name: String,
    ): Result<Inf> {
        val items = itemsRepository.loadItems().getOrThrow()
        return infRepository.loadInf(name.replace(".MAZ", ".INF"), items)
    }

    /** Loads and caches the 6 near-size poses for each of the sublevel's sprite sheets. */
    private suspend fun loadMonsterFrames(sublevel: SubLevel): List<List<Cps.ItemIcon>> {
        return sublevel.monsterGfx.map { gfx ->
            val cpsName = gfx.name.filter { it.code in 33..126 }.uppercase() + ".CPS"
            monsterFrameCache.getOrPut(cpsName) {
                cpsRepository.loadCps(cpsName)
                    .map { cps -> monsterFrameRects[gfx.sizeClass].map { cps.cutFrame(it) } }
                    .onFailure { Logger.e(TAG) { "Failed to load monster sheet $cpsName: $it" } }
                    .getOrDefault(emptyList())
            }
        }
    }

    private fun drawMonstersAtRow(
        relativeY: Int,
        viewPort: ViewPort,
        monsters: List<MonsterInstance>,
        monsterFrames: List<List<Cps.ItemIcon>>,
        sublevel: SubLevel,
        playerX: Int,
        playerY: Int,
        direction: Direction,
    ) {
        val playerDir = direction.ordinal

        for (block in monsterBlockRows.getValue(relativeY)) {
            val (dx, dy) = direction.transformCoordinates(block.relativeX, block.relativeY)
            val mazX = playerX + dx
            val mazY = playerY + dy

            val monstersHere = monsters
                .filter { it.subLevelIndex == sublevel.index && it.x == mazX && it.y == mazY }
                .sortedBy { viewRelativePos(playerDir, it.pos) }

            for (monster in monstersHere) {
                val frames = monsterFrames.getOrNull(monster.gfxIndex) ?: continue
                val frameSelect = monsterFrameSelect[(playerDir shl 2) or (monster.direction and 3)]
                val frame = frames.getOrNull(abs(frameSelect) - 1) ?: continue

                Logger.d(TAG) { "drawMonster type=${monster.type} at ($mazX, $mazY) pos=${monster.pos} dir=${monster.direction}" }

                viewPort.drawMonster(
                    frame = frame,
                    blockIndex = block.blockIndex,
                    subPosition = viewRelativePos(playerDir, monster.pos),
                    mirrored = frameSelect < 0,
                    scaleSteps = block.scaleSteps,
                )
            }
        }
    }

    /** Rotates an absolute sub-position into a view-relative one; 4 = center stays put. */
    private fun viewRelativePos(playerDir: Int, pos: Int): Int =
        if (pos == 4) 4 else monsterPosIndex[playerDir * 4 + pos]

    companion object {
        private const val TAG = "ViewConeRepository"
    }
}
