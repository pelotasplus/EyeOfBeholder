package pl.pelotasplus.eyeofbeholder.data.model

/**
 * A monster working out which way to go, given somewhere it wants to be.
 *
 * There is no route and nothing is planned: it looks at where it wants to be,
 * tries the way that points at it, and fans outwards until something is not a
 * wall. So a monster walks into dead ends and back out of them, which is what
 * a party outrun.
 */
class MonsterPathing(
    private val stepping: MonsterStepping,
    private val kinds: List<MonsterProperty>,
    private val dice: Dice = Dice.random,
) {

    /**
     * Which way a monster tries first when it cannot go straight at what it
     * wants.
     *
     * The original alternates this every eleventh step taken by anything at
     * all, so that two monsters given the same problem do not solve it the
     * same way for ever — which is all that stops a pack walking as one body.
     */
    enum class WayRound(val fan: List<Int>) {
        RIGHT_FIRST(listOf(0, 1, -1, 2, -2, 3, -3, 4)),
        LEFT_FIRST(listOf(0, -1, 1, -2, 2, -3, 3, -4)),
    }

    /** A monster that has arrived getting its feet where its arm reaches. */
    fun shuffling(world: GameState, monster: MonsterInstance) =
        stepping.shuffleOn(world, monster)

    /**
     * One step of [monster]'s towards [destination].
     *
     * Nothing here knows whether the destination is the party, the far end of
     * a corridor, or somewhere it picked at random.
     */
    fun towards(
        world: GameState,
        monster: MonsterInstance,
        destination: Location,
        wayRound: WayRound,
    ): MonsterStepping.Stepped {
        val from = Location(monster.x, monster.y)
        val bearing = Bearing.from(from, destination) ?: return MonsterStepping.Stepped.Refused

        if (bearing.oneStepFrom(from) == destination) {
            arrivingIn(world, monster, from, bearing)?.let { return it }
        }

        return fanningOut(world, monster, from, bearing, wayRound)
    }

    /**
     * The destination is the very next square, so the walk is over.
     *
     * A monster that has arrived turns to face the square and stops short of
     * it rather than stepping on. That is the whole of how one comes to a halt
     * beside the party facing them, in reach and ready to swing, instead of
     * trying to stand where they are standing.
     *
     * Reaching a diagonal one is a step it still has to take, since nothing
     * walks a diagonal: it takes whichever of the two cardinal halves it is
     * not already facing, and a kind that swerves does that only three times
     * in four, so two arriving together come from different sides.
     *
     * Null where the sidestep is not on, which leaves the fan to answer.
     */
    private fun arrivingIn(
        world: GameState,
        monster: MonsterInstance,
        from: Location,
        bearing: Bearing,
    ): MonsterStepping.Stepped? {
        bearing.asDirection?.let { way ->
            return stepping.step(world, monster, facing = way)
        }

        val sideways = SIDESTEP[(bearing.eighths - 1) / 2][monster.direction.ordinal] ?: return null
        if (kind(monster)?.comesInSideways == true && dice.roll(1, 4, 0) >= 4) return null

        // It sidles: still facing the way it was, so that arriving beside the
        // party leaves it one turn from swinging rather than two.
        val stepped = stepping.step(world, monster, onto = sideways.oneStepFrom(from))
        return stepped.takeIf { it !is MonsterStepping.Stepped.Refused }
    }

    /**
     * Everything else: swing the facing towards the bearing, then try each of
     * the four ways in turn, nearest that heading first.
     *
     * The swing is by two eighths towards a cardinal bearing and one towards a
     * diagonal, which is what puts the fan's even steps onto squares rather
     * than onto the diagonals between them.
     */
    private fun fanningOut(
        world: GameState,
        monster: MonsterInstance,
        from: Location,
        bearing: Bearing,
        wayRound: WayRound,
    ): MonsterStepping.Stepped {
        var heading = Bearing.of(monster.direction)
        val off = heading.clockwiseTo(bearing)

        if (off != 0) {
            val by = if (bearing.isCardinal) 2 else 1
            heading = heading.turned(if (off >= 5) -by else by)
        }

        wayRound.fan.forEach { round ->
            val way = heading.turned(round).asDirection ?: return@forEach
            val stepped = stepping.step(world, monster, way.oneStepFrom(from), way)
            if (stepped !is MonsterStepping.Stepped.Refused) return stepped
        }

        return MonsterStepping.Stepped.Refused
    }

    private fun kind(monster: MonsterInstance): MonsterProperty? =
        kinds.firstOrNull { it.id == monster.type.value }

    private companion object {
        /**
         * Which way to come at a square that lies diagonally, by the diagonal
         * and by the way the monster faces. From the original; a monster
         * facing away from the diagonal has no answer here and fans out
         * instead.
         *
         * Rows are the four diagonals in compass order from north-east;
         * columns are the four facings in the order [Direction] declares them.
         */
        val SIDESTEP: Array<Array<Direction?>> = arrayOf(
            arrayOf(Direction.EAST, Direction.NORTH, null, null),
            arrayOf(null, Direction.SOUTH, Direction.EAST, null),
            arrayOf(null, null, Direction.WEST, Direction.SOUTH),
            arrayOf(Direction.WEST, null, null, Direction.NORTH),
        )
    }
}
