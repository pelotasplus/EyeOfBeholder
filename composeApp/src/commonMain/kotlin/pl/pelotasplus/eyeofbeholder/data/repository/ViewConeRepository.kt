package pl.pelotasplus.eyeofbeholder.data.repository

import co.touchlab.kermit.Logger
import pl.pelotasplus.eyeofbeholder.data.model.Cps
import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.Inf
import pl.pelotasplus.eyeofbeholder.data.model.Item
import pl.pelotasplus.eyeofbeholder.data.model.Maz
import pl.pelotasplus.eyeofbeholder.data.model.SubLevel
import pl.pelotasplus.eyeofbeholder.data.model.ViewPort
import pl.pelotasplus.eyeofbeholder.data.model.getWall
import pl.pelotasplus.eyeofbeholder.data.model.wallPositionMappings

interface ViewConeRepository {
    suspend fun loadLevel(name: String): Result<Inf>

    suspend fun renderPosition(
        items: List<Item>,
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

    private val viewPort = ViewPort()
    private var itemIconsCps: Cps? = null

    private suspend fun getItemIconsCps(): Cps {
        return itemIconsCps ?: cpsRepository.loadCps("ITEMS1.CPS").getOrThrow().also {
            itemIconsCps = it
        }
    }

    override suspend fun renderPosition(
        items: List<Item>,
        sublevel: SubLevel,
        playerX: Int,
        playerY: Int,
        direction: Direction
    ): Result<ViewPort> {
        Logger.d(TAG) { "Render position $playerX x $playerY level ${sublevel.level}"}

        viewPort.drawBackdrop(
            vmp = sublevel.vmp,
            vcn = sublevel.vcn,
            pal = sublevel.palette
        )

        val smallIcons = getItemIconsCps()

        // Data-driven wall rendering using wallPositionMappings
        wallPositionMappings.forEachIndexed { wallPosition, mapping ->
            // Transform coordinates based on player direction
            val (dx, dy) = direction.transformCoordinates(
                mapping.relativeX,
                mapping.relativeY
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
            val actualWallSide = direction.transformWallSide(mapping.wallSide)
            val wallType = square.getWall(actualWallSide)

            Logger.d(TAG) { "Wall wallPosition $wallPosition type: $wallType for $mazX x $mazY originalSide ${mapping.wallSide} actualWallSide $actualWallSide" }
            Logger.d(TAG) { "Matching items $matchingItems" }

            when (wallType) {
                is Maz.WallType.Decoration -> {
                    val levelDecoration =
                        sublevel.decorations.find { it.wallIndex == wallType.decorationWallIndex }
                    Logger.d(TAG) { "Wall wallPosition $wallPosition matching decoration $levelDecoration" }
                    if (levelDecoration == null) {
                        Logger.e(TAG) { "Decoration not found for index ${wallType.decorationWallIndex}" }
                        return@forEachIndexed
                    }

                    // stuck door?
                    if (levelDecoration.specialType == 5) {
                        viewPort.drawDoor(
                            wallPosition = wallPosition,
                            vmp = sublevel.vmp,
                            vcn = sublevel.vcn,
                            palette = sublevel.palette,
                            door = sublevel.doors[0],
                            showButton = false,
                            stuckDoor = true
                        )
                    } else if (levelDecoration.wallType != 0) {
                        viewPort.drawWall(
                            wallType = levelDecoration.wallType,
                            wallPosition = wallPosition,
                            vmp = sublevel.vmp,
                            vcn = sublevel.vcn,
                            pal = sublevel.palette
                        )
                    }

                    viewPort.drawDecoration(
                        decoration = levelDecoration,
                        palette = sublevel.palette,
                        wallPosition = wallPosition,
                    )
                }

                is Maz.WallType.DoorTypeOneWithButton -> {
                    viewPort.drawDoor(
                        wallPosition = wallPosition,
                        door = sublevel.doors[0],
                        vmp = sublevel.vmp,
                        vcn = sublevel.vcn,
                        palette = sublevel.palette,
                        showButton = true
                    )
                }

                is Maz.WallType.DoorTypeOneWithoutButton -> {
                    viewPort.drawDoor(
                        wallPosition = wallPosition,
                        door = sublevel.doors[0],
                        vmp = sublevel.vmp,
                        vcn = sublevel.vcn,
                        palette = sublevel.palette,
                        showButton = false
                    )
                }

                is Maz.WallType.DoorTypeTwoWithButton -> {
                    viewPort.drawDoor(
                        wallPosition = wallPosition,
                        door = sublevel.doors[1],
                        vmp = sublevel.vmp,
                        vcn = sublevel.vcn,
                        palette = sublevel.palette,
                        showButton = true
                    )
                }

                is Maz.WallType.DoorTypeTwoWithoutButton -> {
                    viewPort.drawDoor(
                        wallPosition = wallPosition,
                        door = sublevel.doors[1],
                        vmp = sublevel.vmp,
                        vcn = sublevel.vcn,
                        palette = sublevel.palette,
                        showButton = false
                    )
                }

                is Maz.WallType.FixedWall -> {
                    viewPort.drawWall(
                        wallType = wallType.wallType,
                        wallPosition = wallPosition,
                        vmp = sublevel.vmp,
                        vcn = sublevel.vcn,
                        pal = sublevel.palette
                    )
                }

                Maz.WallType.NoWall -> {
                    // no-wall to render
                }

                Maz.WallType.StairDown -> {
                    viewPort.drawStairsDown(
                        wallPosition = wallPosition,
                        vmp = sublevel.vmp,
                        vcn = sublevel.vcn,
                        pal = sublevel.palette
                    )
                }

                Maz.WallType.StairUp -> {
                    viewPort.drawStairsUp(
                        wallPosition = wallPosition,
                        vmp = sublevel.vmp,
                        vcn = sublevel.vcn,
                        pal = sublevel.palette
                    )
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
                    itemIconsCps = smallIcons,
                    palette = sublevel.palette,
                    iconIdx = item.icon,
                    iconPosition = item.pos
                )
            }
        }

        return Result.success(viewPort)
    }

    override suspend fun loadLevel(
        name: String,
    ): Result<Inf> {
        val items = itemsRepository.loadItems().getOrThrow()
        return infRepository.loadInf(name.replace(".MAZ", ".INF"), items)
    }

    companion object {
        private const val TAG = "ViewConeRepository"
    }
}
