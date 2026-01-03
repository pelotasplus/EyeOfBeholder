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
    val vmpData: String,
    val palette: String?,
    val scriptTimers: List<ScriptTimer>,
    val monsters: List<MonsterProperty>,
    val monsterGfx: List<MonsterGfx>,
    val sound: String,
    val doors: List<Door>,
)

