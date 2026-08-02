package pl.pelotasplus.eyeofbeholder.data.repository

import pl.pelotasplus.eyeofbeholder.data.model.SavedGame

/**
 * Reads the original game's own save files.
 *
 * These are how a party comes into being at all before there is a screen to
 * roll one up on: `EOBDATA0.SAV` ships with the game and holds the quick start
 * party, so there is somebody to play as from the first run.
 *
 * Only reading lives here. Our own saves will be written in our own format,
 * which can grow as more of the game is modelled rather than being pinned to
 * the original's fixed record.
 */
interface SavedGameRepository {
    suspend fun loadSavedGame(name: String): Result<SavedGame>
}

class SavedGameRepositoryImpl(
    private val resourceRepository: ResourceRepository,
) : SavedGameRepository {

    override suspend fun loadSavedGame(name: String): Result<SavedGame> = runCatching {
        SavedGame.read(resourceRepository.readResource("files/$name"))
    }

    companion object {
        /** The party the game ships with, for anyone who does not want to roll one. */
        const val QUICK_START = "EOBDATA0.SAV"
    }
}
