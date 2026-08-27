package pl.pelotasplus.eyeofbeholder.data.model.script

import pl.pelotasplus.eyeofbeholder.data.ByteReader
import pl.pelotasplus.eyeofbeholder.data.model.Location

/**
 * Moving something from one place to another — the party, a monster, or the
 * things lying on a square.
 *
 * One opcode with a kind byte that says which, and each kind carries a
 * different tail. The layout is transcribed: a level in the tail is a byte
 * that is [THE_SAME_LEVEL] for the level the party are on, or else a marker
 * and the level after it; a block is a packed square.
 */
sealed class Teleport : ScriptToken {

    /** Every monster standing on [source] moved to [destination]. */
    data class MoveMonster(
        val source: Location,
        val destination: Location,
    ) : Teleport()

    /**
     * The party put on [destination].
     *
     * Two squares are stored and only [destination] is used — the kind byte
     * shares its tail with the moves that take a thing from one square to
     * another. Scripts leave [source] at (0,0).
     */
    data class MoveParty(
        val source: Location,
        val destination: Location,
    ) : Teleport()

    /**
     * The things lying on [from] moved to [to], of every kind ([ofType] null)
     * or of one ([ofType] set) — which is how a lever makes a key appear on a
     * square from the store it was kept in off the map.
     *
     * A null level is the level the party are on.
     */
    data class MoveItems(
        val ofType: Int?,
        val fromLevel: Int?,
        val from: Location,
        val toLevel: Int?,
        val to: Location,
    ) : Teleport()

    /** A kind byte nothing reads yet, its tail unread. */
    data class Unknown(val type: Int) : Teleport()

    companion object {
        private const val MOVE_ITEM = 0xF5
        private const val MOVE_ITEM_OF_TYPE = 0xE1
        private const val MOVE_MONSTER = 0xF3
        private const val MOVE_PARTY = 0xE8

        /** The level byte that means the one the party are on. */
        private const val THE_SAME_LEVEL = 0xEB

        fun read(reader: ByteReader): Teleport {
            return when (val type = reader.readU8()) {
                MOVE_MONSTER -> MoveMonster(Location.read(reader), Location.read(reader))
                MOVE_PARTY -> MoveParty(Location.read(reader), Location.read(reader))
                MOVE_ITEM, MOVE_ITEM_OF_TYPE -> MoveItems(
                    ofType = if (type == MOVE_ITEM_OF_TYPE) reader.readU16LE() else null,
                    fromLevel = readLevel(reader),
                    from = Location.read(reader),
                    toLevel = readLevel(reader),
                    to = Location.read(reader),
                )

                else -> Unknown(type)
            }
        }

        private fun readLevel(reader: ByteReader): Int? {
            val marker = reader.readU8()
            return if (marker == THE_SAME_LEVEL) null else reader.readU8()
        }
    }
}
