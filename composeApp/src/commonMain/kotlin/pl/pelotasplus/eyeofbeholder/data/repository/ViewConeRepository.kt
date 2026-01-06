package pl.pelotasplus.eyeofbeholder.data.repository

import co.touchlab.kermit.Logger
import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.Maz
import pl.pelotasplus.eyeofbeholder.data.model.ViewPort
import pl.pelotasplus.eyeofbeholder.data.model.category
import pl.pelotasplus.eyeofbeholder.data.model.getWall
import pl.pelotasplus.eyeofbeholder.data.model.toRenderWallType
import pl.pelotasplus.eyeofbeholder.data.model.wallPositionMappings

interface ViewConeRepository {
    suspend fun loadVmp(
        name: String,
        playerX: Int,
        playerY: Int,
        direction: Direction = Direction.SOUTH
    ): Result<ViewPort>
}

class ViewConeRepositoryImpl(
    private val infRepository: InfRepository
) : ViewConeRepository {
    override suspend fun loadVmp(
        name: String,
        playerX: Int,
        playerY: Int,
        direction: Direction
    ): Result<ViewPort> {
        return runCatching {
            val inf = infRepository.loadInf(name.replace(".MAZ", ".INF")).getOrThrow()
            Logger.d(TAG) { "INF $inf" }

            val sublevel = inf.subLevels.first()

            val viewPort = ViewPort()

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
            viewPort
        }
    }

    companion object {
        private const val TAG = "ViewConeRepository"
    }
}
