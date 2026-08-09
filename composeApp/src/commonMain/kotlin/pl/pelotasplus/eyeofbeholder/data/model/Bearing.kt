package pl.pelotasplus.eyeofbeholder.data.model

import kotlin.jvm.JvmInline
import kotlin.math.abs

/**
 * Which way something lies, in eighths of a turn clockwise from north.
 *
 * It exists because [Direction] deliberately cannot name a diagonal and this
 * has to: a monster works out where the party lie before it works out which
 * way it can walk, and "north-east" is an answer to the first question that is
 * no answer at all to the second.
 */
@JvmInline
value class Bearing(val eighths: Int) {

    /** Whether this is a way something could actually walk. */
    val isCardinal: Boolean get() = eighths % 2 == 0

    /** The same way as something can face, or null for a diagonal. */
    val asDirection: Direction?
        get() = if (isCardinal) Direction.entries[eighths / 2] else null

    /** The same bearing turned [byEighths] clockwise. */
    fun turned(byEighths: Int) = Bearing((eighths + byEighths) and 7)

    /** How far clockwise [other] is from this one, from 0 to 7. */
    fun clockwiseTo(other: Bearing) = (other.eighths - eighths) and 7

    /** The square one square this way from [from], diagonals included. */
    fun oneStepFrom(from: Location): Location {
        val (dx, dy) = STEPS[eighths]
        return Location(from.x + dx, from.y + dy)
    }

    companion object {
        /**
         * Which way [to] lies from [from], or null when they are the same
         * square.
         *
         * A bearing is not a line: everything within a wedge of the compass
         * reads as one of the eight, so a square two along and one up is
         * simply north-east.
         */
        fun from(from: Location, to: Location): Bearing? {
            val northwards = from.y - to.y
            val eastwards = to.x - from.x

            var bits = 0
            if (2 * northwards >= abs(eastwards)) bits = bits or NORTHWARD
            if (-2 * northwards >= abs(eastwards)) bits = bits or SOUTHWARD
            if (2 * eastwards >= abs(northwards)) bits = bits or EASTWARD
            if (-2 * eastwards >= abs(northwards)) bits = bits or WESTWARD

            return WEDGES[bits]?.let { Bearing(it) }
        }

        /** The same way as something faces. */
        fun of(direction: Direction) = Bearing(direction.ordinal * 2)

        private const val WESTWARD = 1
        private const val EASTWARD = 2
        private const val SOUTHWARD = 4
        private const val NORTHWARD = 8

        /**
         * Which of the eight a wedge of the compass reads as, from the game's
         * own table. Sixteen entries because the four wedge tests are
         * independent bits, and most of the combinations cannot arise — the
         * one that can is nothing set at all, which is the square itself.
         */
        private val WEDGES: Array<Int?> = arrayOfNulls<Int>(16).also {
            it[NORTHWARD] = 0
            it[NORTHWARD or EASTWARD] = 1
            it[EASTWARD] = 2
            it[SOUTHWARD or EASTWARD] = 3
            it[SOUTHWARD] = 4
            it[SOUTHWARD or WESTWARD] = 5
            it[WESTWARD] = 6
            it[NORTHWARD or WESTWARD] = 7
        }

        private val STEPS = arrayOf(
            0 to -1,
            1 to -1,
            1 to 0,
            1 to 1,
            0 to 1,
            -1 to 1,
            -1 to 0,
            -1 to -1,
        )
    }
}
