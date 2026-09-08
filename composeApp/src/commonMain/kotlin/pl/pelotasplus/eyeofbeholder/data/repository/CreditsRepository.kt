package pl.pelotasplus.eyeofbeholder.data.repository

import pl.pelotasplus.eyeofbeholder.data.model.sequence.TheCredits

/** Who made the game, as the game itself lists them. */
interface CreditsRepository {
    suspend fun credits(): Result<List<TheCredits.Line>>
}

class CreditsRepositoryImpl(
    private val resourceRepository: ResourceRepository,
) : CreditsRepository {

    override suspend fun credits(): Result<List<TheCredits.Line>> = runCatching {
        TheCredits.read(resourceRepository.readResource(CREDITS))
    }

    private companion object {
        const val CREDITS = "files/CREDITS.TXT"
    }
}
