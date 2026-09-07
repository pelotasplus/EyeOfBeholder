package pl.pelotasplus.eyeofbeholder.data.model

import co.touchlab.kermit.Logger
import pl.pelotasplus.eyeofbeholder.data.model.Harm.hurtBy
import pl.pelotasplus.eyeofbeholder.data.model.script.CreateMonster
import pl.pelotasplus.eyeofbeholder.data.model.script.ItemOverrides
import pl.pelotasplus.eyeofbeholder.data.model.script.Damage as DamageDealt

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

    /** Whether a script has forbidden the party to sleep where they are. */
    val preventRest: Boolean = false,
    /**
     * Every item in the game in one table, the dungeon's and the party's
     * alike, because everything that can hold one names it by its slot here:
     * a champion's [Champion.carrying], and in time a square's floor and the
     * hand. A game begins with ITEM.DAT's table and a save carries its own.
     */
    val items: List<Item> = emptyList(),

    /**
     * Whatever is crossing the floor rather than lying on it.
     *
     * A save does not carry these — [saved] does not ask for them — so a game
     * put down while a fireball is halfway along a corridor comes back with it
     * landed rather than still coming.
     */
    val inFlight: List<Projectile> = emptyList(),

    /**
     * The fireballs going off this instant, which are not saved either: one
     * lasts about as long as it takes to read this.
     */
    val bursting: List<Burst> = emptyList(),

    /**
     * The sparks a casting throws about the room, while they last.
     *
     * One at a time: a second casting begins them again rather than running
     * two sets at once, there being one view for them to be scattered over.
     */
    val sparkling: SparksInTheRoom? = null,

    /**
     * What the player is holding, which is the mouse cursor itself. It belongs to nobody in the party: it has been picked up out of
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

    /**
     * The champions something is still holding, and how much longer for.
     *
     * Only what lets go by itself is here. Venom and stone are on no clock —
     * a champion has to be seen to for either — so nothing that leaves those
     * puts anything in this list.
     */
    val holding: List<Holding> = emptyList(),

    /**
     * The squares the party have stood on, level by level, which is what the
     * little map draws. Kept with the world rather than beside it so that a
     * game picked up again is picked up mapped.
     *
     * A level is mapped whole rather than a sublevel at a time. Which sublevel
     * is showing is decided by the walls in sight and changes as the party
     * walk, so keeping a map per sublevel splits one floor into several and
     * hands back a blank one to a party who come in by a different door.
     */
    val visited: Map<Int, Set<Location>> = emptyMap(),

) {

    /** What the party have seen of [level]. */
    fun visited(level: Int): Set<Location> = visited[level].orEmpty()

    /** The world with [at] on [level] counted as seen. */
    fun visiting(level: Int, at: Location): GameState =
        copy(visited = visited + (level to (visited(level) + at)))

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
     * The world with one of a champion's slots emptied and what was in it put
     * nowhere, which is [handEmptied] for a thing already put away.
     *
     * The two are separate because the hand is not a slot: what is being
     * carried is the world's, not any champion's, so a thing used out of a
     * pocket has to be taken out of that pocket and off the table both.
     */
    fun slotEmptied(champion: PartySlot, slot: CarrySlot): GameState {
        val who = champions.getOrNull(champion.index) ?: return this
        val what = who.holding(slot)
        if (!what.isSomething) return this

        return copy(
            items = items.mapIndexed { at, item ->
                if (at == what.value) item.copy(location = Item.NOWHERE) else item
            },
        ).carrying(champion, slot, ItemIndex(ItemIndex.NOTHING))
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

        // Every champion has all twenty-seven places whether or not anything
        // is in them. One carrying an empty list is one nothing has ever been
        // put on, and reaching into it for a hand is what threw.
        val slots = who.carrying.toMutableList()
        while (slots.size < CarrySlot.ALL_OF_THEM) slots += ItemIndex(ItemIndex.NOTHING)
        if (slot.index !in slots.indices) return this

        val after = who.copy(carrying = slots.also { held -> held[slot.index] = item })

        return copy(
            champions = champions.toMutableList().also {
                it[champion.index] = types
                    ?.let { known -> after.copy(armorClass = known.armourClassOf(after, items)) }
                    ?: after
            },
        )
    }

    /**
     * Whether anything is standing on that square, as [inSubLevel] sees it.
     *
     * A monster stops the party as surely as a wall does, and is refused the
     * same way: they do not walk through one, and they do not swap places with
     * one. It is also why a monster that has come up to them cannot be simply
     * walked past — it has to be gone round or killed.
     *
     * A monster of another sublevel is not somewhere else, it is nowhere: it
     * is not drawn, and so it must not block either, or the party meet a
     * corridor that is empty to look at and solid to walk into. Asking without
     * a sublevel asks about every one of them, which is what a question about
     * the maze rather than about a floor wants.
     */
    fun anythingStandingOn(at: Location, inSubLevel: Int? = null): Boolean =
        monsters.any {
            it.x == at.x && it.y == at.y && (inSubLevel == null || it.subLevel == inSubLevel)
        }

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
     * The same world with somebody put out of the party, and everything they
     * were carrying on the floor where the party stand.
     *
     * They are not killed and not kept: the flag word goes to nothing, which
     * is what says they are nobody, and the slot is free for the next person
     * to walk into. What they held is not destroyed with them — it lands on
     * the square underfoot, a corner at a time, so a party who drop somebody
     * for a stranger can pick their things back up.
     *
     * [level], [at] and [facing] are where the party are standing and which
     * way they look, which is what decides the two corners it scatters
     * between; each thing is rolled for on its own.
     */
    fun championDropped(
        whose: PartySlot,
        level: Int,
        at: Location,
        facing: Direction,
        dice: Dice,
    ): GameState {
        val who = championIn(whose) ?: return this

        val loose = who.carrying.filterIndexed { slot, what ->
            slot != CarrySlot.QUIVER.index && what.isSomething
        }

        var world = loose.fold(this) { world, what ->
            world.itemLandedAt(what, level, at, letGoOf(facing, dice))
        }

        // A quiver is a ring of arrows rather than one thing, so it empties a
        // shaft at a time. Letting go of only the one the slot names would
        // leave the rest of the ring nowhere: still strung to each other, on
        // no floor and in nobody's hands.
        var quiver = who.carrying[CarrySlot.QUIVER.index]
        var shafts = 0
        while (quiver.isSomething && shafts++ <= items.size) {
            val off = world.unstacking(quiver)
            world = off.world.itemLandedAt(quiver, level, at, letGoOf(facing, dice))
            quiver = off.head
        }

        val emptied = world.copy(
            // unstacking takes each arrow through the hand on its way out
            inHand = inHand,
            champions = champions.mapIndexed { slot, was ->
                if (slot == whose.index) {
                    was.copy(flags = ChampionFlags(0), carrying = CarrySlot.NOTHING_IN_ANY)
                } else {
                    was
                }
            },
        )

        return emptied.championsSwapped(whose, emptied.whereTheGapGoes(whose))
    }

    /**
     * Which slot the gap left by a dropped champion is moved into.
     *
     * It goes to the back, so that the party close up rather than fighting
     * around a hole in the middle of themselves. The last slot takes it where
     * somebody is standing there, and the one before it where nobody is —
     * which keeps the gap behind the living either way.
     *
     * A champion dropped from the last slot leaves the gap where it already
     * is, and nothing moves.
     */
    private fun whereTheGapGoes(dropped: PartySlot): PartySlot {
        val last = PartySlot(Champion.PARTY_SLOTS - 1)
        if (dropped == last) return dropped

        return if (championIn(last)?.inTheParty == true) last else PartySlot(last.index - 1)
    }

    /**
     * Which corner a thing let go of lands in: one of the two ahead of the
     * party, rolled between. A thing dropped starts where a thing thrown
     * does, which is why both read the corners the same way.
     */
    private fun letGoOf(facing: Direction, dice: Dice): SquarePlace =
        (if (dice.roll(1, 2, -1) == 0) ViewPlace.FAR_LEFT else ViewPlace.FAR_RIGHT)
            .onASquareFacing(facing)

    /**
     * The same world with two champions changing places in the party.
     *
     * Whole people change places, not their belongings: everything a champion
     * is goes with them, so the pair are simply written into each other's
     * slots. Which slot somebody stands in decides who is in the front rank
     * and can reach what is ahead, which is the point of moving them.
     *
     * An empty slot is a place like any other, so somebody may be moved into
     * one — that is how a party of four choose which two of them lead.
     */
    fun championsSwapped(one: PartySlot, other: PartySlot): GameState {
        if (one == other) return this
        val first = champions.getOrNull(one.index) ?: return this
        val second = champions.getOrNull(other.index) ?: return this

        return copy(
            champions = champions.mapIndexed { slot, was ->
                when (slot) {
                    one.index -> second
                    other.index -> first
                    else -> was
                }
            },
        )
    }

    /**
     * The same world with somebody taking the first free place in the party,
     * and the bones the party were carrying of theirs let go of.
     *
     * Bones are what is left of somebody who is not with the party: carried to
     * where they can be raised, they are that person again. Whoever has just
     * walked up cannot also be a pile of bones in the pack, so theirs go at
     * the moment of joining.
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
     * table it is — the number handed back, so that a script may do more
     * with it than ask whether it was there at all.
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
     * The things lying on [from] carried over to [to]: all of them, or only
     * those of [ofType]. A lever that makes a key appear does this, taking it
     * from a square off the edge of the map where it was kept.
     *
     * What is still in the air over [from] goes with them, and goes whatever
     * kind it is: [ofType] picks out what is lying on the floor, and a thing
     * in flight is carried across on the strength of being there at all. It
     * arrives still flying, so it goes on from [to] in whatever direction it
     * was going and comes to rest at the far end of that — which is the whole
     * of how a square can throw something somewhere the thrower cannot reach.
     * Carried onto another floor it is simply gone, there being nothing here
     * for it to go on flying over.
     *
     * @param onLevel the floor the party are on, which is the floor anything
     *   in flight is over. A square that shuffles things about on some other
     *   floor moves nothing that is in the air here.
     */
    fun itemsMoved(
        ofType: ItemTypeId?,
        fromLevel: Int,
        from: Location,
        toLevel: Int,
        to: Location,
        onLevel: Int = fromLevel,
    ): GameState = copy(
        items = items.map {
            val carried = it.level == fromLevel && it.location == from &&
                (ofType == null || it.type == ofType)
            if (carried) it.copy(level = toLevel, location = to) else it
        },
        inFlight = if (fromLevel != onLevel) inFlight else inFlight.mapNotNull {
            when {
                it.at != from -> it
                toLevel != onLevel -> null
                else -> it.copy(at = to)
            }
        },
    )

    /** Every monster standing on [from] moved to [to], facing as it did. */
    fun monstersMovedFrom(from: Location, to: Location): GameState = copy(
        monsters = monsters.map {
            if (it.x == from.x && it.y == from.y) it.copy(location = to) else it
        },
    )

    /**
     * What a slain [monster] leaves behind: a copy of the thing it always
     * carries, and one time in ten a copy of the thing it might. Both fall on
     * a corner of the square it died on — the one it stood on, or a corner
     * picked at random if it filled the middle.
     */
    fun whatAMonsterDrops(monster: MonsterInstance, level: Int, dice: Dice): GameState {
        val where = Location(monster.x, monster.y)
        val corner = if (monster.place.onTheFloor) monster.place else SquarePlace.of(dice.roll(1, 4, -1))

        var world = this
        if (monster.pocketItem != ItemIndex.NOTHING) {
            world = world.itemCopied(ItemIndex(monster.pocketItem), level, where, corner)
        }
        if (monster.weapon != ItemIndex.NOTHING && dice.roll(1, ONE_TIME_IN_TEN, 0) == 1) {
            world = world.itemCopied(ItemIndex(monster.weapon), level, where, corner)
        }
        return world
    }

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
    ): GameState = itemCopiedOnto(copyOf, level, at, place, overrides)?.world ?: this

    /**
     * The same, but saying which slot of the table the copy landed in.
     *
     * Needed by whoever means to do something more with it than leave it
     * there — a thing loosed down a corridor is copied onto the square it is
     * thrown from and then has to be named as the thing in the air.
     */
    fun itemCopiedOnto(
        copyOf: ItemIndex,
        level: Int,
        at: Location,
        place: SquarePlace,
        overrides: ItemOverrides = ItemOverrides(),
    ): Made? = copyOf(copyOf) {
        overrides.applyTo(it).copy(location = at, level = level, place = place)
    }

    /**
     * One thing picked up off wherever it was and put down at [at].
     *
     * Used a square at a time by whatever is in flight: a thing crossing a
     * corridor is not drawn specially, it is simply lying on a different
     * square each time it is asked about.
     */
    fun itemLandedAt(which: ItemIndex, level: Int, at: Location, place: SquarePlace): GameState {
        if (item(which) == null) return this

        return copy(
            items = items.mapIndexed { slot, item ->
                if (slot == which.value) {
                    item.copy(location = at, level = level, place = place)
                } else {
                    item
                }
            },
        )
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

    /** A thing just made, and which slot of the table it is in. */
    data class Made(val world: GameState, val slot: ItemIndex)

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
     * How many monsters are remembered on [level], or null where none are and
     * arriving would people it from its file. For reading a trace by: a floor
     * that takes the wrong list is empty of its own monsters and full of
     * somebody else's, and nothing else on screen says so.
     */
    fun remembersOn(level: Int): Int? = asTheyWereLeft[level]?.size

    /**
     * Puts the party on [level], as they left it if they have been before, and
     * as its file [places] it if they have not. [maz] is that file's walls,
     * which everything a script has not changed still comes from.
     *
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
        monsters = remembersOn(level, kinds) ?: places.map {
            it.copy(subLevel = subLevel, level = level).rolledIfKnown(kinds, dice)
        },
        mazes = if (maz == null) mazes else mazes + (level to maz),
    )

    /**
     * What is remembered of [level], or null where the party should be met by
     * what its file lists instead.
     *
     * A monster is a row of that floor's own table of species, so one whose
     * kind the floor has no row for cannot be standing on it: it is somebody
     * else's, drawn from this floor's sheets and answering to none of this
     * floor's rules. Those are dropped, and a memory that is nothing but such
     * creatures is not a memory of this floor at all — the file is a truer
     * account of it than that, so the floor peoples itself afresh.
     *
     * A floor genuinely emptied by fighting is remembered as empty, which is
     * why an empty memory is only overruled where it had nothing right in it
     * to begin with.
     *
     * A monster that names a different floor is not this floor's whatever its
     * kind says, and one list of those is not a memory of this floor at all.
     * The kinds are the weaker test of the two and are kept for the monsters
     * out of old saves, which name no floor: two floors number their species
     * from zero, so the kinds alone let one floor's creature pass as another's.
     */
    fun remembersOn(level: Int, kinds: List<MonsterProperty>): List<MonsterInstance>? {
        val left = asTheyWereLeft[level] ?: return null
        if (left.any { it.level != null && it.level != level }) return null
        if (kinds.isEmpty()) return left

        val belong = left.filter { who -> kinds.any { it.id == who.type.value } }

        return belong.takeUnless { it.isEmpty() && left.isNotEmpty() }
    }

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
     * The same world with the webs on a square cut down.
     *
     * All four sides go at once and not only the one that was swung at, so a
     * web strung across a corridor comes down whole rather than leaving the
     * far face of it standing. Each becomes the next wall along, which is what
     * is left of it: torn, still drawn, and walked through.
     *
     * @param giveWay the wall bytes that are webs, which is a level's own
     *   business — nothing about the byte itself says so.
     */
    fun websCutOn(level: Int, at: Location, giveWay: Set<WallByte>): GameState =
        WallSide.entries.fold(this) { world, side ->
            val was = world.wallByte(level, at, side)
            if (was !in giveWay) world
            else world.wallChanged(level, at, side, WallByte(was.value + 1))
        }

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
     * it works stands open for good.
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
     * to press.
     *
     * It takes its frame already a step open, and never stands shut in it. A
     * stuck door is drawn resting a little above its threshold, so a doorway
     * put there shut would drop that much before it rose, and the first thing
     * forcing a door would be seen to do is close it.
     */
    fun forcedOutOfItsFrame(level: Int, at: Location, side: WallSide): GameState {
        val door = Maz.WallType.Door(
            doorIndex = DoorIndex(if (wallByte(level, at, side) == FIRST_KIND) 0 else 1),
            hasButton = false,
            state = 1,
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
     * The engine refuses the same four ways: never under the party, never
     * onto a square already holding as many as it can, never beside one that
     * has no room to be shared ([roomBesideThem]), and never without a free
     * slot. The slot is not bookkeeping — it decides which of its sheet's
     * color schemes the monster is painted in.
     *
     * With every slot in use it does not refuse but makes room — see
     * [madeRoom].
     *
     * @param subLevel the one the party are in, which a new monster joins.
     * @param level the floor it is conjured on, which it belongs to from here
     *   on — see [MonsterInstance.level].
     */
    fun monsterCreated(
        spawn: CreateMonster,
        subLevel: Int = 0,
        kinds: List<MonsterProperty> = emptyList(),
        dice: Dice = Dice.random,
        level: Int? = null,
    ): GameState {
        val crowd = monstersOn(spawn.location)

        if (spawn.location == party.position) return this
        if (crowd >= MAX_MONSTERS_PER_SQUARE) return this
        if (crowd > 0 && !roomBesideThem(spawn, kinds)) return this

        val taken = monsters.map { it.index }.toSet()
        val free = (0 until MONSTER_SLOTS).map(::MonsterSlot).firstOrNull { it !in taken }

        val (world, slot) = when {
            free != null -> this to free
            else -> madeRoom(subLevel, level, dice) ?: return this
        }

        return world.copy(
            monsters = world.monsters + MonsterInstance
                .spawnedBy(spawn, slot, subLevel, level)
                .rolledIfKnown(kinds, dice),
        )
    }

    /**
     * The world with one thing taken out of it to make room, and the slot
     * that frees — or null where there was nothing that could be taken.
     *
     * A floor holds thirty and no more, and a script asking for one with all
     * thirty standing is not refused: whichever of them is furthest off is
     * removed instead, so that whatever is conjured in front of the party
     * always arrives. Nobody is credited with the kill — it dies unwatched
     * and unearned — but it leaves its belongings on the floor where it
     * stood, exactly as one cut down would.
     *
     * Only what a script conjured may go; see [MonsterInstance.conjured].
     * And anything not where the party are goes before anything that is,
     * because a monster on another floor or another sublevel is not far away
     * so much as elsewhere: the two share a coordinate space and nothing
     * else, and the distance between them means nothing.
     *
     * One standing on the party's own square is never the one taken, however
     * little else there is to choose from. That falls out of the engine
     * measuring furthest from a starting distance of nought, and reads as a
     * quirk rather than an intention, but it is the behaviour.
     */
    private fun madeRoom(
        subLevel: Int,
        level: Int?,
        dice: Dice,
    ): Pair<GameState, MonsterSlot>? {
        val loose = monsters.filter { it.conjured }

        val going = loose.firstOrNull { it.subLevel != subLevel || it.level != level }
            ?: loose
                .filter { it.location.blocksFrom(party.position) > 0 }
                .maxByOrNull { it.location.blocksFrom(party.position) }
            ?: return null

        val emptied = going.level?.let { whatAMonsterDrops(going, it, dice) } ?: this

        return emptied.copy(monsters = emptied.monsters - going) to going.index
    }

    /**
     * Whether a square that already holds monsters has room for one more.
     *
     * How many fit is the kind's own [MonsterSize.toASquare] — four of the
     * small, two of the middling, one of anything that fills a square — and
     * they must all be of a size with each other, each in a corner of its
     * own. One in the middle has spread over the whole square and leaves no
     * corner free.
     *
     * Killing frees a place, so a square down to two takes two more.
     *
     * A kind with no properties to read has no size, which refuses.
     */
    private fun roomBesideThem(spawn: CreateMonster, kinds: List<MonsterProperty>): Boolean {
        fun sizeOf(type: MonsterTypeId) = kinds.getOrNull(type.value)?.size

        val size = sizeOf(spawn.type) ?: return false
        if (spawn.place == SquarePlace.MIDDLE) return false

        val there = monsters.filter { it.x == spawn.location.x && it.y == spawn.location.y }

        return when {
            there.any { it.place == SquarePlace.MIDDLE } -> false
            there.any { sizeOf(it.type) != size } -> false
            there.size >= size.toASquare -> false
            else -> there.none { it.place == spawn.place }
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
    fun monsterHurt(
        slot: MonsterSlot,
        by: Damage,
        kinds: List<MonsterProperty> = emptyList(),
        types: ItemTypes? = null,
        dice: Dice = Dice.random,
    ): GameState {
        val hit = monsters.firstOrNull { it.index == slot } ?: return this

        if (!hit.couldBeHurt) {
            Logger.w(TAG) { "Monster ${slot.value} was never rolled for, so nothing can hurt it" }
            return this
        }

        val kind = kinds.firstOrNull { it.id == hit.type.value }
        if (kind?.burstsWhenHurt == true) return burstOf(hit, types, dice)

        val after = hit.hurt(by)
        return copy(
            monsters = if (after.hitPoints.current <= 0) monsters - hit
            else monsters.map { if (it.index == slot) after else it },
        )
    }

    /**
     * The world with a thing that goes off having gone off, which it does
     * however lightly it was touched.
     *
     * How much it was hurt by never comes into it: it dies of being hurt at
     * all, and what it costs is a matter of where it was standing. Set off
     * beside the party it takes six dice of six off every one of them, a
     * throw halving it, and the flash is drawn on the party's own square
     * rather than on its. Set off further away it is only a flash, and one
     * on a square they cannot see is nothing at all.
     *
     * Everybody means everybody: the burst reaches champions who are already
     * down, as a trap's does.
     */
    private fun burstOf(spore: MonsterInstance, types: ItemTypes?, dice: Dice): GameState {
        val beside = spore.location.squaresFrom(party.position) < TOO_FAR_TO_CATCH
        val gone = copy(
            monsters = monsters - spore,
            bursting = bursting + Burst.of(
                at = if (beside) party.position else spore.location,
                dice = dice,
                inYourFace = beside,
            ),
        )

        if (!beside) return gone

        return gone.hurtBy(Harm.of(A_SPORE_GOING_OFF, gone, types, dice))
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
     * The world a little further from its last meal: everyone still standing
     * is one emptier.
     *
     * Walking is what makes a party hungry: standing still costs them nothing,
     * and an empty stomach only costs anything once they lie down to sleep on
     * it.
     */
    fun hungrier(): GameState = copy(
        champions = champions.map {
            if (!it.inTheParty || it.deadForGood || it.food.value <= 0) it
            else it.copy(food = Food(it.food.value - 1))
        },
    )

    /**
     * How many of the party are carrying a thing of [ofType] worth [worth].
     *
     * Champions, not things: somebody carrying three of them counts once. A
     * puzzle asks this to see whether the party between them hold what it
     * wants — a piece each, or one spellbook anywhere among them — and it
     * counts every slot a champion can fill, hands and pack and worn alike.
     *
     * Either half of the question may be left open with [ANYTHING], which asks
     * only about the other: any value of one kind, or any kind of one value.
     */
    fun championsCarrying(ofType: ItemTypeId, worth: Int): Int = champions.count { champion ->
        champion.inTheParty && champion.carrying.any { slot ->
            item(slot)?.let {
                (ofType.value == ANYTHING || it.type == ofType) &&
                    (worth == ANYTHING || it.value == worth)
            } == true
        }
    }

    /**
     * The world with [what] left on a champion by a blow, or null where
     * nothing was left.
     *
     * Three things stop it, and each is asked in turn: there being nothing
     * left of them to work on, their already being in that state, and their
     * making the throw. Null rather than an unchanged world, so a caller can
     * tell the difference between a venom that took and one that did not —
     * only the first is worth saying out loud.
     */
    fun championLeftWith(
        whose: PartySlot,
        what: WhatABlowLeaves,
        dice: Dice,
        // A spell names its own throw. Hold person leaves the same paralysis a
        // monster's grip does and is shrugged off by a different one, so which
        // throw is asked belongs to whatever did it rather than to the state
        // it leaves behind.
        against: SavingThrow? = what.thrownAgainst,
    ): GameState? {
        val who = champions.getOrNull(whose.index) ?: return null
        if (!who.canBeHurt) return null
        if (what.alreadyOn(who)) return null
        if (against != null && who.saves(against, dice)) return null

        return copy(
            champions = champions.toMutableList().also { it[whose.index] = what.leftOn(who) },
            holding = what.holdsFor
                ?.let { holding + Holding(whose, what, it.value) }
                ?: holding,
        )
    }

    /**
     * Every grip one step nearer to letting go, and whoever it let go of.
     *
     * Only what wears off is here — venom and stone are not on any clock, and
     * a champion has to be seen to for either of those.
     */
    fun gripsStepped(by: Ticks = CLOCK_STEP): Pair<GameState, List<PartySlot>> {
        val stepped = holding.map { it.copy(ticksLeft = it.ticksLeft - by.value) }
        val over = stepped.filter { it.ticksLeft <= 0 }

        val freed = over.fold(this) { world, grip ->
            val who = world.champions.getOrNull(grip.whose.index) ?: return@fold world
            world.copy(
                champions = world.champions.toMutableList().also {
                    it[grip.whose.index] = who.paralysed(false)
                },
            )
        }

        return freed.copy(holding = stepped - over.toSet()) to over.map { it.whose }
    }

    /**
     * [whose] poisoned, or cured of it.
     *
     * Poisoning somebody already poisoned does nothing rather than starting
     * again: the game refuses to refresh it, so a second bite from the same
     * spider is not a second dose to be worked off.
     */
    fun championPoisoned(whose: PartySlot, yes: Boolean = true): GameState {
        val who = champions.getOrNull(whose.index) ?: return this
        if (who.poisoned == yes) return this

        return copy(
            champions = champions.toMutableList().also { it[whose.index] = who.poisoned(yes) },
        )
    }

    /**
     * Everyone the poison is still working on, in the order they stand.
     *
     * Being down is no escape from it: somebody lying there unconscious goes
     * on being poisoned, and the venom is perfectly capable of finishing them.
     * Only being past raising ends it — not because the hold lets go, since
     * nothing washes it out of somebody waiting to be raised, but because
     * there is nothing further to take.
     *
     * This is asked both to hurt them and to decide whether the clock has
     * anything left to do, which is why it has to stop somewhere.
     */
    val poisoned: List<PartySlot>
        get() = champions.indices
            .map(::PartySlot)
            .filter {
                champions[it.index].let { who ->
                    who.poisoned && who.inTheParty && !who.deadForGood
                }
            }

    /** [whose] with a full stomach outright, which is what vitality does. */
    fun championSated(whose: PartySlot): GameState {
        val who = champions.getOrNull(whose.index) ?: return this
        return copy(
            champions = champions.toMutableList().also {
                it[whose.index] = who.copy(food = Food(FULL_STOMACH))
            },
        )
    }

    /** [whose] the fuller for eating [by], up to a full stomach. */
    fun championFed(whose: PartySlot, by: Int): GameState {
        val who = champions.getOrNull(whose.index) ?: return this
        return copy(
            champions = champions.toMutableList().also {
                it[whose.index] = who.copy(food = Food((who.food.value + by).coerceAtMost(FULL_STOMACH)))
            },
        )
    }

    /**
     * [whose] mended by [points], up to what they started the day able to take
     * and no further. Nothing here raises the dead: somebody at nothing left
     * is mended like anybody else, which is what a potion poured down them
     * does, and being past raising is a separate question this does not ask.
     */
    fun championMended(whose: PartySlot, points: Int): GameState {
        val who = champions.getOrNull(whose.index) ?: return this
        return copy(
            champions = champions.toMutableList().also {
                it[whose.index] = who.copy(
                    hitPoints = who.hitPoints.copy(
                        current = (who.hitPoints.current + points)
                            .coerceAtMost(who.hitPoints.max),
                    ),
                )
            },
        )
    }

    /**
     * Whether that champion is wearing [ring].
     *
     * Only the two ring slots count. One in the pack does nothing and one in
     * a hand is being carried rather than worn, so a party who found the ring
     * and never put it on fall as far as anybody else.
     */
    fun isWearing(whose: PartySlot, ring: Ring, types: ItemTypes?): Boolean {
        val who = champions.getOrNull(whose.index) ?: return false

        return CarrySlot.RINGS.any { slot ->
            val worn = item(who.holding(slot)) ?: return@any false
            types?.ring(worn) == ring
        }
    }

    /**
     * Whether the party are lost: not one of them still on their feet.
     *
     * Asked of the whole party rather than of one, and asked after anything
     * that takes hit points — a blow, a bolt, venom, a fall, a script. A
     * party can be lost without a monster in the room.
     *
     * A world with no party in it at all is not a lost one: it is a game that
     * has not finished starting, and the roster arrives a moment after the
     * floor does.
     */
    val nobodyIsStanding: Boolean
        get() = champions.any { it.inTheParty } && champions.none { it.onTheirFeet }

    /**
     * The world with one champion [by] hit points worse off.
     *
     * Nothing else happens to them here: going down at nothing left and being
     * past raising at ten below are what the panel already reads off the
     * number, so taking it away is the whole of the change.
     */
    fun championHurt(whose: PartySlot, by: Damage): GameState {
        if (!by.landed) return this
        val who = champions.getOrNull(whose.index) ?: return this

        // Below nothing is where a champion lies dying rather than standing,
        // and ten below is as far down as it goes: past that they are dead
        // for good, and there is no deader. A blow big enough to take them
        // further is simply a blow that killed them.
        val left = (who.hitPoints.current - by.points)
            .coerceAtLeast(Champion.BEYOND_RAISING)

        return copy(
            champions = champions.toMutableList().also {
                it[whose.index] = who.copy(hitPoints = who.hitPoints.copy(current = left))
            },
            // A second blow before the first has faded shows its own number
            // rather than the two added up.
            showingDamage = showingDamage.filterNot { it.whose == whose } +
                DamageShown(whose, by, DamageShown.WHILE_IT_SHOWS.value),
        )
    }

    /**
     * The world with a blow put up on the portraits without anybody losing
     * anything for it.
     *
     * For a blow already dealt somewhere the clock could not reach — a
     * script's, which is handed back the world it was given and so cannot be
     * left holding the fading.
     */
    fun blowsShown(blows: Map<PartySlot, Damage>): GameState = copy(
        showingDamage = showingDamage.filterNot { it.whose in blows.keys } +
            blows.filterValues { it.landed }.map { (whose, amount) ->
                DamageShown(whose, amount, DamageShown.WHILE_IT_SHOWS.value)
            },
    )

    /** What is showing on that champion's portrait, if anything. */
    fun damageShownOn(whose: PartySlot): Damage? =
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
    fun rousedBy(slot: MonsterSlot) = copy(
        monsters = monsters.map {
            if (it.index == slot || it.standingBy) it.copy(provoked = true) else it
        },
    )

    /** The world with those monsters a frame further through their swing. */
    fun monstersStriking(slots: List<MonsterSlot>) = copy(
        monsters = monsters.map { if (it.index in slots) it.swingingOn() else it },
    )

    /** The world with one monster standing somewhere else, facing [way]. */
    fun monsterMoved(slot: MonsterSlot, to: Location, way: Direction, place: SquarePlace) = copy(
        monsters = monsters.map {
            if (it.index == slot) {
                it.copy(location = to, direction = way, place = place)
            } else {
                it
            }
        },
    )

    /** The world with one monster further round its own loop of looking about. */
    fun monsterStrayed(slot: MonsterSlot, straying: Straying) = copy(
        monsters = monsters.map { if (it.index == slot) it.copy(straying = straying) else it },
    )

    /** The world with one monster standing somewhere else on its own square. */
    fun monsterShifted(slot: MonsterSlot, place: SquarePlace) = copy(
        monsters = monsters.map { if (it.index == slot) it.copy(place = place) else it },
    )

    /** The same for a squareful of them, standing aside for one arriving. */
    fun monstersShifted(places: Map<MonsterSlot, SquarePlace>): GameState {
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
    fun monsterTurned(slot: MonsterSlot, way: Direction) = copy(
        monsters = monsters.map {
            if (it.index == slot) it.copy(direction = way) else it
        },
    )

    /** The world with those monsters turned to face where they are told. */
    fun monstersTurnedToFace(ways: List<Pair<MonsterSlot, Direction>>): GameState {
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
     * Whose blow lands on the frame this world is about to be drawn as: those
     * whose arm has just reached its full stretch.
     *
     * Asked of the world after the swings have moved on rather than before,
     * so that a champion is marked while the arm that marked them is still
     * out. Asking first lands the blow as the pose ends, which puts the splat
     * up a whole frame later, beside a monster already back at rest.
     */
    val landingThisFrame: List<MonsterSlot>
        get() = monsters.filter { it.striking == MonsterPose.ATTACK_B }.map { it.index }

    /**
     * Whether anything the screen shows has changed since [was] — which is
     * what says whether a tick of the clock is worth redrawing for.
     *
     * The party's own step is not on the list: nothing on screen says how far
     * through it they are, only whether the next one is refused.
     */
    fun somethingMoved(was: GameState): Boolean =
        monsters != was.monsters ||
            champions != was.champions ||
            // A bolt crossing an empty corridor moves nothing else at all, and
            // without this it would cross it without being drawn once.
            inFlight != was.inFlight ||
            bursting != was.bursting

    /** Whether any monster is part way through its own swing. */
    val anythingSwinging: Boolean get() = monsters.any { it.striking != null }

    /**
     * Whether the party are pinned by an arm already coming down at them.
     *
     * Once a monster in front of them has begun its swing the blow is theirs,
     * and they may do nothing at all until it lands — so the wind-up
     * announces a hit rather than offering a chance to duck.
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

    /** The world with a casting's sparks begun, or begun again. */
    fun sparksBegun() = copy(sparkling = SparksInTheRoom())

    /** And a frame on, which is how they go out. */
    fun sparksStepped() = copy(sparkling = sparkling?.next())

    /** The world with that hand put out of use for as long as a swing costs. */
    fun handSwung(whose: PartySlot, hand: CarrySlot, came: WhatTheBlowCameTo) = copy(
        recovering = recovering.filterNot { it.whose == whose && it.hand == hand } +
            HandRecovering(whose, hand, came.wait.value, came),
    )

    /**
     * And with it put out of use for the shorter wait a wand or a scroll
     * costs, which reports nothing.
     *
     * It is what stops a wand being spent as fast as the mouse can be clicked.
     * Shorter than a swing, and nothing is said at the end of it: a blow has
     * an outcome worth reading in the slot, where reading something aloud has
     * only whatever the room makes of it.
     */
    fun handCast(whose: PartySlot, hand: CarrySlot) = copy(
        recovering = recovering.filterNot { it.whose == whose && it.hand == hand } +
            HandRecovering(whose, hand, HandRecovering.AFTER_CASTING.value, came = null),
    )

    /** One more thing crossing the room, whoever loosed it. */
    fun inTheAir(loosed: Projectile) = copy(inFlight = inFlight + loosed)

    /**
     * The world with what was read from worn down by the reading — see
     * [WhatCastingCosts].
     *
     * A wand on its last use goes the way a scroll does, so the hand is empty
     * rather than holding something that answers nothing.
     */
    fun castOutOf(whose: PartySlot, hand: CarrySlot, types: ItemTypes): GameState {
        val held = item(champions.getOrNull(whose.index)?.holding(hand) ?: return this)
            ?: return this

        return when (types.whatCastingCosts(held)) {
            WhatCastingCosts.Nothing -> this
            WhatCastingCosts.AllOfIt -> slotEmptied(whose, hand)
            WhatCastingCosts.OneCharge ->
                if (held.chargesLeft <= 1) slotEmptied(whose, hand)
                else withItemChanged(held) { it.oneChargeSpent() }
        }
    }

    private fun withItemChanged(what: Item, worn: (Item) -> Item) = copy(
        items = items.map { if (it === what) worn(it) else it },
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

    /**
     * The world a script left, but with the things a clock owns taken from
     * [live] rather than from the script.
     *
     * A hand coming back to rest and a blow still showing are none of a
     * script's business, and they tick on their own clock. A script reads the
     * world when it starts and writes it back when it ends — which, for a
     * speech that waits to be read, can be much later — so without this it
     * puts back the stale hands it began with and freezes them there, the
     * clock that was emptying them already stopped.
     */
    fun asAScriptLeaves(live: GameState) = copy(
        recovering = live.recovering,
        showingDamage = live.showingDamage,
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
        visited = visited.map { (level, squares) -> VisitedFloor(level, squares) },
        inFlight = inFlight,
    )

    companion object {
        private const val TAG = "GameState"


        /** How often the thing a monster only might carry actually drops. */
        private const val ONE_TIME_IN_TEN = 10

        /** Left open in a question about a thing, asking nothing of that half. */
        const val ANYTHING = -1

        /** As full as a champion's stomach goes. */
        private const val FULL_STOMACH = 100

        /** What a pile of somebody's bones is, as the item table counts kinds. */
        private val BONES = ItemTypeId(33)

        /**
         * How long the party take over a step or a turn. Transcribed,
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
            // A floor whose monsters name no floor comes out of a save written
            // before they did, and nothing in it can say whether it is that
            // floor's list or another's wrongly filed under its number. Only
            // the floor being stood on is vouched for — those monsters are the
            // live world, whatever the map says — and the rest are forgotten,
            // to be read from their files again.
            asTheyWereLeft = saved.leftBehind.filterValues { left ->
                left.all { it.level != null }
            } + (on to saved.monsters.map { it.copy(level = on) }),
            changedWalls = saved.changedWalls.associate {
                WallAt(it.level, it.at, it.side) to it.to
            },
            // A save from when a map was kept per sublevel has a row for each
            // of them, and they are one floor's map between them.
            visited = saved.visited.groupBy { it.level }.mapValues { (_, rows) ->
                rows.flatMapTo(mutableSetOf()) { it.squares }
            },
            // Back into the air where they were. A save written before these
            // were kept has none, and its party walk into a room that has
            // stopped rather than one that never started.
            inFlight = saved.inFlight,
        )

        /**
         * The one wall a forced door leaves behind as a doorway of the level's
         * first kind. Every other stuck door becomes one of the second.
         */
        private val FIRST_KIND = WallByte(51)

        /**
         * How far a bursting spore has to be for the party to be clear of
         * it: on their square or next to it and they are caught, and one
         * square further out is already too far.
         *
         * The engine measures this its own way, adding half the smaller of
         * the two gaps to the larger. That parts company with counting
         * squares further out, but not here — for "on it or beside it" the
         * two agree exactly.
         */
        private const val TOO_FAR_TO_CATCH = 2

        /**
         * What a spore going off beside the party costs them: six dice of
         * six each, a throw against a wand halving it.
         *
         * Transcribed rather than derived, and dealt to everybody — which is
         * what the -1 says.
         */
        private val A_SPORE_GOING_OFF = DamageDealt(
            charIndex = -1,
            times = 6,
            itemOrPips = 6,
            useStrModifierOrBase = 0,
            flags = 8,
            savingThrowType = 1,
            savingThrowEffect = 0,
        )

        private const val MAX_MONSTERS_PER_SQUARE = 7

        /** How many monsters a level can have at once, placed and spawned together. */
        private const val MONSTER_SLOTS = 30
    }
}
