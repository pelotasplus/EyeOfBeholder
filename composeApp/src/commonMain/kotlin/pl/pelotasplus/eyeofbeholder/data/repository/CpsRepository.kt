package pl.pelotasplus.eyeofbeholder.data.repository

import pl.pelotasplus.eyeofbeholder.data.ByteReader
import pl.pelotasplus.eyeofbeholder.data.model.Cps

interface CpsRepository {
    suspend fun loadCps(name: String): Result<Cps>

    suspend fun getAllCpsNames(): Result<List<String>>
}

class CpsRepositoryImpl(
    private val resourceRepository: ResourceRepository
) : CpsRepository {

    override suspend fun loadCps(name: String): Result<Cps> {
        return runCatching {
            val bytes = resourceRepository.decompressResource("files/$name")
            val reader = ByteReader(bytes)

            Cps(
                name = name,
                width = IMAGE_WIDTH,
                height = IMAGE_HEIGHT,
                pixels = reader.readRemaining().map { it.toInt() }.toList()
            )
        }
    }

    override suspend fun getAllCpsNames(): Result<List<String>> {
        return resourceRepository.listResources(".CPS")
    }

    companion object {
        private const val IMAGE_WIDTH = 320
        private const val IMAGE_HEIGHT = 200
    }
}
