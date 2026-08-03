package pl.pelotasplus.eyeofbeholder.data.repository

import kotlinx.serialization.json.Json
import pl.pelotasplus.eyeofbeholder.data.model.Champion
import pl.pelotasplus.eyeofbeholder.data.model.GameState
import pl.pelotasplus.eyeofbeholder.data.model.PlayField
import pl.pelotasplus.eyeofbeholder.data.model.SavedGame

/**
 * Saving and loading a game, in our own format.
 *
 * The [SaveStore] underneath knows where bytes live and nothing else; this
 * knows what they mean. Reading the original game's files is
 * [OriginalSaveRepository]'s job and stays separate — one reads a format we do
 * not write, the other writes a format the original cannot read.
 */
interface SavedGameRepository {

    /** What is in each slot, for a list to draw. Absent slots are empty ones. */
    suspend fun saved(): Map<SaveSlot, SavedGame>

    suspend fun load(slot: SaveSlot): Result<SavedGame>

    suspend fun save(
        slot: SaveSlot,
        description: String,
        savedAt: Long,
        level: Int,
        subLevel: Int = 0,
        champions: List<Champion>,
        world: GameState,
        messages: List<PlayField.Message> = emptyList(),
    ): Result<SavedGame>

    suspend fun erase(slot: SaveSlot): Result<Unit>
}

class SavedGameRepositoryImpl(
    private val store: SaveStore,
) : SavedGameRepository {

    override suspend fun saved(): Map<SaveSlot, SavedGame> =
        store.written().mapNotNull { slot ->
            load(slot).getOrNull()?.let { slot to it }
        }.toMap()

    override suspend fun load(slot: SaveSlot): Result<SavedGame> = runCatching {
        val contents = store.read(slot) ?: error("Nothing saved in ${slot.name}")
        json.decodeFromString<SavedGame>(contents)
    }

    override suspend fun save(
        slot: SaveSlot,
        description: String,
        savedAt: Long,
        level: Int,
        subLevel: Int,
        champions: List<Champion>,
        world: GameState,
        messages: List<PlayField.Message>,
    ): Result<SavedGame> = runCatching {
        SavedGame(
            description = description,
            savedAt = savedAt,
            level = level,
            subLevel = subLevel,
            champions = champions,
            world = world.saved(),
            messages = messages,
        ).also { store.write(slot, json.encodeToString(it)) }
    }

    override suspend fun erase(slot: SaveSlot): Result<Unit> = runCatching {
        store.erase(slot)
    }

    private companion object {
        /**
         * Unknown keys are ignored so that a save written by a later build
         * still opens in an earlier one, minus whatever it did not know about.
         * Defaults are written out so a save can be read without them.
         *
         * Not pretty printed: a heavily played world is a quarter of a
         * megabyte with the whitespace in, and the web writes this into a
         * budget of about five. Exporting one to a file can afford to be
         * readable; storing it cannot.
         */
        val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
}
