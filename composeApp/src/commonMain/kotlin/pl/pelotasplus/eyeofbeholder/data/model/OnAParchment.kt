package pl.pelotasplus.eyeofbeholder.data.model

/**
 * What a parchment carries, which its value says: a page to read, or a map to
 * look at.
 */
sealed interface OnAParchment {

    /** A page of the shared text file, which is read like a speech. */
    data class Writing(val page: DialogueTextId) : OnAParchment

    /**
     * A map, which is a picture and not a page: it goes up in the frame a
     * speaker would be in, and any click puts it away again.
     *
     * @property sourceLeft where it is cut from the sheet the three of them
     *   share, [WIDTH] by [HEIGHT] each
     */
    data class Map(val sourceLeft: Int, val sourceTop: Int) : OnAParchment {

        companion object {
            const val SHEET = "MAP.CPS"
            const val WIDTH = 160
            const val HEIGHT = 96

            /** Which corner each of the three is cut from. From the original. */
            private val CORNERS = listOf(0 to 0, 160 to 0, 0 to 96)

            /** A parchment's value counts the maps down from minus one. */
            fun forValue(value: Int): Map? = CORNERS.getOrNull(-value - 1)
                ?.let { (left, top) -> Map(left, top) }
        }
    }
}
