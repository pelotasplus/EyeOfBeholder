package pl.pelotasplus.eyeofbeholder.data.model

/** What comes of putting something on a champion's plate. */
sealed interface OnAPlate {

    /** Eaten, and the hand that held it emptied. */
    data class Eaten(val food: Item) : OnAPlate

    /** An empty hand: the plate is a place to put something, not a button. */
    data object NothingOffered : OnAPlate

    /** Not eaten, and [says] is what is said about it. */
    data class Refused(val says: String) : OnAPlate
}
