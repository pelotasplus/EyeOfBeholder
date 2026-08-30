package pl.pelotasplus.eyeofbeholder.data.model

/**
 * A single floor/area within a level — the fundamental "world chunk" of the game.
 *
 * Each level (INF) contains one or more sublevels. The first sublevel is the main
 * dungeon floor; additional sublevels may use completely different wall graphics,
 * palettes, and monster sets (e.g. a forest area vs. a stone dungeon).
 *
 * A sublevel combines all the data needed to render and simulate one area:
 *
 * @property level The level number this sublevel belongs to (1-16, matches LEVELn.INF)
 * @property index Zero-based index within the parent level (0 = main floor)
 * @property maz The 32x32 dungeon grid defining wall types for every square
 * @property vmp Viewport mapping — how tiles are arranged to create the 3D view
 * @property vcn Tile set — the actual 8x8 pixel tiles used for rendering
 * @property palette 256-color VGA palette for this sublevel's graphics
 * @property scriptTimers Timed script triggers (e.g. "call script func every N ticks")
 * @property monsters Monster type definitions available on this sublevel (stats, attacks, etc.)
 * @property monsterGfx Monster graphic set references (CPS filenames for sprite sheets)
 * @property sound Background music/ambient sound filename
 * @property doors Up to 2 door type definitions with their CPS graphics and button rectangles
 * @property decorations Wall decoration mappings (lever, alcove, painting, etc.) that
 *           override how specific wall indices are rendered and interacted with
 */
data class SubLevel(
    val level: Int,
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
    val decorations: List<Decoration>,
) {
    /** The wall bytes a blow takes down here — the webs, where there are any. */
    val wallsThatGiveWay: Set<WallByte>
        get() = decorations
            .filter { it.givesWayToABlow }
            .map { WallByte(it.decorationWallIndex) }
            .toSet()
}

