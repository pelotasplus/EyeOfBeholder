package pl.pelotasplus.eyeofbeholder.data.repository

import co.touchlab.kermit.Logger
import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.Inf
import pl.pelotasplus.eyeofbeholder.data.model.Maz
import pl.pelotasplus.eyeofbeholder.data.model.SubLevel
import pl.pelotasplus.eyeofbeholder.data.model.ViewPort
import pl.pelotasplus.eyeofbeholder.data.model.doorRenderData
import pl.pelotasplus.eyeofbeholder.data.model.getWall
import pl.pelotasplus.eyeofbeholder.data.model.wallPositionMappings

interface ViewConeRepository {
    suspend fun loadLevel(name: String): Result<Inf>

    suspend fun renderPosition(
        sublevel: SubLevel,
        playerX: Int,
        playerY: Int,
        direction: Direction
    ): Result<ViewPort>
}

class ViewConeRepositoryImpl(
    private val infRepository: InfRepository
) : ViewConeRepository {

    private val viewPort = ViewPort()

    override suspend fun renderPosition(
        sublevel: SubLevel,
        playerX: Int,
        playerY: Int,
        direction: Direction
    ): Result<ViewPort> {
        viewPort.drawBackdrop(
            vmp = sublevel.vmp,
            vcn = sublevel.vcn,
            pal = sublevel.palette
        )

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

            // Get the maze square at the calculated position
            val square = sublevel.maz[mazX, mazY]

            // Transform wall side based on player direction
            val actualWallSide = direction.transformWallSide(mapping.wallSide)
            val wallType = square.getWall(actualWallSide)
            Logger.d(TAG) { "Wall wallPosition $wallPosition type: $wallType for $mazX x $mazY originalSide ${mapping.wallSide} actualWallSide $actualWallSide" }

            when (wallType) {
                Maz.WallType.BottomPit -> TODO()
                is Maz.WallType.Decoration -> {
                    val levelDecoration =
                        sublevel.decorations.find { it.wallIndex == wallType.decorationWallIndex }
                    if (levelDecoration == null) {
                        "Decoration not found for index ${wallType.decorationWallIndex}"
                    }

                    if (levelDecoration != null) {
                        if (levelDecoration.wallType != 0) {
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
                }

                Maz.WallType.DoorPoleType1 -> {
                    // TODO()
                }

                Maz.WallType.DoorPoleType2 -> TODO()
                is Maz.WallType.DoorTypeOneWithButton -> {
//                    viewPort.drawDoorFrame(
//                        wallPosition = wallPosition,
//                        vmp = sublevel.vmp,
//                        vcn = sublevel.vcn,
//                        pal = sublevel.palette
//                    )
                }

                is Maz.WallType.DoorTypeOneWithoutButton -> {
//                    viewPort.drawDoorFrame(
//                        wallPosition = wallPosition,
//                        vmp = sublevel.vmp,
//                        vcn = sublevel.vcn,
//                        pal = sublevel.palette
//                    )
                }

                is Maz.WallType.DoorTypeTwoWithButton -> {
                    viewPort.drawDoorFrame(
                        wallPosition = wallPosition,
                        vmp = sublevel.vmp,
                        vcn = sublevel.vcn,
                        pal = sublevel.palette
                    )

                    val door = sublevel.doors[1]

                    println("XXX wallPosition $wallPosition")

                    val rectangleIndex = if (wallPosition >= 21) {
                        0
                    } else if (wallPosition >= 16) {
                        1
                    } else {
                        2
                    }

                    val renderData = doorRenderData[wallPosition]

//                    val deltaX = if (wallPosition >= 21) {
//                        52
//                    } else if (wallPosition >= 16) {
//                        60
//                    } else {
//                        72
//                    }
//
//                    val deltaY = if (wallPosition >= 21) {
//                        16
//                    } else if (wallPosition >= 16) {
//                        24
//                    } else {
//                        30
//                    }

                    val rectangle = door.rectangles[rectangleIndex]

                    val cps = door.cps

                    for (srcX in rectangle.x until rectangle.x + rectangle.w) {
                        for (srcY in rectangle.y until rectangle.y + rectangle.h) {
                            val pixel = cps.pixels[srcY * cps.width + srcX]
                            val color = sublevel.palette.colors[pixel]

                            val targetX = srcX - rectangle.x + renderData.offsetInViewPortX
                            val targetY = srcY - rectangle.y + renderData.offsetInViewPortY
                            viewPort.draw(targetX, targetY, color)
                        }
                    }
                }

                is Maz.WallType.DoorTypeTwoWithoutButton -> TODO()
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

                Maz.WallType.PidgeonHole -> TODO()
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

                Maz.WallType.StuckDoorType1 -> TODO()
                Maz.WallType.StuckDoorType2 -> TODO()
                Maz.WallType.Teleport -> {
                    // TODO()
                }
            }
        }

        return Result.success(viewPort)
    }

    override suspend fun loadLevel(
        name: String,
    ): Result<Inf> {
        return infRepository.loadInf(name.replace(".MAZ", ".INF"))
    }

    companion object {
        private const val TAG = "ViewConeRepository"
    }
}
