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
) {
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
    fun monsterCreated(spawn: CreateMonster): GameState {
        val taken = monsters.map { it.index }.toSet()
        val slot = (0 until MONSTER_SLOTS).firstOrNull { it !in taken }

        return when {
            spawn.location == party.position -> this
            monstersOn(spawn.location) >= MAX_MONSTERS_PER_SQUARE -> this
            slot == null -> this
            else -> copy(monsters = monsters + MonsterInstance.spawnedBy(spawn, slot))
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

    private companion object {
        const val MAX_MONSTERS_PER_SQUARE = 7

        /** How many monsters a level can have at once, placed and spawned together. */
        const val MONSTER_SLOTS = 30
    }
}
