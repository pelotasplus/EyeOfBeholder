package pl.pelotasplus.eyeofbeholder.data.repository

import co.touchlab.kermit.Logger
import pl.pelotasplus.eyeofbeholder.data.model.Maz
import pl.pelotasplus.eyeofbeholder.data.model.ViewPort

interface ViewConeRepository {
    suspend fun loadVmp(name: String): Result<ViewPort>
}

class ViewConeRepositoryImpl(
    private val vmpRepository: VmpRepository,
    private val vcnRepository: VcnRepository,
    private val palRepository: PalRepository,
    private val mazRepository: MazRepository,
    private val infRepository: InfRepository
) : ViewConeRepository {
    override suspend fun loadVmp(name: String): Result<ViewPort> {
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

//            viewPort.drawBackdrop(
//                vmp = vmp,
//                vcn = vcn,
//                pal = pal
//            )

            val posX = 15
            val posY = 14

            // A
            val positionA = maz[posX - 3, posY - 3]
            println("XXX positionA $positionA")
            if (positionA.east is Maz.WallType.FixedWall) {
                viewPort.drawWall(
                    wallType = positionA.east.wallType,
                    wallPosition = 0,
                    vmp = vmp,
                    vcn = vcn,
                    pal = pal
                )
            }

            // B
            val positionB = maz[posX - 2, posY - 3]
            println("XXX positionB $positionB")
            if (positionB.east is Maz.WallType.FixedWall) {
                viewPort.drawWall(
                    wallType = positionB.east.wallType,
                    wallPosition = 1,
                    vmp = vmp,
                    vcn = vcn,
                    pal = pal
                )
            }

            // C
            val positionC = maz[posX - 1, posY - 3]
            println("XXX positionC $positionC")
            if (positionC.east is Maz.WallType.FixedWall) {
                viewPort.drawWall(
                    wallType = positionC.east.wallType,
                    wallPosition = 2,
                    vmp = vmp,
                    vcn = vcn,
                    pal = pal
                )
            }

            // D
            val positionD = maz[posX, posY - 3]
            println("XXX positionD $positionD")
            if (positionD.south is Maz.WallType.FixedWall) {
                viewPort.drawWall(
                    wallType = positionD.south.wallType,
                    wallPosition = 8,
                    vmp = vmp,
                    vcn = vcn,
                    pal = pal
                )
            }

            // E
            val positionE = maz[posX + 1, posY - 3]
            println("XXX positionE $positionE")
            if (positionE.west is Maz.WallType.FixedWall) {
                viewPort.drawWall(
                    wallType = positionE.west.wallType,
                    wallPosition = 3,
                    vmp = vmp,
                    vcn = vcn,
                    pal = pal
                )
            }

            // F
            val positionF = maz[posX + 2, posY - 3]
            println("XXX positionF $positionF")
            if (positionF.west is Maz.WallType.FixedWall) {
                viewPort.drawWall(
                    wallType = positionF.west.wallType,
                    wallPosition = 4,
                    vmp = vmp,
                    vcn = vcn,
                    pal = pal
                )
            }

            // G
            val positionG = maz[posX + 3, posY - 3]
            println("XXX positionG $positionG")
            if (positionG.west is Maz.WallType.FixedWall) {
                viewPort.drawWall(
                    wallType = positionG.west.wallType,
                    wallPosition = 5,
                    vmp = vmp,
                    vcn = vcn,
                    pal = pal
                )
            }

            // H
            val positionH = maz[posX - 2, posY - 2]
            println("XXX positionH $positionH")
            if (positionG.west is Maz.WallType.FixedWall) {
                viewPort.drawWall(
                    wallType = positionG.west.wallType,
                    wallPosition = 5,
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
