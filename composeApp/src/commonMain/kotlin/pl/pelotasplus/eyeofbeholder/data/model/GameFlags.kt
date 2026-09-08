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
 * level 2   bit 1   "this looks and smells of a prison block", on (8,20)
 *           bit 2   "someone alive in one of these cells!", on (10,20)
 *           bit 3   the ambush behind the door on (2,3) has been sprung
 *           bit 4   ... the one behind (10,7), one of whom has a skull key
 *           bit 5   ... the long one sprung from (8,4), which fills the
 *                   rooms from (5,1) across to (12,3)
 *           bit 6   "we are far underground", on (10,13)
 *           bit 7   "these doors are not part of the original", on (6,13)
 *           bit 8   the niche on (2,10) has been given its skull key
 *           bit 9   the ambush north of (8,7) has been sprung
 *           bit 10  the roll for spotting the fireball trap on (3,8) has
 *                   been made, whether or not it was made by a thief
 *           bit 11  the illusory wall by (4,18) has been called one
 *           bit 12  the pair let out on (14,19) have been let out
 *           bit 13  the guards on (14,20) have challenged the party
 *           bit 14  "this door looks stuck", on (8,4)
 *           bit 15  "a good place to hide things", on (2,17)
 *           bit 16  "surely, this passage leads somewhere?", on (22,19)
 *           bit 17  the ambush below (15,23) has been sprung
 *           bit 18  the worn path through the wall by (7,15) has been named
 *           bit 19  "why don't we read all the parchments again", on (15,7)
 *           bit 20  "the walls here could crumble down", on (22,3)
 *           bit 21  the vision of Khelben has been had, on (22,8)
 *           bit 24  the pair let out on (23,25) have been let out
 *           bit 25  set and cleared on (30,0); read where a wall is bashed
 *                   in the crumbling corner, deciding whether to suggest
 *                   trying another one
 *
 * level 3   bit 8   read before springing the trap on the treasure on
 *                   (13,14); nothing sets it, so the trap springs again for
 *                   each of the three things taken
 *
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
 *
 * level 7   bit 1   the wasp nest over (5,24) is running. Unlike every other
 *                   bit here it is turned off as well as on: (2,19) sets it
 *                   where it is clear and clears it where it is set, and the
 *                   floor's own clock reads it every 756 ticks and asks for
 *                   three more wasps on (5,24) while it holds
 *           bit 24  the wasps have been loosed, on (1,15) — twenty-three
 *                   conjured at once across seven squares, and the bit is
 *                   what stops the corridor filling again on the way back
 *           bit 25  set by standing on (13,21). (19,18) opens the way past
 *                   the four pillars only while it is *clear*, so crossing
 *                   (13,21) first is what shuts that way for good
 *           bit 29  (10,5) has had its say, which it only ever has for a
 *                   party with a dwarf in it — it asks for the bit clear and
 *                   a dwarf present, and then sets it. Not the global bit 29
 *                   below, which this floor also sets, and the clearest
 *                   example on hand of why these are read per level
 *
 * level 16  bit 0   the one at the end of the dungeon has had his say, on
 *                   (28,5) of the inner half. The same square also stops the
 *                   party resting there
 *           bit 1   the dying mage on (23,8) has gasped his line and dropped
 *                   what he was carrying
 *           bit 2   the four let out of the walls at (16,5), (16,11),
 *                   (13,8) and (19,8) have been let out. One subroutine
 *                   serves all four squares: it opens every side of all four
 *                   and puts one of them on each
 *           bit 4   which of its two frames the pair of magical fields are
 *                   drawn on
 *           bit 5   the field in (22,7)'s west face has been destroyed
 *           bit 6   ... and the one in (22,9)'s
 *           bit 7   the seven conjured around (8,8) have been conjured
 * ```
 *
 * Not every bit is a memory. Level 4's bit 7 is how a subroutine answers the
 * square that called it: five graves share one routine that asks whether to
 * dig, and it clears the bit, sets it if the answer was yes, and clears it
 * again if the party are talked out of it. Each grave reads the bit afterwards
 * to decide whether to open itself and scatter what it held.
 *
 * Who objects is a cleric or a paladin, either of them — the routine asks
 * whether the party hold anybody of one class or the other, and only then
 * puts the second question. A party of neither is never asked twice. Bits 5 and 6 are short-lived in the same way: which of the two
 * squares outside the temple the party stepped from, so that declining to go
 * in puts them back on it, and whether they were walked to the door rather
 * than arriving on their own, which decides whether the door's scene has to
 * draw the view behind it afresh. All three are read and cleared at once.
 *
 * Level 2's bit 17 is the plainest use there is, and the one to read first.
 * Its square conjures a key onto the floor and two guards holding two more
 * things, then sets the bit, and the whole is wrapped in a test of it. Without
 * that a party could pace on and off the square and take a key each time.
 *
 * Most of the rest of level 2 is that same shape, and the floor is the best
 * place to learn the three variations on it.
 *
 * A remark is a bit, and who makes it is asked before it is spent: (10,13)
 * looks for a dwarf and then, failing that, for a human, and a party of
 * neither walks past in silence with the bit still clear. (2,17) wants a thief
 * or a halfling, (4,18) a mage, (7,15) a dwarf.
 *
 * A bit can be spent by the dice rather than by the remark. On (3,8) the roll
 * is made first and the bit is set either way — so a party with no thief has
 * their one chance of noticing the fireball trap thrown away for them, and
 * cannot come back for another.
 *
 * And one bit can serve two squares, which is how a thing is made to happen
 * once no matter which way it is come upon. (9,20) and (21,21) share bit 12,
 * (14,20) and (14,21) share bit 13, (4,18) and (4,20) share bit 11, and the
 * second square's script differs only in what it has to put right about the
 * approach: the wording of a remark made from the other side, or a wall that
 * has to be opened before the scene can play.
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
 * Level 16's bits 4, 5 and 6 are the one place a flag is used as an animation.
 * A square there is woken every eighteen ticks and does nothing but flip bit 4
 * and repaint two wall faces from it, so the fields in (22,7) and (22,9) go on
 * alternating between their two shapes for as long as the party are on the
 * floor — and out of step with each other, since one takes the frame the other
 * has just left. Bits 5 and 6 are what takes a face out of that: a field
 * destroyed is left at its own wall, and once both are the flicker has nothing
 * to paint and the door between them opens.
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

    fun clearing(level: Int, bit: FlagBit) =
        copy(levels = levels + (level to forLevel(level).without(bit)))

    fun clearingGlobal(bit: FlagBit) = copy(global = global.without(bit))

    fun setting(remembered: WhatTheGameItselfRemembers) = settingGlobal(remembered.bit)

    fun has(remembered: WhatTheGameItselfRemembers) = global.isSet(remembered.bit)
}

/**
 * The bits of the global word the game itself writes rather than any script.
 *
 * A script's bits are anonymous — a number a level writes and another reads,
 * meaning whatever the two of them agree. These are not: the engine sets them
 * from inside a set piece, so nothing in the level data would ever explain
 * them, and a name is the only place their meaning can live.
 */
enum class WhatTheGameItselfRemembers(val bit: FlagBit) {
    /**
     * Somebody met on level 1 was let along. The scripts never set this and
     * level 15 reads it, which is where it is answered for.
     */
    SOMEBODY_WAS_LET_ALONG(FlagBit(6)),
}
