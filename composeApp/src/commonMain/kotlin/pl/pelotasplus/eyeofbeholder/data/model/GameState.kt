package pl.pelotasplus.eyeofbeholder.data.model

import co.touchlab.kermit.Logger
import pl.pelotasplus.eyeofbeholder.data.model.script.CreateMonster
import pl.pelotasplus.eyeofbeholder.data.model.script.ItemOverrides

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
    /**
     * Who the party are, as against [party], which is where they stand.
     *
     * Scripts ask about them: level 4's graves put a second question only to a
     * party holding a cleric or a paladin, so a script handed the world
     * without the champions in it cannot answer what the world is asked.
     *
     * Always six long, a slot nobody fills being [Champion.NOBODY].
     */
    val champions: List<Champion> = emptyList(),
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

    /**
     * The things that have been put somewhere since the game began, oldest
     * first — which is what says who lies on top of whom.
     *
     * A slot in the table names a thing; it does not say where in a pile it
     * sits, and a thing put down keeps the slot it always had. So the order
     * has to be kept rather than read off the table, or dropping a dagger on
     * a robe would bury it whenever the dagger's slot happened to be lower.
     *
     * A file's own items are in none of this and lie in table order, beneath
     * anything the party have moved.
     */
    private val putDownSince: List<ItemIndex> = emptyList(),

    /**
     * The doors on their way somewhere.
     *
     * A door is started and then left to finish on its own, so whatever
     * started it — a script, a button — is done with it at once and the party
     * are free while it moves. A door shut behind them is the point of this:
     * it is worth turning round to watch, and there is nothing to watch if
     * the game is holding still until it has finished.
     */
    val swinging: List<Swinging> = emptyList(),

    /** The hands still coming back to rest from a swing, on the same footing. */
    val recovering: List<HandRecovering> = emptyList(),

    /** The blows the party have just taken, still showing on their portraits. */
    val showingDamage: List<DamageShown> = emptyList(),

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

    /**
     * The same, as a script reads it: an empty hand is slot zero, and slot
     * zero holds a record like any other, so it is answered rather than
     * refused. [held] is the question the interface asks and says null.
     */
    val heldAsAScriptReadsIt: Item? get() = items.getOrNull(inHand.value)

    /** The world with what the hand held put nowhere and the hand emptied. */
    fun handEmptied(): GameState {
        if (!inHand.isSomething) return this

        return copy(
            items = items.mapIndexed { at, item ->
                if (at == inHand.value) item.copy(location = Item.NOWHERE) else item
            },
            inHand = ItemIndex(ItemIndex.NOTHING),
        )
    }

    /**
     * The world with what lies on a square put nowhere — everything on it, or
     * only the things of one kind.
     */
    fun itemsSweptFrom(level: Int, at: Location, ofType: ItemTypeId?): GameState = copy(
        items = items.map { item ->
            val swept = item.exists &&
                    item.level == level &&
                    item.location == at &&
                    (ofType == null || item.type == ofType)

            if (swept) item.copy(location = Item.NOWHERE) else item
        },
    )

    /**
     * The same world with one of a champion's slots holding something else.
     *
     * Who the party are is part of the world now, so moving a thing between a
     * hand and a slot is one change to it rather than two — done as two, one
     * of them overwrites the other.
     *
     * Given [types] it also works their armour out again, which has to happen
     * here or not at all: a saved armour class is only the answer as it stood
     * when the game was saved, and a champion who puts a shield down and keeps
     * the old number is as hard to hit as they ever were.
     */
    fun carrying(
        champion: PartySlot,
        slot: CarrySlot,
        item: ItemIndex,
        types: ItemTypes? = null,
    ): GameState {
        val who = champions.getOrNull(champion.index) ?: return this

        val after = who.copy(
            carrying = who.carrying.toMutableList().also { held -> held[slot.index] = item },
        )

        return copy(
            champions = champions.toMutableList().also {
                it[champion.index] = types
                    ?.let { known -> after.copy(armorClass = known.armourClassOf(after, items)) }
                    ?: after
            },
        )
    }

    /**
     * Whether anything is standing on that square.
     *
     * A monster stops the party as surely as a wall does, and is refused the
     * same way: they do not walk through one, and they do not swap places with
     * one. It is also why a monster that has come up to them cannot be simply
     * walked past — it has to be gone round or killed.
     */
    fun anythingStandingOn(at: Location): Boolean =
        monsters.any { it.x == at.x && it.y == at.y }

    /** Who is in one of the six places, or null where nobody is. */
    fun championIn(slot: PartySlot): Champion? =
        champions.getOrNull(slot.index)?.takeIf { it.inTheParty }

    /**
     * Whether anybody in the party is of one of [classes]. One will do — a
     * script asks whether the party hold such a person at all, not how many.
     */
    fun anybodyOfClass(classes: Set<CharacterClass>): Boolean =
        champions.any { it.inTheParty && it.countsAs.any { of -> of in classes } }

    fun anybodyOfRace(race: Race): Boolean =
        champions.any { it.inTheParty && it.race == race }

    /** The same world with [slot] in the hand instead of whatever was. */
    fun holding(slot: ItemIndex) = copy(inHand = slot)

    /** Whether there is a place in the party for one more. */
    val roomForOneMore: Boolean get() = champions.any { !it.inTheParty }

    /**
     * The same world with somebody taking the first free place in the party,
     * and the bones the party were carrying of theirs let go of.
     *
     * Bones are what is left of somebody who is not with the party: carried to
     * where they can be raised, they are that person again. Whoever has just
     * walked up cannot also be a pile of bones in the pack, so the original
     * takes theirs out at the moment of joining rather than leaving both.
     */
    fun joinedBy(somebody: Champion, whose: NpcId): GameState {
        val place = champions.indexOfFirst { !it.inTheParty }
        if (place < 0) return this

        return copy(
            champions = champions.mapIndexed { slot, was ->
                if (slot == place) somebody else was
            },
        ).withoutBonesOf(whose)
    }

    /**
     * A pile of bones knows whose it is by the number that person is, plus
     * one. Only what the party are carrying is let go of: bones of theirs
     * lying somewhere in the dungeon are nothing to do with the party.
     */
    private fun withoutBonesOf(whose: NpcId): GameState {
        val carried = champions.flatMap { it.carrying }.toSet() + inHand

        val theirs = carried
            .filter { slot ->
                item(slot)?.let {
                    it.exists && it.type == BONES && it.value == whose.value + 1
                } == true
            }
            .toSet()

        if (theirs.isEmpty()) return this

        return copy(
            items = items.mapIndexed { slot, item ->
                if (ItemIndex(slot) in theirs) item.copy(location = Item.NOWHERE) else item
            },
            champions = champions.map { champion ->
                champion.copy(
                    carrying = champion.carrying.map {
                        if (it in theirs) ItemIndex(ItemIndex.NOTHING) else it
                    },
                )
            },
            inHand = if (inHand in theirs) ItemIndex(ItemIndex.NOTHING) else inHand,
        )
    }

    /**
     * Every thing in the world, bottom of its pile first.
     *
     * One order, read by the hand and by the screen alike: whoever is drawn
     * last is on top, and whoever is on top is what a hand closes on. Reading
     * the two from separate rules is how the party come away holding something
     * that is not the thing they can see.
     */
    val fromTheBottomUp: List<ItemIndex>
        get() = items.indices.map(::ItemIndex).sortedBy { putDownSince.indexOf(it) }

    /** Which item lies at one place on a square, if any — the one on top. */
    fun lyingAt(level: Int, at: Location, place: SquarePlace): ItemIndex? =
        fromTheBottomUp.lastOrNull { slot ->
            item(slot)?.let { it.level == level && it.location == at && it.place == place } == true
        }

    /**
     * How many things of one kind lie on a square, which is what a plate set
     * into the floor weighs.
     *
     * A thing in the air over the square is passing across it rather than
     * resting on it, and does not count unless the asking script says it
     * should.
     */
    fun itemsLyingOn(
        level: Int,
        at: Location,
        ofType: ItemTypeId?,
        countingWhatIsInTheAir: Boolean,
    ): Int = items.count {
        it.level == level && it.location == at &&
            (ofType == null || it.type == ofType) &&
            (countingWhatIsInTheAir || it.place != SquarePlace.MIDDLE)
    }

    /**
     * Whether one particular thing lies on a square, and which slot of the
     * table it is — the number the original hands back, so that a script may
     * do more with it than ask whether it was there at all.
     */
    fun theOneLyingOn(level: Int, at: Location, item: ItemIndex): ItemIndex? =
        item.takeIf { this.item(it)?.let { on -> on.level == level && on.location == at } == true }

    /**
     * Puts what is in the hand down at one place on a square, and leaves the
     * hand empty. Putting nothing down changes nothing.
     */
    fun puttingDown(level: Int, at: Location, place: SquarePlace): GameState {
        if (!inHand.isSomething) return this

        return copy(
            items = items.mapIndexed { slot, item ->
                if (slot != inHand.value) item
                else item.copy(level = level, location = at, place = place)
            },
            inHand = ItemIndex(ItemIndex.NOTHING),
            putDownSince = putDownSince.filterNot { it == inHand } + inHand,
        )
    }

    /**
     * Takes what lies at one place on a square into the hand. What is picked
     * up is being carried rather than lying anywhere, which is what keeps it
     * from being drawn where it was left.
     */
    fun takingUp(slot: ItemIndex) = copy(
        items = items.mapIndexed { at, item ->
            if (at != slot.value) item
            else item.copy(location = Item.CARRIED, level = Item.CARRIED_LEVEL)
        },
        inHand = slot,
    )

    /**
     * Another thing like the one in [copyOf], put down at [place] on a square.
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
        place: SquarePlace,
        overrides: ItemOverrides = ItemOverrides(),
    ): GameState {
        val made = copyOf(copyOf) {
            overrides.applyTo(it).copy(location = at, level = level, place = place)
        }
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
    fun itemCopiedIntoTheHand(
        copyOf: ItemIndex,
        level: Int,
        place: SquarePlace,
        overrides: ItemOverrides = ItemOverrides(),
    ): GameState {
        if (inHand.isSomething) return itemCopied(copyOf, level, party.position, place, overrides)

        val made = copyOf(copyOf) {
            overrides.applyTo(it).copy(location = Item.CARRIED, level = Item.CARRIED_LEVEL)
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
     * @param kinds that sublevel's species, which say what each of them can
     *   take. Rolled for here rather than where the file is read, so that
     *   coming back to a level the party have already been on finds the
     *   monsters as hurt as they were left and does not roll again.
     */
    fun arrivingAt(
        level: Int,
        places: List<MonsterInstance>,
        maz: Maz? = null,
        subLevel: Int = 0,
        kinds: List<MonsterProperty> = emptyList(),
        dice: Dice = Dice.random,
    ) = copy(
        monsters = asTheyWereLeft[level] ?: places.map {
            it.copy(subLevel = subLevel).rolledIfKnown(kinds, dice)
        },
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
     * Which face of a square its doorway hangs in, or null where there is no
     * door. A doorway takes two opposite faces, so either of them will do and
     * the first found is the answer.
     */
    fun doorFacing(level: Int, at: Location): WallSide? =
        WallSide.entries.firstOrNull { wall(level, at, it) is Maz.WallType.Door }

    /**
     * The same world with a doorway one step further open, or further shut.
     *
     * Both faces of the doorway move together, and each keeps its own kind —
     * the side with the button on it does not hand its button to the side
     * without one. A wall that is no door is left alone.
     */
    fun doorStepped(level: Int, at: Location, side: WallSide, opening: Boolean): GameState =
        listOf(side, side.opposite).fold(this) { world, face ->
            val door = world.wall(level, at, face) as? Maz.WallType.Door ?: return@fold world
            world.wallChanged(level, at, face, door.stepped(opening).asByte())
        }

    /**
     * The same world with a door set going, which it then does by itself.
     *
     * A door already at the end it is being sent to does not move and is not
     * heard trying, and one already going the other way turns around rather
     * than being sent twice.
     *
     * Where a door stands is not the whole of what it is doing. One still shut
     * because the clock has not reached it yet is nonetheless on its way open,
     * and telling it to close has to turn it round — otherwise a plate stepped
     * on and straight off again keeps the opening it was sent on, and the door
     * it works stands open for good. The original never meets this: it moves a
     * door off the end position the moment it sends it, so a door on its way
     * is never found standing at the end it started from.
     */
    fun doorSetGoing(level: Int, at: Location, side: WallSide, opening: Boolean): GameState {
        val door = wall(level, at, side) as? Maz.WallType.Door ?: return this
        val onItsWay = swinging.firstOrNull { it.level == level && it.at == at }

        val atThatEnd = if (opening) door.isOpen else door.isShut
        if (atThatEnd && onItsWay?.opening != !opening) return this

        val going = Swinging(level, at, side, opening)

        return copy(
            swinging = if (onItsWay != null) {
                swinging.map { if (it.level == level && it.at == at) going else it }
            } else {
                swinging + going
            },
        )
    }

    /**
     * Every door in motion one position further along, with what each of them
     * was heard doing. A door that has arrived stops being one in motion.
     */
    fun doorsStepped(): DoorsStepped {
        var world = this
        val heard = mutableListOf<TrackIndex>()
        val stillGoing = mutableListOf<Swinging>()

        swinging.forEach { door ->
            world = world.doorStepped(door.level, door.at, door.side, door.opening)

            val now = world.wall(door.level, door.at, door.side) as? Maz.WallType.Door
            val arrived = now == null || if (door.opening) now.isOpen else now.isShut

            heard += DoorSounds.of(door.opening, arriving = arrived)
            if (!arrived) stillGoing += door
        }

        return DoorsStepped(world.copy(swinging = stillGoing), heard)
    }

    /**
     * The same world with a door forced out of its frame.
     *
     * A door stuck fast is not a door in the maze at all: it is a wall with a
     * picture of one on it, which is why nothing opens it and why forcing it
     * is what it takes. What it becomes is a real doorway on both faces of the
     * square — one without a button, since the door it replaces never had one
     * to press — and it is still shut, so that it can be seen to swing.
     */
    fun forcedOutOfItsFrame(level: Int, at: Location, side: WallSide): GameState {
        val door = Maz.WallType.Door(
            doorIndex = DoorIndex(if (wallByte(level, at, side) == FIRST_KIND) 0 else 1),
            hasButton = false,
            state = 0,
        )

        return listOf(side, side.opposite).fold(this) { world, face ->
            world.wallChanged(level, at, face, door.asByte())
        }
    }

    /**
     * The same world with a lever thrown. A lever is two wall shapes kept side
     * by side, one for each way it points, so throwing it is a step from the
     * one to the other and back again.
     */
    fun leverThrown(level: Int, at: Location, side: WallSide, up: Boolean): GameState =
        wallChanged(
            level, at, side,
            WallByte(wallByte(level, at, side).value + if (up) 1 else -1),
        )

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
    fun monsterCreated(
        spawn: CreateMonster,
        subLevel: Int = 0,
        kinds: List<MonsterProperty> = emptyList(),
        dice: Dice = Dice.random,
    ): GameState {
        val taken = monsters.map { it.index }.toSet()
        val slot = (0 until MONSTER_SLOTS).firstOrNull { it !in taken }

        return when {
            spawn.location == party.position -> this
            monstersOn(spawn.location) >= MAX_MONSTERS_PER_SQUARE -> this
            slot == null -> this
            else -> copy(
                monsters = monsters + MonsterInstance
                    .spawnedBy(spawn, slot, subLevel)
                    .rolledIfKnown(kinds, dice),
            )
        }
    }

    /**
     * The world with one monster hurt, and without it if that killed it.
     *
     * A dead one leaves the list rather than lying there: the engine empties
     * its record and puts it on no square, which is the same thing said the
     * long way — nothing left in it is ever read, and the slot it frees is the
     * next slot a script's conjuring takes.
     */
    fun monsterHurt(slot: Int, by: Int): GameState {
        val hit = monsters.firstOrNull { it.index == slot } ?: return this

        if (!hit.couldBeHurt) {
            Logger.w(TAG) { "Monster $slot was never rolled for, so nothing can hurt it" }
            return this
        }

        val after = hit.hurt(by)
        return copy(
            monsters = if (after.hitPoints.current <= 0) monsters - hit
            else monsters.map { if (it.index == slot) after else it },
        )
    }

    private fun MonsterInstance.rolledIfKnown(kinds: List<MonsterProperty>, dice: Dice) =
        kinds.firstOrNull { it.id == type.value }?.let { rolledFor(it, dice) } ?: this

    /**
     * The world a moment later, with whatever was struck no longer showing it.
     *
     * The flash is a single moment rather than a fading thing, so this puts
     * every one of them back at once.
     */
    fun flashesFaded() = copy(monsters = monsters.map { it.copy(struck = false) })

    /** Whether anything is showing a blow this instant. */
    val anythingFlashing: Boolean get() = monsters.any { it.struck }

    /**
     * The world with one champion [by] hit points worse off.
     *
     * Nothing else happens to them here: going down at nothing left and being
     * past raising at ten below are what the panel already reads off the
     * number, so taking it away is the whole of the change.
     */
    fun championHurt(whose: PartySlot, by: Int): GameState {
        if (by <= 0) return this
        val who = champions.getOrNull(whose.index) ?: return this

        return copy(
            champions = champions.toMutableList().also {
                it[whose.index] = who.copy(
                    hitPoints = who.hitPoints.copy(current = who.hitPoints.current - by),
                )
            },
            // A second blow before the first has faded shows its own number
            // rather than the two added up.
            showingDamage = showingDamage.filterNot { it.whose == whose } +
                DamageShown(whose, by, DamageShown.WHILE_IT_SHOWS.value),
        )
    }

    /** What is showing on that champion's portrait, if anything. */
    fun damageShownOn(whose: PartySlot): Int? =
        showingDamage.firstOrNull { it.whose == whose }?.amount

    /** The world with every splat that much nearer to going. */
    fun damageFaded(by: Ticks = DamageShown.STEP) = copy(
        showingDamage = showingDamage
            .map { it.copy(ticksLeft = it.ticksLeft - by.value) }
            .filter { it.ticksLeft > 0 },
    )

    /**
     * The world with a monster roused, and with it everything standing by
     * waiting to see whether the party were friendly.
     *
     * Swinging at one of a group is swinging at all of them: the pair on level
     * 5 greet the party together and turn on them together, and hitting either
     * ends the conversation for both. Anything already fighting, or minding
     * its own business for a reason of its own, is not touched.
     */
    fun rousedBy(slot: Int) = copy(
        monsters = monsters.map {
            if (it.index == slot || it.standingBy) it.copy(provoked = true) else it
        },
    )

    /** The world with those monsters a frame further through their swing. */
    fun monstersStriking(slots: List<Int>) = copy(
        monsters = monsters.map { if (it.index in slots) it.swingingOn() else it },
    )

    /** The world with one monster standing somewhere else, facing [way]. */
    fun monsterMoved(slot: Int, to: Location, way: Direction, place: SquarePlace) = copy(
        monsters = monsters.map {
            if (it.index == slot) {
                it.copy(block = to.asBlock, direction = way, place = place)
            } else {
                it
            }
        },
    )

    /** The world with one monster further round its own loop of looking about. */
    fun monsterStrayed(slot: Int, straying: Straying) = copy(
        monsters = monsters.map { if (it.index == slot) it.copy(straying = straying) else it },
    )

    /** The world with one monster standing somewhere else on its own square. */
    fun monsterShifted(slot: Int, place: SquarePlace) = copy(
        monsters = monsters.map { if (it.index == slot) it.copy(place = place) else it },
    )

    /** The same for a squareful of them, standing aside for one arriving. */
    fun monstersShifted(places: Map<Int, SquarePlace>): GameState {
        if (places.isEmpty()) return this

        return copy(
            monsters = monsters.map { monster ->
                places[monster.index]?.let { monster.copy(place = it) } ?: monster
            },
        )
    }

    /**
     * The world with one monster facing [way] where it stands.
     *
     * Turning is all it does with the turn. It cannot also swing, because
     * swinging is only ever at the square it was already facing.
     */
    fun monsterTurned(slot: Int, way: Direction) = copy(
        monsters = monsters.map {
            if (it.index == slot) it.copy(direction = way) else it
        },
    )

    /** The world with those monsters turned to face where they are told. */
    fun monstersTurnedToFace(ways: List<Pair<Int, Direction>>): GameState {
        if (ways.isEmpty()) return this
        val turning = ways.toMap()

        return copy(
            monsters = monsters.map { monster ->
                turning[monster.index]?.let { monster.copy(direction = it) } ?: monster
            },
        )
    }

    /** The world with every swing carried on to its next frame. */
    fun swingsCarriedOn() = copy(
        monsters = monsters.map { if (it.striking == null) it else it.swingingOn() },
    )

    /**
     * Whether anything the screen shows has changed since [was] — which is
     * what says whether a tick of the clock is worth redrawing for.
     *
     * The party's own step is not on the list: nothing on screen says how far
     * through it they are, only whether the next one is refused.
     */
    fun somethingMoved(was: GameState): Boolean =
        monsters != was.monsters || champions != was.champions

    /** Whether any monster is part way through its own swing. */
    val anythingSwinging: Boolean get() = monsters.any { it.striking != null }

    /**
     * Whether the party are pinned by an arm already coming down at them.
     *
     * Once a monster in front of them has begun its swing the blow is theirs,
     * and the original will not let them do anything at all until it lands —
     * so the wind-up announces a hit rather than offering a chance to duck.
     * What a party dance away from is a monster's turn coming round, not the
     * swing they can already see.
     *
     * Only the square straight ahead: that is the one a swing is drawn on, and
     * something reaching them from the side holds nobody up.
     */
    val pinnedByASwing: Boolean
        get() {
            val ahead = party.facing.oneStepFrom(party.position)
            return monsters.any { it.striking != null && it.x == ahead.x && it.y == ahead.y }
        }

    /** Whether that hand is still coming back to rest from its last swing. */
    fun isRecovering(whose: PartySlot, hand: CarrySlot): Boolean =
        recovering.any { it.whose == whose && it.hand == hand }

    /** The world with that hand put out of use for as long as a swing costs. */
    fun handSwung(whose: PartySlot, hand: CarrySlot, came: WhatTheBlowCameTo) = copy(
        recovering = recovering.filterNot { it.whose == whose && it.hand == hand } +
            HandRecovering(whose, hand, came.wait.value, came),
    )

    /**
     * What the weapon slots show between them, which is what says whether a
     * tick of the clock changed anything worth redrawing for.
     */
    val asTheSlotsRead: List<Pair<HandRecovering, Boolean>>
        get() = recovering.map { it to it.stillReporting }

    /** What that hand's slot says at the moment, if it is saying anything. */
    fun reportIn(whose: PartySlot, hand: CarrySlot): WhatTheBlowCameTo? = recovering
        .firstOrNull { it.whose == whose && it.hand == hand && it.stillReporting }
        ?.came

    /**
     * The world one tick of the recovery clock later. A hand whose wait has run
     * out is dropped, which is what puts it back to use.
     */
    fun recoveryStepped(by: Ticks = HandRecovering.STEP) = copy(
        recovering = recovering
            .map { it.copy(ticksLeft = it.ticksLeft - by.value) }
            .filter { it.ticksLeft > 0 },
    )

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
        private const val TAG = "GameState"

        /** What a pile of somebody's bones is, as the item table counts kinds. */
        private val BONES = ItemTypeId(33)

        /**
         * How long the party take over a step or a turn. From the original,
         * where a monster's turn is twenty — so the party get five actions to
         * a monster's one, which is exactly the room the dance round a monster
         * needs and no more.
         *
         * It is not part of the world: a script hands back the world it was
         * given, so anything the party's own clock had written into it would
         * be undone every time one ran.
         */
        val A_STEP = Ticks(4)

        /** And how often that clock, and the monsters', are wound on. */
        val CLOCK_STEP = Ticks(2)

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

        /**
         * The one wall a forced door leaves behind as a doorway of the level's
         * first kind. Every other stuck door becomes one of the second.
         */
        private val FIRST_KIND = WallByte(51)

        private const val MAX_MONSTERS_PER_SQUARE = 7

        /** How many monsters a level can have at once, placed and spawned together. */
        private const val MONSTER_SLOTS = 30
    }
}
