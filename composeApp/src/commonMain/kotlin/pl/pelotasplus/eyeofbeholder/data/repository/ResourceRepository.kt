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
        "FINALE_0.PAL",
        "FINALE_1.PAL",
        "FINALE_2.PAL",
        "FINALE_3.PAL",
        "FINALE_4.PAL",
        "FINALE_5.PAL",
        "FINALE_6.PAL",
        "FINALE_7.PAL",
        "FOREST.PAL",
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
