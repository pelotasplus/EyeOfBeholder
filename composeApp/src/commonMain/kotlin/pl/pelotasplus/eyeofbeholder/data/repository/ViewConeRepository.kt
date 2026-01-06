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
    private val vmpRepository: VmpRepository,
    private val vcnRepository: VcnRepository,
    private val palRepository: PalRepository,
    private val mazRepository: MazRepository,
    private val infRepository: InfRepository
) : ViewConeRepository {
    override suspend fun loadVmp(
        name: String,
        playerX: Int,
        playerY: Int,
        direction: Direction
    ): Result<ViewPort> {
        return runCatching {
            val maz = mazRepository.loadMaz(name).getOrThrow()
            Logger.d(TAG) { "MAZ $maz" }

            val inf = infRepository.loadInf(name.replace(".MAZ", ".INF")).getOrThrow()
            Logger.d(TAG) { "INF $inf" }

            val sublevel = inf.subLevels.first()

            val vmp = vmpRepository.loadVmp(sublevel.vmpData + ".VMP").getOrThrow()
            Logger.d(TAG) { "VMP $vmp" }

            val vcn = vcnRepository.loadVcn(sublevel.vmpData + ".VCN").getOrThrow()
            Logger.d(TAG) { "VCN $vcn" }

            val pal = palRepository.loadPal(sublevel.vmpData + ".PAL").getOrThrow()
            Logger.d(TAG) { "PAL $pal" }

            val viewPort = ViewPort()

            viewPort.drawBackdrop(
                vmp = vmp,
                vcn = vcn,
                pal = pal
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
                if (mazX !in 0 until maz.width || mazY !in 0 until maz.height) {
                    return@forEachIndexed
                }

                // Get the maze square at the calculated position
                val square = maz[mazX, mazY]

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
                    vmp = vmp,
                    vcn = vcn,
                    pal = pal
                )
            }
            viewPort
        }
    }

    companion object {
        private const val TAG = "ViewConeRepository"
    }
}
