package pl.pelotasplus.eyeofbeholder.data.model

/**
 * What a trigger script is allowed to ask about the party.
 *
 * Scripts interrogate the party before deciding what to do — the stairs on
 * level 5 check which way it is facing before letting anyone through. Every
 * such question needs an answer from somewhere, and passing them one at a time
 * does not scale past the first, so they arrive together.
 *
 * Only what is modelled lives here. The characters themselves are the next to
 * arrive — scripts ask which classes are present and what is in hand — along
 * with anything else the still unanswered conditions need.
 */
data class PartyState(
    val position: Location,
    val facing: Direction,
)
