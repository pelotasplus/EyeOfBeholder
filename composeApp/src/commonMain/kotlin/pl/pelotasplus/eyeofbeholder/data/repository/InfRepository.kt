package pl.pelotasplus.eyeofbeholder.data.repository

import pl.pelotasplus.eyeofbeholder.data.model.Inf

interface InfRepository {
    suspend fun loadInf(name: String): Result<Inf>

    suspend fun getAllInfNames(): Result<List<String>>
}

class InfRepositoryImpl(
    private val resourceRepository: ResourceRepository
) : InfRepository {

    override suspend fun loadInf(name: String): Result<Inf> {
        return runCatching {
            val bytes = resourceRepository.readResource("files/$name")
            Inf(
                name = name,
                data = bytes
            )
        }
    }

    override suspend fun getAllInfNames(): Result<List<String>> {
        return resourceRepository.listResources(".INF")
    }
}
