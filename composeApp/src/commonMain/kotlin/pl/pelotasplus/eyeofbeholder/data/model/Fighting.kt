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
 * @property wallsThatGiveWay the sublevel's webs, which a blow takes down when
 *   there is nothing standing in front of them to hit instead.
 */
class Fighting(
    private val itemTypes: ItemTypes,
    private val kinds: List<MonsterProperty>,
    private val dice: Dice = Dice.random,
    // Where a slain monster's belongings land, on the level they land on.
    private val level: Int = 0,
    private val wallsThatGiveWay: Set<WallByte> = emptySet(),
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
                    after = after.whatAMonsterDrops(struck, level, dice)
                }
            }

            is Blow.Missed -> after = after.rousedBy(blow.monster)

            is Blow.CutDown ->
                after = after.websCutOn(level, aheadOf(world), wallsThatGiveWay)

            else -> Unit
        }

        return Struck(blow, after)
    }

    private fun blowStruck(world: GameState, whose: PartySlot, hand: CarrySlot): Blow {
        if (!whose.inTheFrontRank) return Blow.OutOfReach

        val champion = world.championIn(whose) ?: return Blow.Nothing

        // Somebody down, or held where they stand, does not swing. Their slots
        // are drawn barred over to say so, and this is the same answer to
        // anything that asks them anyway — a click on the bars, or a key that
        // sets the whole front rank going.
        if (!champion.canAct) return Blow.Unable

        // A hand swings a weapon, or nothing at all. Anything else in it is
        // held rather than wielded, and asking it to strike is asking the
        // wrong question of it — before this, a shield rolled its own dice and
        // a set of lock picks hit for whatever the table said they were worth.
        //
        // This sits here rather than beside the click that starts a swing
        // because there is more than one such click, and the key that sets the
        // whole front rank going was not asking.
        // A hand swings a weapon, or nothing at all. Anything else in it is
        // held rather than wielded, and asking it to strike is asking the
        // wrong question of it — before this, a shield rolled its own dice and
        // a set of lock picks hit for whatever the table said they were worth.
        //
        // This sits here rather than beside the click that starts a swing
        // because there is more than one such click, and the key that sets the
        // whole front rank going was not asking.
        val inHand = world.item(champion.holding(hand))
        if (inHand != null && !itemTypes.isSwungByHand(inHand)) return Blow.NotAWeapon

        // A web is swung at only when nothing is standing in front of it. The
        // blow takes it down without a roll — it is cut, not fought.
        val target = inReach(world, whose)
            ?: return if (facingAWallThatGivesWay(world)) {
                Blow.CutDown(edged = itemTypes.isEdged(inHand))
            } else {
                Blow.Nothing
            }

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
    private fun damageOf(weapon: Item?, champion: Champion, kind: MonsterProperty?): Damage {
        val rolled = weapon?.let { itemTypes[it.type]?.damageAgainst(kind, dice) }
            ?: dice.roll(1, 2, 0)

        val plus = weapon?.value ?: 0
        return Damage((rolled + champion.abilities.strengthDamageBonus + plus).coerceAtLeast(0))
    }

    /** The square the party are looking at, which is the one a blow reaches. */
    private fun aheadOf(world: GameState): Location {
        val (dx, dy) = world.party.facing.transformCoordinates(0, -1)
        return Location(world.party.position.x + dx, world.party.position.y + dy)
    }

    /** Whether the face of the square ahead that looks back at the party is a web. */
    private fun facingAWallThatGivesWay(world: GameState): Boolean {
        val facingUs = world.party.facing.transformWallSide(WallSide.SOUTH)
        return world.wallByte(level, aheadOf(world), facingUs) in wallsThatGiveWay
    }

    /**
     * Which monster on the square ahead the blow lands on: whatever fills the
     * square, else the first corner this champion reaches.
     */
    private fun inReach(world: GameState, whose: PartySlot): MonsterInstance? {
        val ahead = aheadOf(world)

        val there = world.monsters.filter { it.x == ahead.x && it.y == ahead.y }
        if (there.isEmpty()) return null

        there.firstOrNull { it.place == SquarePlace.MIDDLE }?.let { return it }

        val order = WhoIsInReach.cornersFor(world.party.facing, whose)
        return order.firstNotNullOfOrNull { corner -> there.firstOrNull { it.place == corner } }
    }
}
