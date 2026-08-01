package pl.pelotasplus.eyeofbeholder.data.model.script

import pl.pelotasplus.eyeofbeholder.data.ByteReader
import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.Location

/**
 * SetWall script token.
 * Changes wall types or party direction.
 */
sealed class SetWall : ScriptToken {

    

    /**
     * Set all sides of a wall.
     * type = -9 (0xF7)
     */
    data class AllSides(
        val location: Location,
        val to: Int
    ) : SetWall()

    /**
     * Set one side of a wall.
     * type = -23 (0xE9)
     */
    data class OneSide(
        val location: Location,
        val side: Int,
        val to: Int
    ) : SetWall()

    /**
     * Change party direction.
     * type = -19 (0xED)
     */
    data class ChangePartyDirection(
        val direction: Direction
    ) : SetWall()

    data class Unknown(val type: Int) : SetWall()

    companion object {
        fun read(reader: ByteReader): SetWall {
            return when (val type = reader.readU8()) {
                0xF7 -> AllSides(                                // 0xF7 - all sides
                    location = Location.read(reader),
                    to = reader.readU8()
                )

                0xE9 -> OneSide(                                // 0xE9 - one side
                    location = Location.read(reader),
                    side = reader.readU8(),
                    to = reader.readU8()
                )

                0xED -> ChangePartyDirection(                   // 0xED - change party direction
                    direction = Direction.entries[reader.readU8()]
                )

                else -> error("Unknown SetWall type: $type")
            }
        }
    }
}
