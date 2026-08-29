package pl.pelotasplus.eyeofbeholder.data.model

/**
 * A monster striking back.
 *
 * The mirror of [Fighting] and a simpler thing: a monster has no weapon and no
 * strength, only what its kind is worth. It needs `1d20` at or above its own
 * to-hit number less the champion's armour, and a twenty always lands however
 * well armoured they are.
 */
class MonstersTurn(
    private val kinds: List<MonsterProperty>,
    private val dice: Dice = Dice.random,
    // What a champion carries is only perishable by its kind, so nothing is
    // ruined by a monster on a turn taken without the table that says which
    // kinds those are.
    private val itemTypes: ItemTypes? = null,
    // Shifting feet to reach the party is not walking across the floor, so a
    // monster does it whether or not the floor lets it walk. Kept apart from
    // [MonsterPathing] for that reason.
    private val stepping: MonsterStepping? = null,
) {

    /**
     * What the monsters did with their turn, and the world they leave behind.
     *
     * A blow that did not land is in [missed] and not in [struck]: a miss takes
     * nothing off anybody, and something that reports it as a hit for no damage
     * will sooner or later flash, sound or bleed on a swing that touched air.
     */
    data class Taken(
        val struck: List<Struck>,
        val world: GameState,
        val missed: List<MonsterSlot> = emptyList(),
        val ruined: List<Ruined> = emptyList(),
        /** Who the venom took hold of, which is only those it was new to. */
        val poisoned: List<PartySlot> = emptyList(),
    )

    /** One monster's blow at one champion. */
    data class Struck(
        val monster: MonsterSlot,
        val at: PartySlot,
        val damage: Damage,
        val heard: TrackIndex?,
    )

    /** Something a blow destroyed, which is gone rather than dropped. */
    data class Ruined(val whose: PartySlot, val what: ItemIndex)

    /**
     * Every monster that can reach the party starts its swing.
     *
     * Starting it is all this does. The arm goes back, and only when it has
     * come down again — [landed] — does anybody get hurt: the wind-up is the
     * warning a player reads, and a blow resolved at the moment it begins is
     * no warning at all.
     *
     * Only a monster that has been roused: the pair on level 5 stand talking
     * until somebody hits one of them, and one nobody has provoked is scenery.
     *
     * Given [walking], one that cannot reach the party comes after them, and
     * one that has not been roused takes up the hunt when it notices them.
     * Without it a monster fights only from where it was placed, which is not
     * the game but is what every scene so far was built and frozen against.
     *
     * Only the monsters of one [group] take a turn. Turns are staggered across
     * the four rather than taken together, which is what keeps a pair from
     * moving as one body — see [MonsterInstance.turnGroup]. Null is every
     * monster at once, which is only ever what a test wants.
     */
    fun begun(
        world: GameState,
        walking: MonsterPathing? = null,
        wayRound: MonsterPathing.WayRound = MonsterPathing.WayRound.RIGHT_FIRST,
        group: Int? = null,
    ): GameState {
        // One swing at a time. A blow coming down at the party holds the rest
        // of the floor still until it lands, so a second monster cannot start
        // its own swing over the top of the first — without this a pair in
        // front take turns swinging and never leave a gap to move in.
        if (world.pinnedByASwing) return world

        // Anything that walks also notices, and whatever it was doing before it
        // is now hunting. What is standing by is deaf until it is hit, which is
        // the whole of level 5's encounter.
        val noticing = if (walking == null) world else world.copy(
            monsters = world.monsters.map {
                if (it.whatItDoes.noticesTheParty && !it.provoked && it.notices(world.party)) {
                    it.takingUpTheHunt()
                } else {
                    it
                }
            },
        )

        val taking = noticing.monsters
            .filter {
                if (it.striking != null) return@filter false
                if (group != null && it.turnGroup != group) return@filter false

                // Rooted, only a fight is going on at all. Once monsters walk,
                // everything with somewhere to be takes its turn.
                it.provoked || (walking != null && it.whatItDoes.wanders)
            }
            .map { it.index }

        // One at a time, and read back out of the world each time: an earlier
        // monster's step may have taken the square this one was making for.
        return taking.fold(noticing) { world, slot ->
            world.monsters.firstOrNull { it.index == slot }
                ?.let { takenBy(world, it, walking, wayRound) }
                ?: world
        }
    }

    /**
     * One monster's turn: swing at the party if it is already facing them,
     * and otherwise go to them.
     *
     * The order is the point. Swinging is only ever at the square a monster
     * already faces, so arriving and striking are different turns — except for
     * the kinds that carry the flag for it, which land a blow in the turn they
     * moved in and so cannot be escaped by stepping aside.
     */
    private fun takenBy(
        world: GameState,
        monster: MonsterInstance,
        walking: MonsterPathing?,
        wayRound: MonsterPathing.WayRound,
    ): GameState {
        if (walking != null && !monster.provoked) {
            return wandering(world, monster, walking)
        }

        swungBy(world, monster, walking)?.let { return it }

        if (walking == null) {
            // Rooted, all a monster out of reach can do is turn towards the
            // party. It reaches only the square it faces, so without this it
            // is dangerous from one side and harmless from the other three.
            return monster.facingThe(world.party)
                ?.let { world.monsterTurned(monster.index, it) }
                ?: world
        }

        val after = when (
            val went = walking.towards(world, monster, world.party.position, wayRound)
        ) {
            is MonsterStepping.Stepped.Moved -> went.world
            is MonsterStepping.Stepped.Turned -> went.world
            MonsterStepping.Stepped.Refused -> world
        }

        if (kinds.firstOrNull { it.id == monster.type.value }?.hitsAsItMoves != true) return after

        val moved = after.monsters.firstOrNull { it.index == monster.index } ?: return after
        return swungBy(after, moved, walking) ?: after
    }

    /**
     * A monster going about its own business, having not noticed the party.
     *
     * There is no route: it walks straight on, and turns by a fixed amount
     * when the way shuts. The amount is the whole difference between pacing a
     * corridor and following a wall.
     */
    private fun wandering(
        world: GameState,
        monster: MonsterInstance,
        walking: MonsterPathing,
    ): GameState {
        val mode = monster.whatItDoes
        val towards = mode.straysTowards
            ?: return walking.onwards(world, monster, mode.turnsBy).worldOr(world)

        return strayingOn(world, monster, walking, towards)
    }

    /**
     * The same, but also turning off into an opening it walks past rather than
     * only when something stops it — which is the only reason two monsters
     * given the same corridor do not end up in the same place.
     *
     * The one byte it remembers is where it is in that loop: having just moved
     * forward it is worth a glance to the side, having just turned it is not.
     */
    private fun strayingOn(
        world: GameState,
        monster: MonsterInstance,
        walking: MonsterPathing,
        towards: Int,
    ): GameState {
        var after = world
        var now = monster

        if (now.straying != Straying.SETTLED) {
            if (now.straying == Straying.TURNED_AWAY) {
                after = walking.onwards(after, now, -towards).worldOr(after)
                now = after.monsters.firstOrNull { it.index == monster.index } ?: return after
            }

            val aside = now.direction.turnedBy(towards)
            val open = walking.opensOnto(after, now, aside)

            if (now.straying == Straying.TURNED_AWAY) {
                return if (open) after else after.monsterStrayed(now.index, Straying.SETTLED)
            }

            if (open) {
                return walking.turning(after, now, aside).worldOr(after)
                    .monsterStrayed(now.index, Straying.SETTLED)
            }
        }

        val ahead = now.direction.oneStepFrom(Location(now.x, now.y))
        val stepped = walking.stepping(after, now, ahead)

        return if (stepped is MonsterStepping.Stepped.Moved) {
            stepped.world.monsterStrayed(now.index, Straying.WENT_FORWARD)
        } else {
            walking.turning(after, now, now.direction.turnedBy(-towards)).worldOr(after)
                .monsterStrayed(now.index, Straying.TURNED_AWAY)
        }
    }

    /**
     * What a monster facing the party's square does about it, or null when it
     * is facing somewhere else and has walking to do.
     *
     * Facing them is not the same as touching them: an arm reaches from only
     * two corners of a square, and one standing on the wrong corner spends the
     * turn shifting its feet. Nor is every turn one it swings on — the count
     * belongs to the attack itself, so a monster that spent turns walking is
     * not owed a free blow for them.
     */
    private fun swungBy(
        world: GameState,
        monster: MonsterInstance,
        walking: MonsterPathing?,
    ): GameState? {
        if (!monster.facesTheSquareOf(world.party)) return null

        if (!monster.reaches(world.party)) {
            stepping?.let { return it.shuffleOn(world, monster) }
            return walking?.shuffling(world, monster) ?: world
        }

        val counted = monster.turnCameRound()
        val after = world.copy(
            monsters = world.monsters.map { if (it.index == monster.index) counted else it },
        )

        return if (counted.readyToStrike) {
            after.monstersStriking(listOf(monster.index))
        } else {
            after
        }
    }

    /**
     * What the monsters whose arms have just come down do to the party.
     *
     * Whom each reaches is worked out now rather than when the arm went back,
     * so a party who have turned on the spot are hit as they now stand.
     */
    fun landed(world: GameState, byWhom: List<MonsterSlot>): Taken {
        var after = world
        val struck = mutableListOf<Struck>()
        val missed = mutableListOf<MonsterSlot>()
        val ruined = mutableListOf<Ruined>()
        val poisoned = mutableListOf<PartySlot>()

        world.monsters.filter { it.index in byWhom }.forEach { monster ->
            val blow = strike(after, monster) ?: return@forEach

            if (!blow.damage.landed) {
                missed += monster.index
                return@forEach
            }

            struck += blow

            // What it ruins, it ruins on the way in: a champion the same blow
            // finishes still loses it.
            ruins(after, monster, blow.at)?.let {
                after = it.first
                ruined += it.second
            }

            after = after.championHurt(blow.at, blow.damage)

            // What poisons, poisons on a blow that landed. It is not rolled
            // for and not saved against here — the champion has already been
            // hit, and the venom goes with the bite.
            if (kinds.firstOrNull { it.id == monster.type.value }?.poisonsWhatItHits == true) {
                if (after.championIn(blow.at)?.poisoned == false) poisoned += blow.at
                after = after.championPoisoned(blow.at)
            }
        }

        return Taken(struck, after, missed, ruined, poisoned)
    }

    /**
     * What one landed blow destroys of the champion's, and the world without
     * it, or null where nothing goes.
     *
     * Three blows in four of a monster that ruins things: the fourth spares
     * whatever it would have taken. Where it starts looking is random and it
     * takes the first perishable thing from there, so nothing about a slot
     * makes it safer than any other — a sword in the hand goes as readily as
     * one at the bottom of the pack.
     */
    private fun ruins(
        world: GameState,
        monster: MonsterInstance,
        whose: PartySlot,
    ): Pair<GameState, Ruined>? {
        val kind = kinds.firstOrNull { it.id == monster.type.value } ?: return null
        if (!kind.ruinsSomethingItHits) return null
        val types = itemTypes ?: return null
        if (dice.roll(1, IN_FOUR, 0) == IN_FOUR) return null

        val champion = world.championIn(whose) ?: return null
        val from = dice.roll(1, CarrySlot.ALL_OF_THEM, -1)

        repeat(CarrySlot.ALL_OF_THEM) { step ->
            val slot = CarrySlot((from + step) % CarrySlot.ALL_OF_THEM)
            val held = champion.carrying.getOrNull(slot.index) ?: return@repeat
            val item = world.item(held) ?: return@repeat
            if (!types.perishes(item)) return@repeat

            // Told what the champion now carry, so that armour eaten off them
            // is armour they no longer have the benefit of.
            val without = world.carrying(whose, slot, ItemIndex(ItemIndex.NOTHING), types)
            return without to Ruined(whose, held)
        }

        return null
    }

    /** Whether its arm gets to the party, which its kind's size has a say in. */
    private fun MonsterInstance.reaches(party: PartyState): Boolean {
        val size = kinds.firstOrNull { it.id == type.value }?.size ?: return false
        return canReach(party, size)
    }

    private fun strike(world: GameState, monster: MonsterInstance): Struck? {
        if (!monster.reaches(world.party)) return null

        val kind = kinds.firstOrNull { it.id == monster.type.value } ?: return null
        val whom = whoItReaches(world, monster) ?: return null
        val champion = world.championIn(whom) ?: return null

        var damage = 0
        repeat(kind.attacksPerRound) { attack ->
            val roll = dice.roll(1, 20, 0)
            val lands = roll == NATURALLY_ALWAYS ||
                roll >= kind.hitChance - champion.armorClass.value

            if (lands) {
                kind.dmgDc.getOrNull(attack)?.let { damage += dice.roll(it.times, it.pips, it.base) }
            }
        }

        // A monster is heard swinging whether or not it connects, which is the
        // one sound a monster has ever been given.
        return Struck(
            monster = monster.index,
            at = whom,
            damage = Damage(damage),
            heard = TrackIndex(kind.sound1).takeIf { kind.sound1 > 0 },
        )
    }

    /**
     * Which champion the blow falls on: the first of the six still standing,
     * in the order this monster reaches them from where it is.
     */
    private fun whoItReaches(world: GameState, monster: MonsterInstance): PartySlot? =
        WhoTheMonsterReaches
            .inOrder(world.party.facing, monster.direction, monster.place)
            .firstOrNull { world.championIn(it)?.dead == false }

    private companion object {
        /** A twenty lands on anything, however well armoured. */
        const val NATURALLY_ALWAYS = 20

        /** How often a monster that ruins things does: three of these. */
        const val IN_FOUR = 4
    }
}

/**
 * Which champion a monster's arm comes down on, in the order it reaches them.
 *
 * Three transcribed tables, and the shape of them is: the party's facing
 * against the monster's picks one of four groups of twelve; where the monster
 * stands on its own square picks which half of that twelve; and the six that
 * are left are champion slots, nearest first.
 */
object WhoTheMonsterReaches {

    fun inOrder(
        partyFacing: Direction,
        monsterFacing: Direction,
        monsterPlace: SquarePlace,
    ): List<PartySlot> {
        val group = GROUPS[partyFacing.ordinal * SIDES + monsterFacing.ordinal] * (SLOTS * 2)
        val half = if (nearerSide(monsterFacing, monsterPlace)) 0 else SLOTS

        return REACHED_IN_ORDER.subList(group + half, group + half + SLOTS).map(::PartySlot)
    }

    /**
     * Whether a monster stands on the half of its square nearer the party, and
     * so reaches the near pair of them first. Something in the middle of a
     * square is always near.
     */
    private fun nearerSide(facing: Direction, place: SquarePlace): Boolean {
        if (!place.onTheFloor) return true
        return NEARER[facing.ordinal * SIDES + place.ordinal] == 0
    }

    /**
     * Whether a monster on that corner can reach the party's square at all.
     * A separate table from [NEARER], and the one that keeps something
     * tucked into a far corner out of the fight.
     */
    fun armIsLongEnough(facing: Direction, place: SquarePlace): Boolean =
        !place.onTheFloor || CAN_REACH[facing.ordinal * SIDES + place.ordinal] == 1

    /** Which corners reach, by the monster's facing. Transcribed. */
    private val CAN_REACH = listOf(
        1, 1, 0, 0,
        0, 1, 0, 1,
        0, 0, 1, 1,
        1, 0, 1, 0,
    )

    /** Which group of twelve, by the party's facing against the monster's. */
    private val GROUPS = listOf(
        2, 3, 0, 1,
        1, 2, 3, 0,
        0, 1, 2, 3,
        3, 0, 1, 2,
    )

    /** Which half of it, by the monster's facing against where it stands. */
    private val NEARER = listOf(
        0, 1, 0, 1,
        0, 0, 1, 1,
        1, 0, 1, 0,
        1, 1, 0, 0,
    )

    /** And the six champions of each half, in the order they are reached. */
    private val REACHED_IN_ORDER = listOf(
        1, 0, 3, 2, 5, 4, /**/ 0, 1, 2, 3, 4, 5,
        5, 3, 1, 4, 2, 0, /**/ 1, 3, 5, 0, 2, 4,
        4, 5, 2, 3, 0, 1, /**/ 5, 4, 3, 2, 1, 0,
        0, 2, 4, 1, 3, 5, /**/ 4, 2, 0, 5, 3, 1,
    )

    private const val SIDES = 4
    private const val SLOTS = 6
}
