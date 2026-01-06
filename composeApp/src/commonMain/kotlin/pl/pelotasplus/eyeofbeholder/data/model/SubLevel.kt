package pl.pelotasplus.eyeofbeholder.data.model

data class SubLevel(
    val index: Int,
    val maz: Maz,
    val vmp: Vmp,
    val vcn: Vcn,
    val palette: Palette,
    val scriptTimers: List<ScriptTimer>,
    val monsters: List<MonsterProperty>,
    val monsterGfx: List<MonsterGfx>,
    val sound: String,
    val doors: List<Door>,
)

