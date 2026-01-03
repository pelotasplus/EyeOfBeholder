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

            viewPort.drawBackdrop(
                vmp = vmp,
                vcn = vcn,
                pal = pal
            )

            val posX = 10
            val posY = 4

            if (true) {
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
            }

            if (true) {
                // B-south
                val positionB = maz[posX - 2, posY - 3]
                println("XXX positionB $positionB")
                if (positionB.south is Maz.WallType.FixedWall) {
                    viewPort.drawWall(
                        wallType = positionB.south.wallType,
                        wallPosition = 6,
                        vmp = vmp,
                        vcn = vcn,
                        pal = pal
                    )
                }

                // C
                val positionC = maz[posX - 1, posY - 3]
                println("XXX positionC $positionC")
                if (positionC.south is Maz.WallType.FixedWall) {
                    viewPort.drawWall(
                        wallType = positionC.south.wallType,
                        wallPosition = 7,
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
                if (positionE.south is Maz.WallType.FixedWall) {
                    viewPort.drawWall(
                        wallType = positionE.south.wallType,
                        wallPosition = 9,
                        vmp = vmp,
                        vcn = vcn,
                        pal = pal
                    )
                }

                // F-south
                val positionF = maz[posX + 2, posY - 3]
                println("XXX positionF $positionF")
                if (positionF.south is Maz.WallType.FixedWall) {
                    viewPort.drawWall(
                        wallType = positionF.south.wallType,
                        wallPosition = 10,
                        vmp = vmp,
                        vcn = vcn,
                        pal = pal
                    )
                }
            }

            if (true) {
                // H-south
                val positionH = maz[posX - 2, posY - 2]
                println("XXX positionH $positionH")
                if (positionH.east is Maz.WallType.FixedWall) {
                    viewPort.drawWall(
                        wallType = positionH.east.wallType,
                        wallPosition = 11,
                        vmp = vmp,
                        vcn = vcn,
                        pal = pal
                    )
                }

                // I
                val positionI = maz[posX - 1, posY - 2]
                println("XXX positionI $positionI")
                if (positionI.east is Maz.WallType.FixedWall) {
                    viewPort.drawWall(
                        wallType = positionI.east.wallType,
                        wallPosition = 12,
                        vmp = vmp,
                        vcn = vcn,
                        pal = pal
                    )
                }

                // K
                val positionK = maz[posX + 1, posY - 2]
                println("XXX positionK $positionK")
                if (positionK.west is Maz.WallType.FixedWall) {
                    viewPort.drawWall(
                        wallType = positionK.west.wallType,
                        wallPosition = 13,
                        vmp = vmp,
                        vcn = vcn,
                        pal = pal
                    )
                }

                // L
                val positionL = maz[posX + 2, posY - 2]
                println("XXX positionL $positionL")
                if (positionL.west is Maz.WallType.FixedWall) {
                    viewPort.drawWall(
                        wallType = positionL.west.wallType,
                        wallPosition = 14,
                        vmp = vmp,
                        vcn = vcn,
                        pal = pal
                    )
                }
            }

            if (true) {
                // I-south
                val positionI = maz[posX - 1, posY - 2]
                println("XXX positionI $positionI")
                if (positionI.south is Maz.WallType.FixedWall) {
                    viewPort.drawWall(
                        wallType = positionI.south.wallType,
                        wallPosition = 15,
                        vmp = vmp,
                        vcn = vcn,
                        pal = pal
                    )
                }

                // J-south
                val positionJ = maz[posX, posY - 2]
                println("XXX positionJ $positionJ")
                if (positionJ.south is Maz.WallType.FixedWall) {
                    viewPort.drawWall(
                        wallType = positionJ.south.wallType,
                        wallPosition = 16,
                        vmp = vmp,
                        vcn = vcn,
                        pal = pal
                    )
                }

                // K-south
                val positionK = maz[posX + 1, posY - 2]
                println("XXX positionK $positionK")
                if (positionK.south is Maz.WallType.FixedWall) {
                    viewPort.drawWall(
                        wallType = positionK.south.wallType,
                        wallPosition = 17,
                        vmp = vmp,
                        vcn = vcn,
                        pal = pal
                    )
                }
            }

            if (true) {
                // M-east
                val positionM = maz[posX - 1, posY - 1]
                println("XXX positionM $positionM")
                if (positionM.east is Maz.WallType.FixedWall) {
                    viewPort.drawWall(
                        wallType = positionM.east.wallType,
                        wallPosition = 18,
                        vmp = vmp,
                        vcn = vcn,
                        pal = pal
                    )
                }

                // O-west
                val positionO = maz[posX + 1, posY - 1]
                println("XXX positionO $positionO")
                if (positionO.west is Maz.WallType.FixedWall) {
                    viewPort.drawWall(
                        wallType = positionO.west.wallType,
                        wallPosition = 19,
                        vmp = vmp,
                        vcn = vcn,
                        pal = pal
                    )
                }
            }

            if (true) {
                // M-south
                val positionM = maz[posX - 1, posY - 1]
                println("XXX positionM $positionM")
                if (positionM.south is Maz.WallType.FixedWall) {
                    viewPort.drawWall(
                        wallType = positionM.south.wallType,
                        wallPosition = 20,
                        vmp = vmp,
                        vcn = vcn,
                        pal = pal
                    )
                }

                // N-south
                val positionN = maz[posX, posY - 1]
                println("XXX positionN $positionN")
                if (positionN.south is Maz.WallType.FixedWall) {
                    viewPort.drawWall(
                        wallType = positionN.south.wallType,
                        wallPosition = 21,
                        vmp = vmp,
                        vcn = vcn,
                        pal = pal
                    )
                }

                // O-west
                val positionO = maz[posX + 1, posY - 1]
                println("XXX positionO $positionO")
                if (positionO.south is Maz.WallType.FixedWall) {
                    viewPort.drawWall(
                        wallType = positionO.south.wallType,
                        wallPosition = 22,
                        vmp = vmp,
                        vcn = vcn,
                        pal = pal
                    )
                }
            }

            if (true) {
                // P-east
                val positionP = maz[posX - 1, posY]
                println("XXX positionP $positionP")
                if (positionP.east is Maz.WallType.FixedWall) {
                    viewPort.drawWall(
                        wallType = positionP.east.wallType,
                        wallPosition = 23,
                        vmp = vmp,
                        vcn = vcn,
                        pal = pal
                    )
                }

                // Q-west
                val positionQ = maz[posX + 1, posY]
                println("XXX positionQ $positionQ")
                if (positionQ.west is Maz.WallType.FixedWall) {
                    viewPort.drawWall(
                        wallType = positionQ.west.wallType,
                        wallPosition = 24,
                        vmp = vmp,
                        vcn = vcn,
                        pal = pal
                    )
                }
            }

            viewPort
        }
    }

    companion object {
        private const val TAG = "ViewConeRepository"
    }
}
