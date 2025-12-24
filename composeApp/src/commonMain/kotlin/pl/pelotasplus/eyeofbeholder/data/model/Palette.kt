package pl.pelotasplus.eyeofbeholder.data.model

import kotlinx.collections.immutable.ImmutableList

data class Palette(
    val name: String,
    val colors: ImmutableList<RGB>,
)
