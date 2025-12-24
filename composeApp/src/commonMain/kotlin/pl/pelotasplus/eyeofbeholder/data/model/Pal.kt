package pl.pelotasplus.eyeofbeholder.data.model

import kotlinx.collections.immutable.ImmutableList

data class Pal(
    val name: String,
    val colors: ImmutableList<RGB>,
)
