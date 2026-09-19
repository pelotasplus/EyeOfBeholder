package pl.pelotasplus.eyeofbeholder.data.model

/**
 * What a spell laid on one of the party does to whoever is pointed at.
 *
 * Carrying one of these is what makes a spell ask which champion first, and
 * until it is answered nothing has happened — not the words, not the scroll,
 * not the hand's rest — so a question thought better of costs nothing.
 *
 * Every one can be pointed at somebody it would do nothing for, and every one
 * says so and is turned down rather than spent.
 */
sealed interface LaidOnAChampion {

    /** Whether it would do anything for [whom]. One that would not is refused. */
    fun wouldHelp(whom: Champion): Boolean

    /**
     * Hit points given back. Nothing reaches somebody ten below: that far down
     * is [Raises]'s business rather than mending's.
     */
    data class Mends(val by: Mending) : LaidOnAChampion {
        override fun wouldHelp(whom: Champion) = !whom.isWhole && !whom.deadForGood
    }

    data class Lifts(val what: Ailment) : LaidOnAChampion {
        override fun wouldHelp(whom: Champion) = what.troubles(whom)
    }

    /**
     * Somebody brought back from past raising, and from nowhere else: a
     * champion merely knocked down wants mending instead.
     *
     * Not an elf. What an elf leaves behind does not answer to this, which is
     * what the spell nobody has written is for — so a party of elves carry
     * their dead.
     */
    data object Raises : LaidOnAChampion {
        override fun wouldHelp(whom: Champion) = whom.deadForGood && whom.race != Race.ELF
    }
}

/**
 * Something wrong with a champion that a spell can take away again.
 *
 * Only the two that are lifted rather than waited out: being held wears off in
 * its own time, and being knocked down is mended rather than cured.
 */
enum class Ailment {

    POISON {
        override fun troubles(whom: Champion) = whom.poisoned
        override fun liftedFrom(whom: Champion) = whom.poisoned(false)
    },

    /** Stone, which no clock undoes — only this lifts it. */
    BEING_STONE {
        override fun troubles(whom: Champion) = whom.petrified
        override fun liftedFrom(whom: Champion) = whom.turnedBackToFlesh()
    };

    abstract fun troubles(whom: Champion): Boolean

    abstract fun liftedFrom(whom: Champion): Champion
}
