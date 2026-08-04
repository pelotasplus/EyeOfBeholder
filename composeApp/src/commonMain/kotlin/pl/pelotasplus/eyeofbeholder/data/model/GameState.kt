package pl.pelotasplus.eyeofbeholder.data.model

import pl.pelotasplus.eyeofbeholder.data.model.script.CreateMonster

/**
 * What a trigger script is allowed to ask about the world it runs in.
 *
 * Scripts read more than the party. Level 5's encounter counts the monsters on
 * the square in front before letting its speaker talk, and falls silent once
 * they are dead — so the answer changes as the game is played, and the state
 * arrives with each call rather than being fixed when the runner is built.
 *
 * Only what is modelled lives here, along with anything else the still
 * unanswered conditions need.
 */
data class GameState(
    val party: PartyState,
    val monsters: List<MonsterInstance> = emptyList(),
    val flags: GameFlags = GameFlags(),
    /**
     * Every item in the game in one table, the dungeon's and the party's
     * alike, because everything that can hold one names it by its slot here:
     * a champion's [Champion.carrying], and in time a square's floor and the
     * hand. A game begins with ITEM.DAT's table and a save carries its own.
     */
    val items: List<Item> = emptyList(),

    /**
     * What the player is holding, which in the original is the mouse cursor
     * itself. It belongs to nobody in the party: it has been picked up out of
     * a hand or off the floor and not yet put anywhere.
     */
    val inHand: ItemIndex = ItemIndex(ItemIndex.NOTHING),
    /**
     * How each level stood when the party walked out of it, which is not how
     * its file describes it: monsters a script conjured are there, and in time
     * the ones that have been killed will be missing.
     */
    private val asTheyWereLeft: Map<Int, List<MonsterInstance>> = emptyMap(),

    /**
     * The walls a script has changed, as the byte it changed them to.
     *
     * Only the changed ones: a maze is four thousand faces of which a script
     * moves a handful, and keeping a copy of the rest would be a second answer
     * to what a wall is — the one nobody updates. The byte is what the script
     * writes and what the file holds, and [Maz.WallType.fromInt] is the one
     * place that says what it means.
     */
    private val changedWalls: Map<WallAt, WallByte> = emptyMap(),

    /** Each level's maze as its file describes it, for everything unchanged. */
    private val mazes: Map<Int, Maz> = emptyMap(),
) {

    /** What is in [slot], or null for an empty hand or pack slot. */
    fun item(slot: ItemIndex): Item? =
        if (!slot.isSomething) null else items.getOrNull(slot.value)

    /**
     * How many items are stacked in [slot].
     *
     * A dozen arrows are a dozen items in one place, not one item that knows
     * it is a dozen, and things in one place are strung into a ring — the last
     * points back at the one the slot names. So counting them is walking round
     * until it comes back, and a chain that leads nowhere stops the count
     * rather than running away with it.
     */
    fun stackedIn(slot: ItemIndex): Int {
        if (!slot.isSomething) return 0

        var at = slot.value
        var counted = 0

        while (counted <= items.size) {
            val item = items.getOrNull(at) ?: return counted
            counted++
            at = item.prev
            if (at == slot.value) return counted
        }
        return counted
    }

    /** What is being held, if anything. */
    val held: Item? get() = item(inHand)

    /** The same world with [slot] in the hand instead of whatever was. */
    fun holding(slot: ItemIndex) = copy(inHand = slot)

    /**
     * Which item lies in one quadrant of a square, if any. A square can hold
     * several, and the original takes them one at a time from where they were
     * put rather than off a pile.
     */
    fun lyingAt(level: Int, at: Location, quadrant: Int): ItemIndex? =
        items.indices.firstOrNull { slot ->
            items[slot].let { it.level == level && it.location == at && it.pos == quadrant }
        }?.let(::ItemIndex)

    /**
     * Puts what is in the hand down at one quadrant of a square, and leaves
     * the hand empty. Putting nothing down changes nothing.
     */
    fun puttingDown(level: Int, at: Location, quadrant: Int): GameState {
        if (!inHand.isSomething) return this

        return copy(
            items = items.mapIndexed { slot, item ->
                if (slot != inHand.value) item
                else item.copy(level = level, location = at, pos = quadrant)
            },
            inHand = ItemIndex(ItemIndex.NOTHING),
        )
    }

    /**
     * Takes what lies at one quadrant of a square into the hand. What is
     * picked up is being carried rather than lying anywhere, which is what
     * keeps it from being drawn where it was left.
     */
    fun takingUp(slot: ItemIndex) = copy(
        items = items.mapIndexed { at, item ->
            if (at != slot.value) item
            else item.copy(location = Item.CARRIED, level = Item.CARRIED_LEVEL)
        },
        inHand = slot,
    )

    /**
     * Another thing like the one in [copyOf], put down at [corner] of a
     * square.
     *
     * A script does not describe what it makes; it points at something the
     * world already holds and asks for another like it. The copy goes into
     * the first slot of the table that holds nothing, which is what all the
     * spare slots in a save are for — and if every one of them is taken the
     * table simply grows, so a level can never quietly stop making things.
     *
     * Nothing is made when there is nothing to copy, and the world comes back
     * unchanged.
     */
    fun itemCopied(
        copyOf: ItemIndex,
        level: Int,
        at: Location,
        corner: Int,
    ): GameState {
        val made = copyOf(copyOf) { it.copy(location = at, level = level, pos = corner) }
        return made?.world ?: this
    }

    /**
     * Puts a copy of [copyOf] somewhere in the table, [placed] where it goes,
     * and says which slot it landed in.
     */
    private fun copyOf(copyOf: ItemIndex, placed: (Item) -> Item): Made? {
        val template = item(copyOf)?.takeIf { it.exists } ?: return null
        val free = items.indexOfFirst { !it.exists }.takeIf { it > 0 }

        return if (free == null) {
            Made(copy(items = items + placed(template)), ItemIndex(items.size))
        } else {
            Made(
                world = copy(
                    items = items.mapIndexed { slot, item ->
                        if (slot == free) placed(template) else item
                    },
                ),
                slot = ItemIndex(free),
            )
        }
    }

    /** A thing a script has just made, and which slot of the table it is in. */
    private data class Made(val world: GameState, val slot: ItemIndex)

    /**
     * The same, into the hand — which is where a script puts a thing it means
     * the player to be holding. A hand that is already full has the thing put
     * on the floor at their feet instead, since it has to go somewhere.
     */
    fun itemCopiedIntoTheHand(copyOf: ItemIndex, level: Int, corner: Int): GameState {
        if (inHand.isSomething) return itemCopied(copyOf, level, party.position, corner)

        val made = copyOf(copyOf) {
            it.copy(location = Item.CARRIED, level = Item.CARRIED_LEVEL, pos = 0)
        } ?: return this

        return made.world.copy(inHand = made.slot)
    }

    /**
     * Puts what is being held onto the stack that [head] names the top of,
     * and leaves the hand empty.
     *
     * A dozen arrows are a dozen items in one slot, strung into a ring, so
     * joining one is a matter of linking into it: the newcomer becomes the
     * top and the ring closes round it.
     */
    fun stacking(head: ItemIndex): Stacked {
        val joining = inHand
        if (!joining.isSomething) return Stacked(this, head)

        val table = items.toMutableList()
        table[joining.value] = table[joining.value].copy(
            location = Item.ON_A_STACK,
            level = Item.NO_LEVEL,
            pos = 0,
        )

        if (!head.isSomething) {
            // the first of a stack is a ring of one, pointing at itself
            table[joining.value] = table[joining.value].copy(
                next = joining.value,
                prev = joining.value,
            )
        } else {
            val below = table[head.value].next
            table[joining.value] = table[joining.value].copy(
                prev = table[below].prev,
                next = below,
            )
            table[below] = table[below].copy(prev = joining.value)
            table[head.value] = table[head.value].copy(next = joining.value)
        }

        return Stacked(
            world = copy(items = table, inHand = ItemIndex(ItemIndex.NOTHING)),
            head = joining,
        )
    }

    /**
     * Takes the top thing off the stack [head] names into the hand, closing
     * the ring behind it. A stack of one leaves the slot empty.
     */
    fun unstacking(head: ItemIndex): Stacked {
        if (!head.isSomething) return Stacked(this, head)

        val taken = head.value
        val table = items.toMutableList()
        val above = table[taken].next
        val below = table[taken].prev

        table[above] = table[above].copy(prev = below)
        table[below] = table[below].copy(next = above)
        table[taken] = table[taken].copy(
            next = 0,
            prev = 0,
            location = Item.CARRIED,
            level = Item.CARRIED_LEVEL,
        )

        return Stacked(
            world = copy(items = table, inHand = head),
            head = if (below == taken) ItemIndex(ItemIndex.NOTHING) else ItemIndex(below),
        )
    }

    /** Remembers [level] as it stands, for whenever the party comes back. */
    fun leaving(level: Int) = copy(asTheyWereLeft = asTheyWereLeft + (level to monsters))

    /**
     * Puts the party on [level], as they left it if they have been before, and
     * as its file [places] it if they have not. [maz] is that file's walls,
     * which everything a script has not changed still comes from.
     */
    /**
     * @param subLevel the one being entered, which the monsters the file lists
     *   take as their own — they are read by whichever sublevel's tables the
     *   party arrive under, and are a different creature under each.
     */
    fun arrivingAt(
        level: Int,
        places: List<MonsterInstance>,
        maz: Maz? = null,
        subLevel: Int = 0,
    ) = copy(
        monsters = asTheyWereLeft[level] ?: places.map { it.copy(subLevel = subLevel) },
        mazes = if (maz == null) mazes else mazes + (level to maz),
    )

    /** The wall on one side of a square, changed or as the file has it. */
    fun wall(level: Int, at: Location, side: WallSide): Maz.WallType =
        Maz.WallType.of(wallByte(level, at, side))

    /** The same wall as the byte a script compares against and writes. */
    fun wallByte(level: Int, at: Location, side: WallSide): WallByte =
        changedWalls[WallAt(level, at, side)]
            ?: mazes[level]?.square(at)?.getWall(side)?.asByte()
            ?: WallByte(0)

    /** The same square with one of its sides changed to [to]. */
    fun wallChanged(level: Int, at: Location, side: WallSide, to: WallByte) =
        copy(changedWalls = changedWalls + (WallAt(level, at, side) to to))

    /** The same square with all four of its sides changed to [to]. */
    fun wallsChanged(level: Int, at: Location, to: WallByte) =
        copy(changedWalls = changedWalls + WallSide.entries.associate { WallAt(level, at, it) to to })

    /**
     * How many monsters stand on [location], at most seven.
     *
     * The engine keeps this count in the low three bits of the square's flag
     * byte, stepping it up as a monster arrives and down as it leaves, and a
     * script reads those bits to ask whether anything is standing there.
     */
    fun monstersOn(location: Location) = monsters
        .count { it.x == location.x && it.y == location.y }
        .coerceAtMost(MAX_MONSTERS_PER_SQUARE)

    /**
     * The world with the monster a script asked for standing in it.
     *
     * The engine refuses the same three ways: never under the party, never
     * onto a square already holding as many as it can, and never without a
     * free slot. The slot is not bookkeeping — it decides which of its
     * sheet's color schemes the monster is painted in.
     *
     * The engine has a fourth way out we cannot take yet: with every slot in
     * use it evicts whichever monster stands farthest from the party, killing
     * it if it still lives. Nothing dies here, so a full world drops the
     * spawn instead.
     */
    /** @param subLevel the one the party are in, which a new monster joins. */
    fun monsterCreated(spawn: CreateMonster, subLevel: Int = 0): GameState {
        val taken = monsters.map { it.index }.toSet()
        val slot = (0 until MONSTER_SLOTS).firstOrNull { it !in taken }

        return when {
            spawn.location == party.position -> this
            monstersOn(spawn.location) >= MAX_MONSTERS_PER_SQUARE -> this
            slot == null -> this
            else -> copy(monsters = monsters + MonsterInstance.spawnedBy(spawn, slot, subLevel))
        }
    }

    fun partyMovedTo(destination: Location) =
        copy(party = party.copy(position = destination))

    fun partyTurnedTo(direction: Direction) =
        copy(party = party.copy(facing = direction))

    fun levelFlagSet(level: Int, bit: FlagBit) =
        copy(flags = flags.setting(level, bit))

    fun globalFlagSet(bit: FlagBit) =
        copy(flags = flags.settingGlobal(bit))

    fun levelFlagCleared(level: Int, bit: FlagBit) =
        copy(flags = flags.clearing(level, bit))

    fun globalFlagCleared(bit: FlagBit) =
        copy(flags = flags.clearingGlobal(bit))

    fun isLevelFlagSet(level: Int, bit: FlagBit) = flags.forLevel(level).isSet(bit)

    fun isGlobalFlagSet(bit: FlagBit) = flags.global.isSet(bit)

    /** One face of one square of one level. */
    data class WallAt(val level: Int, val at: Location, val side: WallSide)

    /**
     * A stack after something joined it or left it: the world as it now
     * stands, and which item the slot holding the stack should name.
     */
    data class Stacked(val world: GameState, val head: ItemIndex)

    /**
     * Everything here the game files cannot say, ready to be written out.
     *
     * [mazes] is left behind deliberately: it is the level files, which are
     * loaded rather than saved, and putting a copy in every save would be a
     * second answer to what a wall is.
     */
    fun saved() = SavedWorld(
        party = party,
        monsters = monsters,
        flags = flags,
        items = items,
        inHand = inHand,
        leftBehind = asTheyWereLeft,
        changedWalls = changedWalls.map { (where, to) ->
            ChangedWall(where.level, where.at, where.side, to)
        },
    )

    companion object {
        /**
         * The world a save describes. The mazes arrive afterwards, with
         * [arrivingAt], because they come from the level files.
         *
         * [on] is the level the party were standing on, and it is remembered
         * as though they had just walked out of it — otherwise arriving back
         * would people it from its file again, and the monsters a script
         * conjured, or the ones already killed, would be undone by loading.
         */
        fun restoredFrom(saved: SavedWorld, on: Int) = GameState(
            party = saved.party,
            monsters = saved.monsters,
            flags = saved.flags,
            items = saved.items,
            inHand = saved.inHand,
            asTheyWereLeft = saved.leftBehind + (on to saved.monsters),
            changedWalls = saved.changedWalls.associate {
                WallAt(it.level, it.at, it.side) to it.to
            },
        )

        private const val MAX_MONSTERS_PER_SQUARE = 7

        /** How many monsters a level can have at once, placed and spawned together. */
        private const val MONSTER_SLOTS = 30
    }
}
