package pl.pelotasplus.eyeofbeholder.data.model.dungeon

/**
 * Tracks all script flags for the game session.
 *
 * The scripting engine uses flags to track game progress: whether a lever
 * has been pulled, a quest completed, a door permanently opened, etc.
 * Scripts set/clear flags via [SetFlag]/[ClearFlag] opcodes and check them
 * via [Conditional.GetLevelFlag]/[Conditional.GetGlobalFlag] in [Eval] expressions.
 *
 * - **Level flags**: scoped to a single level, stored as a bitmask per level number
 * - **Global flags**: persist across all levels for the entire game session
 * - **Dialog result**: set after a dialog choice is made
 * - **Prevent rest**: blocks the party from resting (dangerous area)
 */
class FlagState {
    /** Per-level flag bitmask. Key = level number (1-16), Value = bitmask */
    private val levelFlags: MutableMap<Int, Int> = mutableMapOf()

    /** Global flag bitmask (single int, up to 32 flags) */
    private var globalFlags: Int = 0

    /** Dialog/event result flag */
    var dialogResult: Boolean = false

    /** Whether the party can rest */
    var preventRest: Boolean = false

    fun setLevelFlag(level: Int, flag: Int) {
        levelFlags[level] = (levelFlags[level] ?: 0) or (1 shl flag)
    }

    fun clearLevelFlag(level: Int, flag: Int) {
        levelFlags[level] = (levelFlags[level] ?: 0) and (1 shl flag).inv()
    }

    fun getLevelFlag(level: Int, flag: Int): Boolean {
        return ((levelFlags[level] ?: 0) and (1 shl flag)) != 0
    }

    fun setGlobalFlag(flag: Int) {
        globalFlags = globalFlags or (1 shl flag)
    }

    fun clearGlobalFlag(flag: Int) {
        globalFlags = globalFlags and (1 shl flag).inv()
    }

    fun getGlobalFlag(flag: Int): Boolean {
        return (globalFlags and (1 shl flag)) != 0
    }
}
