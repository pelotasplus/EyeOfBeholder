package pl.pelotasplus.eyeofbeholder.data.model

import kotlinx.serialization.Serializable
/**
 * What a trigger script is allowed to ask about the party.
 *
 * Scripts interrogate the party before deciding what to do — the stairs on
 * level 5 check which way it is facing before letting anyone through. Every
 * such question needs an answer from somewhere, and passing them one at a time
 * does not scale past the first, so they arrive together.
 *
 * This is where the party are, not who they are: the champions themselves are
 * the roster, and who speaks a message is [speakerFrom]'s business. What is
 * still missing here is what scripts ask about the characters — which classes
 * are present, what is in hand.
 */
@Serializable
data class PartyState(
    val position: Location,
    val facing: Direction,
)
