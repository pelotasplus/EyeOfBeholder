package pl.pelotasplus.eyeofbeholder.data.model

/**
 * The one thing a hand does with what it holds.
 *
 * A hand does one thing and not several: a potion is drunk, a ration eaten, a
 * horn blown, and only what is left over is swung. Deciding each of those
 * separately and then offering the swing as well drinks the potion and hits
 * with the flask in the same breath, which is what used to happen.
 *
 * So the kinds are read once, here, and the answer is one of these. Every kind
 * of thing has an answer — the ones with nothing to do still have something to
 * say about it — and adding a kind means adding it to this list rather than
 * remembering to exclude it somewhere else.
 */
sealed interface HandUse {

    /** Swung at whatever stands in front. An empty hand is a fist, and swings. */
    data object Swing : HandUse

    /** Drunk, out of the hand it is in. */
    data object Drink : HandUse

    /** Eaten, out of the hand it is in. */
    data object Eat : HandUse

    /** Blown, which is heard; what the sound is for is the wall's business. */
    data class Blow(val horn: Horn) : HandUse

    /**
     * Read, which puts a page up.
     *
     * Alone among these it is the whole of what the click does: the wall in
     * front is not asked what it makes of it, though the game asks for
     * everything else. Asking runs a script that ends by clearing the box,
     * which closes the page the instant it opens.
     */
    data class Read(val what: OnAParchment) : HandUse

    /** Nothing, and says so: it is worn to work, not used. */
    data object WorksByBeingWorn : HandUse

    /** Nothing, and says so: it is not a thing used this way at all. */
    data object NotUsedThisWay : HandUse

    /**
     * Nothing, and says nothing. A kind the game does something with that
     * nothing here has been written for yet — a scroll, a wand, a spellbook.
     * Distinct from [NotUsedThisWay], which is the game's own refusal.
     */
    data object NotWrittenYet : HandUse
}
