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
 * Only what is modelled lives here. Items lying on a square are the next to
 * arrive, along with anything else the still unanswered conditions need.
 */
data class GameState(
    val party: PartyState,
    val monsters: List<MonsterInstance> = emptyList(),
    val flags: GameFlags = GameFlags(),
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
            ?: mazes[level]?.squareOrNull(at)?.getWall(side)?.asByte()
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

    fun isLevelFlagSet(level: Int, bit: FlagBit) = flags.forLevel(level).isSet(bit)

    fun isGlobalFlagSet(bit: FlagBit) = flags.global.isSet(bit)

    /** One face of one square of one level. */
    data class WallAt(val level: Int, val at: Location, val side: WallSide)

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
