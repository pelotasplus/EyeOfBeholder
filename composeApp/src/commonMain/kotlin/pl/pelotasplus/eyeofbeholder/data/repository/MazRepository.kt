package pl.pelotasplus.eyeofbeholder.data.repository

import pl.pelotasplus.eyeofbeholder.data.model.Maz

interface MazRepository {
    suspend fun loadMaz(name: String): Result<Maz>
}

class MazRepositoryImpl(
    private val resourceRepository: ResourceRepository
) : MazRepository {
    override suspend fun loadMaz(name: String): Result<Maz> {
        return runCatching {
            val bytes = resourceRepository.readResource("files/$name")

            Maz(name = name)
        }
    }

    companion object {
    }
}


