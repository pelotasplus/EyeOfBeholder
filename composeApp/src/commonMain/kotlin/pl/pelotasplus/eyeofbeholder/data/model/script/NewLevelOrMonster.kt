package pl.pelotasplus.eyeofbeholder.data.model.script

import pl.pelotasplus.eyeofbeholder.data.ByteReader
import pl.pelotasplus.eyeofbeholder.data.model.Location

/**
 * NewLevelOrMonster script token.
 * Either changes the current level or loads monster shapes.
 */
sealed class NewLevelOrMonster : ScriptToken {

    override fun read(reader: ByteReader): ScriptToken = read(reader)

    /**
     * Change to a new level.
     * cmd = -27 (0xE5)
     */
    data class ChangeLevel(
        val level: Int,
        val subLevel: Int,
        val location: Location,
        val direction: Int?  // null if 0xFF (keep current direction)
    ) : NewLevelOrMonster()

    /**
     * Load monster shapes.
     * Other cmd values.
     */
    data class LoadMonsterShapes(
        val cmd: Int,
        val monsterCmd: Int,
        val shapesName: String
    ) : NewLevelOrMonster()

    companion object Companion {
        fun read(reader: ByteReader): NewLevelOrMonster {
            val cmd = reader.readI8()
            val index = reader.readI8()

            return if (cmd == -27) { // 0xE5 - change level
                val subLevel = reader.readI8()
                val location = Location.read(reader)
                val dir = reader.readU8()

                ChangeLevel(
                    level = index,
                    subLevel = subLevel,
                    location = location,
                    direction = if (dir != 0xFF) dir else null
                )
            } else { // load monster shapes
                val monsterCmd = reader.readI8()
                val shapesName = reader.readString(13)
                LoadMonsterShapes(
                    cmd = cmd,
                    monsterCmd = monsterCmd,
                    shapesName = shapesName
                )
            }
        }
    }
}
