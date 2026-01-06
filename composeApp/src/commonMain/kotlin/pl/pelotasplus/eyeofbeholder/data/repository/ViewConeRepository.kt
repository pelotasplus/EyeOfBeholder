package pl.pelotasplus.eyeofbeholder.data.repository

import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.Inf
import pl.pelotasplus.eyeofbeholder.data.model.SubLevel
import pl.pelotasplus.eyeofbeholder.data.model.ViewPort
import pl.pelotasplus.eyeofbeholder.data.model.category
import pl.pelotasplus.eyeofbeholder.data.model.getWall
import pl.pelotasplus.eyeofbeholder.data.model.toRenderWallType
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

            // Apply type filtering if specified
            if (mapping.acceptedTypes != null &&
                wallType.category() !in mapping.acceptedTypes
            ) {
                return@forEachIndexed
            }

            // Get renderable wall type
            val renderWallType = wallType.toRenderWallType() ?: return@forEachIndexed

            // Draw the wall using existing rendering infrastructure
            viewPort.drawWall(
                wallType = renderWallType,
                wallPosition = wallPosition,
                vmp = sublevel.vmp,
                vcn = sublevel.vcn,
                pal = sublevel.palette
            )
        }

        return Result.success(viewPort)
    }

    override suspend fun loadLevel(
        name: String,
    ): Result<Inf> {
        return infRepository.loadInf(name.replace(".MAZ", ".INF"))
    }
}
