package pl.pelotasplus.eyeofbeholder.data.model

import kotlinx.serialization.Serializable

/**
 * Something with hold of a champion that will let go by itself.
 *
 * @property ticksLeft how much longer it holds. It runs down on the same
 *   clock as everything else the world keeps time on, which is why it is
 *   counted in ticks rather than in anything the player would recognise.
 */
@Serializable
data class Holding(
    val whose: PartySlot,
    val what: WhatABlowLeaves,
    val ticksLeft: Int,
)
