package pl.pelotasplus.eyeofbeholder.data.model

/**
 * What casting a spell out of a thing costs the thing it was cast from.
 *
 * Reading is not free. A scroll goes up with the words, a wand is a use worse
 * off and goes when its last one does, and one wand is never spent at all.
 */
sealed interface WhatCastingCosts {

    /** Nothing, which is the wand that defends and everything not read from. */
    data object Nothing : WhatCastingCosts

    /** The whole of it: a scroll is read once and there is nothing left. */
    data object AllOfIt : WhatCastingCosts

    /** One of the uses it carries, the last of which takes the wand too. */
    data object OneCharge : WhatCastingCosts
}
