package pl.pelotasplus.eyeofbeholder.data.model

/**
 * Fear: everything on the square ahead frightened off.
 *
 * Unlike the spells that take one creature, this reaches every one standing
 * there, each asked on its own whether it escapes. One that does not turns its
 * back where it stands and spends its next walks getting away — see
 * [MonsterInstance.scaredOff]. Nothing is said either way: what the party see
 * is backs.
 */
class Frightening(
    private val kinds: List<MonsterProperty> = emptyList(),
    private val dice: Dice = Dice.random,
) {
    fun castAt(world: GameState): GameState {
        val ahead = world.party.facing.oneStepFrom(world.party.position)

        return world.copy(
            monsters = world.monsters.map { monster ->
                if (monster.location != ahead || !frightens(monster)) monster
                else monster.scaredOff(world.party.position)
            },
        )
    }

    /**
     * The throw comes first and the rest after it, which is the game's order
     * and decides what the dice do for whatever is asked next: a creature that
     * cannot be frightened still rolls against the spell before that is known.
     *
     * Something still waiting to see what the party do is not frightened —
     * until it has been roused, which is the end of waiting for all of them.
     *
     * Stone is ours to refuse. Frightening a statue would turn it round on the
     * spot, which nothing should see a statue do.
     */
    private fun frightens(monster: MonsterInstance): Boolean {
        val kind = kinds.firstOrNull { it.id == monster.type.value } ?: return false
        if (kind.escapesASpellAimedAtIt(dice)) return false

        return !kind.immunities.neverAfraid &&
            !monster.isHeld &&
            !(monster.standingBy && !monster.provoked) &&
            !monster.stone
    }
}
