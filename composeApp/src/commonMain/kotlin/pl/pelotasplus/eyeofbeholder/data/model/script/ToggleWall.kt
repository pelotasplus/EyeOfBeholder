package pl.pelotasplus.eyeofbeholder.data.model.script

import pl.pelotasplus.eyeofbeholder.data.ByteReader
import pl.pelotasplus.eyeofbeholder.data.model.Location

/**
 * ToggleWall script token.
 * Toggles wall types or processes door switches.
 */
sealed class ToggleWall : ScriptToken {

    /**
     * Toggle one wall side.
     * type = -23 (0xE9)
     */
    data class OneSide(
        val location: Location,
        val dir: Int,
        val a: Int,
        val b: Int
    ) : ToggleWall()

    /**
     * Process door switch.
     * type = -22 (0xEA)
     */
    data class DoorSwitch(
        val location: Location
    ) : ToggleWall()

    /**
     * Toggle all walls.
     * type = -9 (0xF7)
     */
    data class AllSides(
        val location: Location,
        val a: Int,
        val b: Int
    ) : ToggleWall()

    data class Unknown(val type: Int) : ToggleWall()

    companion object {
        fun read(reader: ByteReader): ToggleWall {
            return when (val type = reader.readU8()) {
                0xE9 -> OneSide(                                // -23 toggle one side
                    location = Location.read(reader),
                    dir = reader.readI8(),
                    a = reader.readU8(),
                    b = reader.readU8()
                )

                0xEA -> DoorSwitch(                             // -22 door switch
                    location = Location.read(reader)
                )

                0xF7 -> AllSides(                               // -9 toggle all walls
                    location = Location.read(reader),
                    a = reader.readU8(),
                    b = reader.readU8()
                )

                else -> Unknown(type)
            }
        }
    }
}
