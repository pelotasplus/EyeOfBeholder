package pl.pelotasplus.eyeofbeholder.data.model

/**
 * Striking with what is in a hand.
 *
 * Holds the three things a blow needs that the world does not carry: what each
 * kind of item is, what each species of monster is, and where the luck comes
 * from.
 *
 * @property kinds the sublevel's species, which say how well armoured each is
 *   and how big — a weapon rolls different dice against something bigger than
 *   a champion.
 */
class Fighting(
    private val itemTypes: ItemTypes,
    private val kinds: List<MonsterProperty>,
    private val dice: Dice = Dice.random,
) {

    /** A blow, and the world it leaves behind. */
    data class Struck(val blow: Blow, val world: GameState)

    /**
     * [whose] swings what is in [hand] at whatever is on the square in front
     * of the party.
     *
     * The square is the one ahead whether or not the party could walk onto it:
     * a monster in a doorway is struck through the doorway, and so is one
     * behind a grating.
     */
    fun strike(world: GameState, whose: PartySlot, hand: CarrySlot): Struck {
        if (world.isRecovering(whose, hand)) return Struck(Blow.StillRecovering, world)

        val blow = blowStruck(world, whose, hand)
        val came = WhatTheBlowCameTo.of(blow)

        // The arm is committed the moment it goes back, so the wait is paid
        // whether or not it found anything — swinging at thin air is how the
        // party would otherwise get a free look at what is round the corner.
        // An arm that never went costs the shorter wait, being only the time
        // the slot spends saying so.
        var after = world
        if (came != null) after = after.handSwung(whose, hand, came)

        // Swinging rouses whatever was swung at whether or not it connects,
        // and a miss is as much an answer to a greeting as a hit.
        when (blow) {
            is Blow.Hit -> {
                val struck = world.monsters.firstOrNull { it.index == blow.monster }
                after = after.rousedBy(blow.monster).monsterHurt(blow.monster, blow.damage)

                val killed = struck != null && after.monsters.none { it.index == blow.monster }
                if (killed) {
                    val worth = kinds.firstOrNull { it.id == struck.type.value }?.experience ?: 0
                    after = after.partyEarns(XpPoints(worth.toLong()), dice)
                }
            }

            is Blow.Missed -> after = after.rousedBy(blow.monster)
            else -> Unit
        }

        return Struck(blow, after)
    }

    private fun blowStruck(world: GameState, whose: PartySlot, hand: CarrySlot): Blow {
        if (!whose.inTheFrontRank) return Blow.OutOfReach

        val champion = world.championIn(whose) ?: return Blow.Nothing
        val target = inReach(world, whose) ?: return Blow.Nothing
        val kind = kinds.firstOrNull { it.id == target.type.value }

        val weapon = world.item(champion.holding(hand))
        val bonus = champion.abilities.strengthToHitBonus + (weapon?.value ?: 0)
        val needed = champion.needsToHit(kind?.armorClass ?: 0) - bonus

        if (dice.roll(1, 20, 0).coerceIn(1, 20) < needed) return Blow.Missed(target.index)

        return Blow.Hit(target.index, damageOf(weapon, champion, kind))
    }

    /**
     * A weapon rolls its own dice; a bare hand rolls 1d2. Either way strength
     * is added and the whole thing is floored at nothing, so a weak champion
     * with a small knife does no healing.
     */
    private fun damageOf(weapon: Item?, champion: Champion, kind: MonsterProperty?): Int {
        val rolled = weapon?.let { itemTypes[it.type]?.damageAgainst(kind, dice) }
            ?: dice.roll(1, 2, 0)

        val plus = weapon?.value ?: 0
        return (rolled + champion.abilities.strengthDamageBonus + plus).coerceAtLeast(0)
    }

    /**
     * Which monster on the square ahead the blow lands on: whatever fills the
     * square, else the first corner this champion reaches.
     */
    private fun inReach(world: GameState, whose: PartySlot): MonsterInstance? {
        val (dx, dy) = world.party.facing.transformCoordinates(0, -1)
        val ahead = Location(world.party.position.x + dx, world.party.position.y + dy)

        val there = world.monsters.filter { it.x == ahead.x && it.y == ahead.y }
        if (there.isEmpty()) return null

        there.firstOrNull { it.place == SquarePlace.MIDDLE }?.let { return it }

        val order = WhoIsInReach.cornersFor(world.party.facing, whose)
        return order.firstNotNullOfOrNull { corner -> there.firstOrNull { it.place == corner } }
    }
}
