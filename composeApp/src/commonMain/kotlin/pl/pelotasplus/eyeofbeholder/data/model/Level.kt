package pl.pelotasplus.eyeofbeholder.data.model

import pl.pelotasplus.eyeofbeholder.data.model.script.Script

data class Level(
    val inf: String,
    val subLevels: List<SubLevel>,
    val script: Script,
    val messages: MutableList<String>
)

data class SubLevel(
    val index: Int,
    val mazName: String,
)

