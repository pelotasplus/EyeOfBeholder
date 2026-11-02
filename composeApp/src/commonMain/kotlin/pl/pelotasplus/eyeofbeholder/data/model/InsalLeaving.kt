package pl.pelotasplus.eyeofbeholder.data.model

import pl.pelotasplus.eyeofbeholder.data.model.script.ItemOverrides

/**
 * The world after Insal has robbed a party of five or more and gone, or null
 * if he is not there to do it or [hours] of rest did not give him the chance.
 * What he owned he leaves on the floor; what he takes is the party's.
 *
 * The flags and the stripping table are data and cannot be worked out. The
 * two flags are the second floor's, not the one recording that he joined;
 * nothing about him explains them, so do not tidy them away.
 */
fun GameState.insalGoneInTheNight(hours: Int, level: Int, dice: Dice): GameState? {
    if (hours <= HOURS_BEFORE_HE_LEAVES) return null

    val insal = champions.indexOfFirst {
        it.canSpeak && it.portrait == NpcMeeting.joiningAs(NpcId(0))?.portrait
    }
    if (insal < 0 || champions.count { it.inTheParty } < SMALLEST_PARTY_HE_ROBS) return null

    var world = championDropped(PartySlot(insal), level, party.position, party.facing, dice)
    // Only the front four close up behind him: from the back row he leaves his gap.
    if (insal == BACK_ROW_LEFT && champions[BACK_ROW_RIGHT].inTheParty) {
        world = world.championsSwapped(PartySlot(BACK_ROW_LEFT), PartySlot(BACK_ROW_RIGHT))
    }
    THE_FLAGS_HE_CLEARS.forEach { world = world.globalFlagCleared(it) }

    val swords = world.stripped(ItemTypeId(1), most = 1) { it >= 1 }
    world = if (swords.took > 0) swords.world else swords.world.stripped(ItemTypeId(2), most = 1) { it >= 1 }.world
    world = world.stripped(ItemTypeId(31), most = 3) { it >= 0 }.world
    world = world.stripped(ItemTypeId(39), most = 3) { it == 1 }.world
    world = world.stripped(ItemTypeId(47), most = 2) { it >= 0 }.world

    return world.itemCopied(
        copyOf = HIS_NOTE,
        level = level,
        at = party.position,
        place = letGoOf(party.facing, dice),
        overrides = ItemOverrides(value = WHAT_HIS_NOTE_SAYS),
    )
}

private data class Stripped(val world: GameState, val took: Int)

/** Up to [most] things of [type] gone from the party, front of the party and first slot first. */
private fun GameState.stripped(type: ItemTypeId, most: Int, worth: (Int) -> Boolean): Stripped {
    val table = items.toMutableList()
    var took = 0
    val after = champions.map { who ->
        if (!who.inTheParty) return@map who
        who.copy(
            carrying = who.carrying.mapIndexed { slot, held ->
                val what = item(held)
                if (took < most && slot != CarrySlot.QUIVER.index && held.isSomething &&
                    what?.type == type && worth(what.value)
                ) {
                    took++
                    table[held.value] = what.copy(location = Item.NOWHERE)
                    ItemIndex(ItemIndex.NOTHING)
                } else {
                    held
                }
            },
        )
    }
    return Stripped(copy(champions = after, items = table), took)
}

private const val HOURS_BEFORE_HE_LEAVES = 4
private const val BACK_ROW_LEFT = 4
private const val BACK_ROW_RIGHT = 5
private const val SMALLEST_PARTY_HE_ROBS = 5
private val THE_FLAGS_HE_CLEARS = listOf(FlagBit(1), FlagBit(2))
private val HIS_NOTE = ItemIndex(28)
private const val WHAT_HIS_NOTE_SAYS = 26
