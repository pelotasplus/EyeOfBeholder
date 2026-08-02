package pl.pelotasplus.eyeofbeholder.data.model.script

import pl.pelotasplus.eyeofbeholder.data.ByteReader
import pl.pelotasplus.eyeofbeholder.data.model.Location

/**
 * Teleport script token.
 * Handles movement of items, monsters, and party.
 */
sealed class Teleport : ScriptToken {

    

    /**
     * Move item.
     * type = 0xF5 (-11)
     */
    sealed class MoveItem : Teleport() {
        abstract val source: Location

        data class ToLevel(
            override val source: Location,
            val srcLevel: Int,
            val dstLevel: Int,
            val dstLocation: Location
        ) : MoveItem()

        data class ToBlock(
            override val source: Location,
            val srcLevel: Int,
            val dstBlk: Location
        ) : MoveItem()

        data class Other(
            override val source: Location,
            val srcLevel: Int,
            val sub: Int,
            val payload: UByteArray
        ) : MoveItem()
    }

    /**
     * Move item by type.
     * type = 0xE1 (-31)
     */
    data class MoveItemByType(
        val itemType: Int,
        val source: Location,
        val srcBlk: Location,
        val dstBlk: Location
    ) : Teleport()

    /**
     * Move monster.
     * type = 0xF3 (-13)
     */
    data class MoveMonster(
        val source: Location,
        val destination: Location
    ) : Teleport()

    /**
     * Move party.
     * type = 0xE8 (-24)
     *
     * Two blocks are stored and only [destination] is used — the opcode shares
     * its layout with the moves that take something from one square to
     * another, and the party is simply put on the second. Scripts leave
     * [source] at (0,0), so reading the wrong one of the two would look right
     * nearly everywhere.
     */
    data class MoveParty(
        val source: Location,
        val destination: Location
    ) : Teleport()

    data class Unknown(
        val type: Int,
        val source: Location
    ) : Teleport()

    companion object {
        fun read(reader: ByteReader): Teleport {
            val type = reader.readU8()

            // If type is 0xE1, read item_type before source
            val itemType = if (type == 0xE1) reader.readU16LE() else null

            val source = Location.read(reader)

            return when (type) {
                0xF5 -> { // Move item
                    val srcLevel = reader.readU8()
                    val sub = reader.readU8()
                    when (sub) {
                        0xE5 -> MoveItem.ToLevel(
                            source = source,
                            srcLevel = srcLevel,
                            dstLevel = reader.readU8(),
                            dstLocation = Location.read(reader)
                        )
                        0xEB -> MoveItem.ToBlock(
                            source = source,
                            srcLevel = srcLevel,
                            dstBlk = Location.read(reader)
                        )
                        else -> MoveItem.Other(
                            source = source,
                            srcLevel = srcLevel,
                            sub = sub,
                            payload = reader.readBytes(4)
                        )
                    }
                }
                0xE1 -> MoveItemByType( // Move item by type
                    itemType = itemType!!,
                    source = source,
                    srcBlk = Location.read(reader),
                    dstBlk = Location.read(reader)
                )
                0xF3 -> MoveMonster( // Move monster
                    source = source,
                    destination = Location.read(reader)
                )
                0xE8 -> MoveParty( // Move party
                    source = source,
                    destination = Location.read(reader)
                )
                else -> Unknown(type = type, source = source)
            }
        }
    }
}
