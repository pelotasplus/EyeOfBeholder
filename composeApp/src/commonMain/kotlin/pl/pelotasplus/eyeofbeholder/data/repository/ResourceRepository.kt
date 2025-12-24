package pl.pelotasplus.eyeofbeholder.data.repository

import co.touchlab.kermit.Logger
import eyeofbeholder.composeapp.generated.resources.Res

interface ResourceRepository {
    suspend fun readResource(path: String): ByteArray

    suspend fun listResources(extension: String): Result<List<String>>
}

class ResourceRepositoryImpl() : ResourceRepository {
    private val TAG = "ResourceRepository"
    private val manifest = listOf(
        "AZURE.PAL",
        "AZURE1.CPS",
        "CRIMSON.PAL",
        "DUNG.PAL",
        "FINALE_0.PAL",
        "FINALE_1.PAL",
        "FINALE_2.PAL",
        "FINALE_3.PAL",
        "FINALE_4.PAL",
        "FINALE_5.PAL",
        "FINALE_6.PAL",
        "FINALE_7.PAL",
        "FOREST.PAL",
        "LEVEL1.INF",
        "LEVEL1.MAZ",
        "LEVEL10.INF",
        "LEVEL10.MAZ",
        "LEVEL11.INF",
        "LEVEL11.MAZ",
        "LEVEL12.INF",
        "LEVEL12.MAZ",
        "LEVEL13.INF",
        "LEVEL13.MAZ",
        "LEVEL14.INF",
        "LEVEL14.MAZ",
        "LEVEL15.INF",
        "LEVEL15.MAZ",
        "LEVEL16.INF",
        "LEVEL2.INF",
        "LEVEL2.MAZ",
        "LEVEL3.INF",
        "LEVEL3.MAZ",
        "LEVEL4.INF",
        "LEVEL4.MAZ",
        "LEVEL5.INF",
        "LEVEL5.MAZ",
        "LEVEL6.INF",
        "LEVEL6.MAZ",
        "LEVEL7.INF",
        "LEVEL7.MAZ",
        "LEVEL8.INF",
        "LEVEL8.MAZ",
        "LEVEL9.INF",
        "LEVEL9.MAZ",
        "MEZZ.PAL",
        "MEZZ1.CPS",
        "MEZZ2.CPS",
        "MINDFLAY.CPS",
        "MMOUTH1.CPS",
        "MMOUTH2.CPS",
        "OUTPORTS.CPS",
        "OUTTAKE.CPS",
        "PALETTE0.PAL",
        "PALETTE1.PAL",
        "PALETTE2.PAL",
        "PALETTE3.PAL",
        "PALETTE4.PAL",
        "PLAYFLD.CPS",
        "PORTALA.CPS",
        "PORTALB.CPS",
        "SALAMNDR.CPS",
        "SHRUNKEN.CPS",
        "SILVER.PAL",
        "SILVER1.CPS",
        "SILVER2.CPS",
        "SKELWAR.CPS",
        "SOUT1.CPS",
        "SOUT2.CPS",
        "SOUT3.CPS",
        "SOUT4.CPS",
        "SOUT5.CPS",
        "SPIDER.CPS",
        "STONEGIA.CPS",
        "STREET1.CPS",
        "STREET2.CPS",
        "SUICIDE.CPS",
        "TANGLOR.CPS",
        "THANKS.CPS",
        "THROWN.CPS",
        "WASP.CPS",
        "WESTWOOD.CPS",
        "WILLOWIS.CPS",
        "WINDING.CPS",
        "WOLF.CPS",
    )

    override suspend fun readResource(path: String): ByteArray {
        return Res.readBytes(path)
    }

    override suspend fun listResources(extension: String): Result<List<String>> {
        return runCatching {
            manifest.filter { it.endsWith(extension, ignoreCase = true) }
                .also { Logger.d(TAG) { "listResources: $extension got $it" } }
        }
    }
}
