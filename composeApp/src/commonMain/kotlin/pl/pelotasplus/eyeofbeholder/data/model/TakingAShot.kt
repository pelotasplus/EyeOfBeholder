package pl.pelotasplus.eyeofbeholder.data.model

/**
 * Whether a monster shoots this turn, and with what.
 *
 * Shooting is the first thing a monster tries on a turn it might act, and it
 * is refused far more often than it is allowed: it needs shots left, a wait
 * that has run long enough, the party within three squares and straight
 * ahead, and nothing of its own kind standing in the way. Meeting all of
 * that spends the turn, and a monster that shoots does not also step.
 *
 * Kept apart from taking a turn because the question is worth asking on its
 * own — five conditions, of which four are cheap and one walks a corridor.
 * Once it is allowed, [loosed] puts the thing in the air.
 */
class TakingAShot(
    private val sublevel: SubLevel,
    private val level: Int,
    private val kinds: List<MonsterProperty> = emptyList(),
    private val dice: Dice = Dice.random,
) {
    /**
     * What a monster does about shooting: nothing, wait a little longer, or
     * loose one of the things its kind carries.
     */
    sealed interface Shot {
        /** It cannot shoot at all, or not from where it stands. */
        data object NotThisTurn : Shot

        /**
         * It could have, but its wait has not run out. The wait is longer by
         * one for next time, which is the whole of why it ever ends.
         */
        data class StillWaiting(val monster: MonsterInstance) : Shot

        /**
         * It shoots. [monster] is it with the shot spent and its wait begun
         * again, and [weapon] is what it reached for — negative for a thing
         * it throws, and otherwise one of the conjured kinds.
         */
        data class Looses(val monster: MonsterInstance, val weapon: Int) : Shot
    }

    /** What [monster] does about shooting at the party in [world]. */
    fun taken(world: GameState, monster: MonsterInstance): Shot {
        val kind = kinds.firstOrNull { it.id == monster.type.value } ?: return Shot.NotThisTurn
        if (kind.remoteWeapons.isEmpty()) return Shot.NotThisTurn
        if (monster.shotsLeft == 0) return Shot.NotThisTurn

        // Raced against a die rather than counted down, so the wait is short
        // and never quite the same twice.
        if (dice.roll(1, 3, 0) > monster.stepsTillItShoots) {
            return Shot.StillWaiting(
                monster.copy(stepsTillItShoots = monster.stepsTillItShoots + 1),
            )
        }

        if (!canSee(world, monster)) return Shot.NotThisTurn

        val which = whichWeapon(kind, monster)

        return Shot.Looses(
            monster = monster.copy(
                shotsLeft = monster.shotsLeft.spent(),
                stepsTillItShoots = 0,
                nextRemoteWeapon = which.second,
            ),
            weapon = kind.remoteWeapons[which.first],
        )
    }

    /**
     * The world with what the monster reached for now in the air.
     *
     * The sign of the weapon decides which of two quite different things it
     * is. A negative number names an item in the level's own table, and what
     * flies is a copy of it — a real thing, which lands where it stops and can
     * be picked up and thrown back. Anything else is a spell, which is nothing
     * you could pick up and is drawn as whichever bolt its kind uses.
     *
     * A weapon with neither meaning leaves nothing in the air. The mind blast
     * is the one that matters: it is a number in the list with no projectile
     * behind it at all, reaching the whole party where they stand.
     */
    fun loosed(world: GameState, shot: Shot.Looses): GameState {
        val monster = shot.monster

        if (shot.weapon < 0) {
            return world.thrown(monster, ItemIndex(-shot.weapon))
        }

        val spell = MonsterSpell.of(shot.weapon) ?: return world
        if (spell.looksLike == null) return world

        return world.inTheAir(
            Projectile(
                what = null,
                at = monster.location,
                place = monster.place,
                going = monster.direction,
                squaresLeft = spell.reach,
                thrownBy = Projectile.Thrower.AMonster(monster.index),
                harm = Projectile.Harm.ofASpell(spell.bursts),
                spell = spell,
            ),
        )
    }

    /**
     * A copy of one of the level's items, thrown. Where there is no room in
     * the table for the copy, nothing is thrown and the shot is simply lost.
     */
    private fun GameState.thrown(monster: MonsterInstance, copyOf: ItemIndex): GameState {
        val made = itemCopiedOnto(
            copyOf = copyOf,
            level = level,
            at = monster.location,
            place = monster.place,
        ) ?: return this

        return made.world.inTheAir(
            Projectile(
                what = made.slot,
                at = monster.location,
                place = monster.place,
                going = monster.direction,
                thrownBy = Projectile.Thrower.AMonster(monster.index),
            ),
        )
    }

    private fun GameState.inTheAir(loosed: Projectile) = copy(inFlight = inFlight + loosed)

    /**
     * Whether the party are somewhere this monster can shoot them: near
     * enough, straight ahead of it, and with the way clear.
     */
    private fun canSee(world: GameState, monster: MonsterInstance): Boolean {
        val party = world.party.position
        if (monster.location.blocksFrom(party) > AS_FAR_AS_IT_SHOOTS) return false

        // Straight ahead and nowhere else: a monster does not shoot round a
        // corner, and does not turn to do it either.
        var square = monster.direction.oneStepFrom(monster.location)
        var crossed = 0
        val facing = monster.direction.wallSideFacingBack

        while (square != party) {
            if (crossed++ > AS_FAR_AS_IT_SHOOTS) return false
            if (!sublevel.canBeReachedOnto(world.wall(level, square, facing))) return false

            // Nor over the heads of its own kind.
            if (world.monstersOn(square) > 0) return false

            square = monster.direction.oneStepFrom(square)
        }

        return sublevel.canBeReachedOnto(world.wall(level, square, facing))
    }

    /**
     * Which of the kind's weapons it reaches for, and which it will reach for
     * after that.
     *
     * One kind of monster works down its list in turn and another picks at
     * random; anything else never gets past the first.
     */
    private fun whichWeapon(kind: MonsterProperty, monster: MonsterInstance): Pair<Int, Int> {
        val count = kind.remoteWeapons.size

        return when (kind.remoteWeaponChangeMode) {
            IN_TURN -> {
                val which = monster.nextRemoteWeapon % count
                which to (which + 1) % count
            }

            AT_RANDOM -> dice.roll(1, count, -1).coerceIn(0, count - 1) to monster.nextRemoteWeapon
            else -> 0 to monster.nextRemoteWeapon
        }
    }

    private companion object {
        /** How far off the party may be. Transcribed. */
        const val AS_FAR_AS_IT_SHOOTS = 3

        const val IN_TURN = 1
        const val AT_RANDOM = 2
    }
}

/** A count of shots that never runs down. */
const val SHOOTS_FOREVER = 255

/** One shot fewer, which is no fewer at all for whatever shoots forever. */
private fun Int?.spent(): Int? = if (this == SHOOTS_FOREVER) this else this?.minus(1)
