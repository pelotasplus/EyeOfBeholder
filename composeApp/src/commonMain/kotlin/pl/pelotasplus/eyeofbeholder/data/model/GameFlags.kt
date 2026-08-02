package pl.pelotasplus.eyeofbeholder.data.model

import kotlinx.serialization.Serializable
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
 * level 4   bit 0   the woman by the temple door has been spoken to
 *           bit 1   the remark about the strange bushes has been made
 *           bit 2   ... about the graves having found peace
 *           bit 3   ... about standing in a graveyard
 *           bit 5   the party came to the door from (15,11), not (16,10)
 *           bit 6   the woman walked them there herself
 *           bit 7   the answer to "do you wish to dig up this grave?"
 *
 * level 5   bit 0   the clerics on (13,8) have been spoken to from (13,9)
 *           bit 1   ... from (13,11), further down the same corridor
 *           bit 2   ... from (11,9), from the west
 *
 * level 6   bit 2   the priest by the stairs has had his say, or has lost
 *                   his chance
 * ```
 *
 * Not every bit is a memory. Level 4's bit 7 is how a subroutine answers the
 * square that called it: five graves share one routine that asks whether to
 * dig, and it clears the bit, sets it if the answer was yes, and clears it
 * again if a paladin in the party refuses to desecrate a grave. Each grave
 * reads the bit afterwards to decide whether to open itself and scatter what
 * it held. Bits 5 and 6 are short-lived in the same way: which of the two
 * squares outside the temple the party stepped from, so that declining to go
 * in puts them back on it, and whether they were walked to the door rather
 * than arriving on their own, which decides whether the door's scene has to
 * draw the view behind it afresh. All three are read and cleared at once.
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
@Serializable
data class GameFlags(
    private val levels: Map<Int, FlagWord> = emptyMap(),
    val global: FlagWord = FlagWord(),
) {
    fun forLevel(level: Int) = levels[level] ?: FlagWord()

    fun setting(level: Int, bit: FlagBit) =
        copy(levels = levels + (level to forLevel(level).with(bit)))

    fun settingGlobal(bit: FlagBit) = copy(global = global.with(bit))
}
