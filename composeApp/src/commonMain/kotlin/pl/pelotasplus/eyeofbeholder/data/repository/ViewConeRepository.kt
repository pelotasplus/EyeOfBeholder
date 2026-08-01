package pl.pelotasplus.eyeofbeholder.data.repository

import co.touchlab.kermit.Logger
import pl.pelotasplus.eyeofbeholder.data.model.Cps
import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.Inf
import pl.pelotasplus.eyeofbeholder.data.model.Item
import pl.pelotasplus.eyeofbeholder.data.model.ItemIconId
import pl.pelotasplus.eyeofbeholder.data.model.Maz
import pl.pelotasplus.eyeofbeholder.data.model.WallSide
import pl.pelotasplus.eyeofbeholder.data.model.DistanceFromParty
import pl.pelotasplus.eyeofbeholder.data.model.MonsterInstance
import pl.pelotasplus.eyeofbeholder.data.model.SubLevel
import pl.pelotasplus.eyeofbeholder.data.model.ViewPort
import pl.pelotasplus.eyeofbeholder.data.model.WallSet
import pl.pelotasplus.eyeofbeholder.data.model.cutFrame
import pl.pelotasplus.eyeofbeholder.data.model.getWall
import pl.pelotasplus.eyeofbeholder.data.model.itemScaleSteps
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
            // Items and monsters of a depth row draw after that row's walls
            // and before the next (nearer) row's walls, so closer walls
            // occlude them. Items draw first so monsters stand in front.
            when (wallPosition) {
                11, 18, 23 -> {
                    val relY = if (wallPosition == 11) -3 else if (wallPosition == 18) -2 else -1
                    drawItemsAtRow(relY, viewPort, items, smallIcons, largeIcons, sublevel, playerX, playerY, direction)
                    drawMonstersAtRow(relY, viewPort, monsters, monsterFrames, sublevel, playerX, playerY, direction)
                }
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

            // Get the maze square at the calculated position
            val square = sublevel.maz[mazX, mazY]

            // Transform wall side based on player direction
            val actualWallSide = direction.transformWallSide(slot.wallSide)
            val wallType = square.getWall(actualWallSide)

            // A slot's SOUTH wall is the far face of its square; the others are
            // the faces turned towards the party.
            val distance = if (slot.wallSide == WallSide.SOUTH) {
                DistanceFromParty.farSideOfSquare(slot.relativeX, slot.relativeY)
            } else {
                DistanceFromParty.nearSideOfSquare(slot.relativeX, slot.relativeY)
            }

            viewPort.at(distance) {
                when (wallType) {
                    is Maz.WallType.Decoration -> {
                        val levelDecoration = sublevel.decorations
                            .find { it.decorationWallIndex == wallType.decorationWallIndex }
                        Logger.d(TAG) { "Wall wallPosition $wallPosition matching decoration $levelDecoration" }
                        if (levelDecoration == null) {
                            Logger.e(TAG) { "Decoration not found for index ${wallType.decorationWallIndex}" }
                            return@at
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
                            door = sublevel.doors[wallType.doorIndex.value],
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
            }
        }

        // Items on the party's own square (visible block 16, dim 3): only the
        // two quadrants ahead of the party are visible; rear quadrants are
        // behind the camera and niche items beside it are never drawn.
        drawItemsAtBlock(
            viewPort, items, smallIcons, largeIcons, sublevel,
            mazX = playerX, mazY = playerY,
            blockIndex = ViewPort.OWN_BLOCK_INDEX, dim = 3,
            playerDir = direction.ordinal,
        )

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

    private fun drawItemsAtRow(
        relativeY: Int,
        viewPort: ViewPort,
        items: List<Item>,
        smallIcons: Cps,
        largeIcons: Cps,
        sublevel: SubLevel,
        playerX: Int,
        playerY: Int,
        direction: Direction,
    ) {
        val dim = when (relativeY) {
            -3 -> 0
            -2 -> 1
            else -> 2
        }

        for (block in monsterBlockRows.getValue(relativeY)) {
            val (dx, dy) = direction.transformCoordinates(block.relativeX, block.relativeY)
            viewPort.at(
                DistanceFromParty.standingOnSquare(block.relativeX, block.relativeY),
                hiddenByCloserThings = true,
            ) {
                drawItemsAtBlock(
                    viewPort, items, smallIcons, largeIcons, sublevel,
                    mazX = playerX + dx, mazY = playerY + dy,
                    blockIndex = block.blockIndex, dim = dim,
                    playerDir = direction.ordinal,
                )
            }
        }
    }

    private fun drawItemsAtBlock(
        viewPort: ViewPort,
        items: List<Item>,
        smallIcons: Cps,
        largeIcons: Cps,
        sublevel: SubLevel,
        mazX: Int,
        mazY: Int,
        blockIndex: Int,
        dim: Int,
        playerDir: Int,
    ) {
        val itemsHere = items.filter {
            it.level == sublevel.level && it.location.x == mazX && it.location.y == mazY
        }

        for (item in itemsHere) {
            Logger.d(TAG) { "drawItem ${item.nameUnidentified} icon=${item.icon} at ($mazX, $mazY) pos=${item.pos} block=$blockIndex" }

            when {
                item.pos == 8 -> {
                    // niche items are hidden when too far (dim 0) or on the own square (dim 3)
                    if (dim == 1 || dim == 2) {
                        sheetFor(item.icon, smallIcons, largeIcons)?.let { sheet ->
                            viewPort.drawNicheItem(sheet, item.icon, blockIndex, dim)
                        }
                    }
                }

                item.pos < 4 -> {
                    val quadrant = viewRelativePos(playerDir, item.pos)
                    val scaleSteps = itemScaleSteps[dim * 4 + quadrant]
                    if (scaleSteps.isVisible) {
                        sheetFor(item.icon, smallIcons, largeIcons)?.let { sheet ->
                            viewPort.drawFloorItem(sheet, item.icon, blockIndex, quadrant, scaleSteps)
                        }
                    }
                }

                else -> Logger.w(TAG) { "Unexpected item position: ${item.pos}, skipping" }
            }
        }
    }

    /**
     * Which sprite sheet an icon's shape lives in. The shape map decides
     * whether an icon is a small or a large item, and the two sizes are packed
     * into different files — cutting a small shape out of the large sheet
     * yields whatever else happens to sit at those coordinates.
     */
    private fun sheetFor(icon: ItemIconId, small: Cps, large: Cps): Cps? =
        when (small.locate(icon)) {
            is Cps.ShapeLocation.SmallItem -> small
            is Cps.ShapeLocation.LargeItem -> large
            Cps.ShapeLocation.NoShape -> null
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

                viewPort.at(
                    DistanceFromParty.standingOnSquare(block.relativeX, block.relativeY),
                    hiddenByCloserThings = true,
                ) {
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
    }

    /** Rotates an absolute sub-position into a view-relative one; 4 = center stays put. */
    private fun viewRelativePos(playerDir: Int, pos: Int): Int =
        if (pos == 4) 4 else monsterPosIndex[playerDir * 4 + pos]

    companion object {
        private const val TAG = "ViewConeRepository"
    }
}
