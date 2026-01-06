package pl.pelotasplus.eyeofbeholder.data.model

import pl.pelotasplus.eyeofbeholder.data.model.script.Script

data class Inf(
    val name: String,
    val subLevels: List<SubLevel>,
    val script: Script,
    val messages: List<String>,
)
