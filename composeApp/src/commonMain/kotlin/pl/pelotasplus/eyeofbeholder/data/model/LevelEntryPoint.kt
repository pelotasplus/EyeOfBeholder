package pl.pelotasplus.eyeofbeholder.data.model

import pl.pelotasplus.eyeofbeholder.data.model.script.NewLevelOrMonster
import pl.pelotasplus.eyeofbeholder.data.model.script.Script

/**
 * A square the game puts the party on when it enters a level.
 *
 * No level records its own entrances. Every ChangeLevel instruction names the
 * level it leads to along with the square and facing the party arrives on, so a
 * level's entrances are scattered across the scripts of all the levels that
 * lead into it — including, for a few of them, its own.
 */
data class LevelEntryPoint(
    val level: Int,
    val subLevel: Int,
    val location: Location,
    /** null where the instruction leaves the party facing the way it already was. */
    val direction: Direction?,
    val fromLevel: Int,
)

/** "LEVEL7.INF" for 7. */
fun levelFileName(level: Int) = "LEVEL$level.INF"

/** 7 for "LEVEL7.INF". */
fun levelNumber(fileName: String) =
    fileName.removePrefix("LEVEL").removeSuffix(".INF").toInt()

/** Every entrance this script leads to, [fromLevel] being the level it belongs to. */
fun List<Script>.entryPoints(fromLevel: Int): List<LevelEntryPoint> =
    mapNotNull { it.token as? NewLevelOrMonster.ChangeLevel }
        .map {
            LevelEntryPoint(
                level = it.level,
                subLevel = it.subLevel,
                location = it.location,
                direction = it.direction,
                fromLevel = fromLevel,
            )
        }
        .distinct()
