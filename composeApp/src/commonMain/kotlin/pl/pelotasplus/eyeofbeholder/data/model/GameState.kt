package pl.pelotasplus.eyeofbeholder.data.model

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

    private companion object {
        const val MAX_MONSTERS_PER_SQUARE = 7
    }
}
