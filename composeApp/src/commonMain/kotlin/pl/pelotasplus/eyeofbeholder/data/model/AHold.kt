package pl.pelotasplus.eyeofbeholder.data.model

/**
 * A spell that stops a creature where it stands rather than hurting it.
 *
 * Two of them, and they differ only in what they will take hold of: one asks
 * for a person, the other for a monster, and a creature's kind is a bit on its
 * species rather than anything about the creature itself. Neither will take
 * hold of a creature carrying neither mark, so there are things in the dungeon
 * that no hold of either sort reaches.
 *
 * Held is not stunned and not asleep: it is a mode the creature is in, which
 * runs down on its own and which nothing the party do will cut short. It goes
 * on taking damage the whole time and can be killed where it stands — but
 * hurting it does not free it, and neither does anything else.
 */
enum class AHold(
    /** The mark a species must carry for this to take hold of it at all. */
    private val theKindItTakes: Int,
) {
    /** A person: what hold person will take, and only that. */
    OF_A_PERSON(0x1),

    /**
     * And what hold monster will take, which is not everything either.
     *
     * The name is the spell's rather than a description: the mark it asks for
     * is its own, and there are creatures carrying neither mark that no hold
     * of either sort will touch.
     */
    OF_A_MONSTER(0x2),
    ;

    /** Whether this hold is the right one for that kind of creature. */
    fun takesHoldOf(kind: MonsterProperty): Boolean = kind.typeFlags and theKindItTakes != 0

    companion object {
        /**
         * How many turns of its own a hold lasts. The game's number, and the
         * same for both — a hold is not made longer by being harder to cast.
         *
         * A turn here is a monster's turn rather than anything the player
         * counts: one comes round every twenty ticks, so fifteen of them is a
         * little over sixteen seconds of standing still.
         */
        const val FOR_THIS_LONG = 15

        /** What a creature may throw to shrug one off. */
        val THROWN_OFF_BY = SavingThrow.A_SPELL
    }
}
