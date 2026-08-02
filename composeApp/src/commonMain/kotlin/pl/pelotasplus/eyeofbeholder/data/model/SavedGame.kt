package pl.pelotasplus.eyeofbeholder.data.model

import kotlinx.serialization.Serializable

/**
 * A game as we save it, which is not how the original saved one.
 *
 * [OriginalSave] reads the game's own `EOBDATA*.SAV`, a fixed 46,891-byte
 * record from 1991. Writing that back would pin us to what it had room for,
 * and we already model things it did not. So ours is JSON with a [version] on
 * the front: readable when something goes wrong, and migratable when the shape
 * changes.
 *
 * What is saved is the world as the party made it — where they are, who they
 * are, what they have set off and what they have changed — and nothing that
 * can be read back out of the game files. The mazes are not here: they are
 * loaded from disk and the walls a script moved are kept as [ChangedWall]s on
 * top.
 */
@Serializable
data class SavedGame(
    val version: Int = VERSION,
    /** What the player typed, or what the autosave called itself. */
    val description: String,
    /** Milliseconds since the epoch, for showing which save is the newest. */
    val savedAt: Long,
    /** Which level file the party are standing in. */
    val level: Int,
    val champions: List<Champion>,
    val world: SavedWorld,
) {
    companion object {
        /**
         * Bumped whenever an older save can no longer be read as written.
         * Adding a field with a default does not need it; changing what a
         * field means does.
         */
        const val VERSION = 1
    }
}

/** The parts of a [GameState] that the game files cannot say. */
@Serializable
data class SavedWorld(
    val party: PartyState,
    val monsters: List<MonsterInstance>,
    val flags: GameFlags,
    /** Each level as the party left it, so going back finds it that way. */
    val leftBehind: Map<Int, List<MonsterInstance>>,
    val changedWalls: List<ChangedWall>,
)

/**
 * One face a script has changed.
 *
 * A map keyed by the face would say the same thing, but JSON keys are strings
 * and a three-part key would have to be flattened into one and parsed back —
 * so the key is written out as a value instead.
 */
@Serializable
data class ChangedWall(
    val level: Int,
    val at: Location,
    val side: WallSide,
    val to: WallByte,
)
