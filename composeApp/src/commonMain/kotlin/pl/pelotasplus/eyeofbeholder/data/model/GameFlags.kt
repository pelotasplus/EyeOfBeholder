package pl.pelotasplus.eyeofbeholder.data.model

/**
 * What the scripts remember: a word of flags for each level, and one word that
 * belongs to the game rather than to any level.
 *
 * A level's flags outlive leaving it. Walking back into level 5 does not make
 * its clerics greet the party a second time, because the bit their square set
 * is still set when the party returns.
 *
 * ## What the bits mean
 *
 * Nothing in the game data names them, and nothing fixes their meaning across
 * levels: bit 1 of level 5 is not bit 1 of level 7. A bit means whatever the
 * scripts that read and write it treat it as meaning, so the only way to learn
 * one is to read those scripts. What has been read so far:
 *
 * ```
 * level 5   bit 0   the clerics on (13,8) have been spoken to from (13,9)
 *           bit 1   ... from (13,11), further down the same corridor
 *           bit 2   ... from (11,9), from the west
 *
 * level 6   bit 2   the priest by the stairs has had his say, or has lost
 *                   his chance
 * ```
 *
 * Each of level 5's three squares tests its own bit and sets it, so a way in
 * speaks once and the other two still work — one bit for the whole encounter
 * would have silenced them.
 *
 * Level 6 spends its bit the other way about, on one encounter reachable three
 * ways. Stepping onto (10,2) with the bit clear sets it, conjures a priest
 * behind the party on (9,2), turns them round to face him and asks whether
 * they will leave or fight. The squares either side of that one, (9,2) and
 * (11,2), set the same bit and do nothing else — so a party that wanders into
 * the corridor from the side rather than straight up from the stairs never
 * meets him at all, and cannot afterwards.
 *
 * The global word is mostly one level leaving word for another:
 *
 * ```
 * global    bit 1-4   level 2 talking to itself
 *           bit 6     read on level 15; no script anywhere sets it
 *           bit 19    level 15, set once and cleared in nine places
 *           bit 20    level 12, set and cleared
 *           bit 28    set and cleared on level 4; read leaving level 5 for
 *                     level 4, choosing between arriving on (15,11) facing
 *                     south and (16,10) facing east
 *           bit 29    set on level 7, read on level 8
 *           bit 30    set by standing on level 1's (10,12), the stairs down to
 *                     level 5; read twice on level 5
 *           bit 31    set on level 9, read on level 6
 * ```
 *
 * Add to this only what a script has actually been watched doing.
 */
data class GameFlags(
    private val levels: Map<Int, FlagWord> = emptyMap(),
    val global: FlagWord = FlagWord(),
) {
    fun forLevel(level: Int) = levels[level] ?: FlagWord()

    fun setting(level: Int, bit: FlagBit) =
        copy(levels = levels + (level to forLevel(level).with(bit)))

    fun settingGlobal(bit: FlagBit) = copy(global = global.with(bit))
}
