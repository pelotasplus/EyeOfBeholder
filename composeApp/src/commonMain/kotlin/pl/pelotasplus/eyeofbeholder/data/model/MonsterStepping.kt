package pl.pelotasplus.eyeofbeholder.data.model

/**
 * One monster moving one square, or turning where it stands.
 *
 * Everything a monster does to get anywhere goes through here: hunting the
 * party, patrolling a corridor and wandering are all a rule for choosing a
 * square, and then this.
 */
class MonsterStepping(
    private val level: Int,
    private val subLevel: SubLevel,
    private val kinds: List<MonsterProperty>,
) {

    /** What came of asking a monster to move. */
    sealed interface Stepped {
        /** It went, and this is what it sounds like going. */
        data class Moved(val world: GameState, val heard: TrackIndex?) : Stepped

        /**
         * It stayed where it was but is facing somewhere new — either that was
         * all it was asked to do, or a shut door turned it and took its step.
         */
        data class Turned(val world: GameState, val opened: Location?) : Stepped

        /** It could not, and has done nothing at all. */
        data object Refused : Stepped
    }

    /**
     * A monster steps onto [onto], or turns to [facing] where it stands when
     * given nowhere to go.
     *
     * Where it ends up facing is [facing], and it need not be where it is
     * going: a monster reaching a square off its shoulder sidles onto it
     * still facing the way it was. Which face of the wall is asked about is
     * always the way it travels, whatever it is looking at.
     *
     * A monster asked to move somewhere it cannot always ends up doing
     * nothing, never half of it: whoever is choosing squares is expected to
     * ask again with another one.
     */
    fun step(
        world: GameState,
        monster: MonsterInstance,
        onto: Location? = null,
        facing: Direction? = null,
    ): Stepped {
        val way = facing ?: monster.direction
        val from = Location(monster.x, monster.y)

        if (onto == null || onto == from) return turn(world, monster, way)

        // Not onto the party. A monster does not reach them by walking into
        // them; it stops on the square beside them and swings from there.
        if (onto == world.party.position) return Stepped.Refused

        val travel = Direction.entries.firstOrNull { it.oneStepFrom(from) == onto }
            ?: return Stepped.Refused

        val wall = world.wall(level, onto, travel.wallSideFacingBack)
        if (!subLevel.canBeWalkedOnto(wall)) {
            return openingTheDoor(world, monster, onto, travel, wall)
        }

        val place = roomOn(world, monster, onto, way) ?: return Stepped.Refused

        return Stepped.Moved(
            world = world.monsterMoved(monster.index, onto, way, place),
            heard = kind(monster)?.sound2?.takeIf { it > 0 }?.let { TrackIndex(it) },
        )
    }

    /**
     * A monster facing the party from a corner its arm does not reach from,
     * shifting its feet.
     *
     * Alone on its square it simply stands in the middle of it, from where
     * everything is in reach. Sharing it, it takes the best corner left. This
     * is why a single monster is never stuck being harmless, and why one of a
     * crowd sometimes is.
     */
    fun shuffleOn(world: GameState, monster: MonsterInstance): GameState {
        val sharing = world.monsters.any {
            it.index != monster.index && it.x == monster.x && it.y == monster.y
        }

        val place = if (sharing) {
            val taken = world.monsters
                .filter { it.index != monster.index && it.x == monster.x && it.y == monster.y }
                .map { it.place }
                .toSet()

            CORNERS_FACING[monster.direction.ordinal].firstOrNull { it !in taken }
        } else {
            SquarePlace.MIDDLE
        }

        return place?.let { world.monsterShifted(monster.index, it) } ?: world
    }

    /**
     * Turning is a step. It costs the same turn moving would and makes the
     * same noise, which is why something heavy is heard rounding a corner.
     */
    private fun turn(world: GameState, monster: MonsterInstance, way: Direction): Stepped =
        Stepped.Turned(
            world = world.monsterTurned(monster.index, way),
            opened = null,
        )

    /**
     * A shut door in the way, for a monster whose kind can work one.
     *
     * Opening it is the whole of its step: it ends the turn facing the door
     * and standing where it started, and walks through on the next one. What
     * it does not do is make its own noise over the door's.
     */
    private fun openingTheDoor(
        world: GameState,
        monster: MonsterInstance,
        onto: Location,
        way: Direction,
        wall: Maz.WallType,
    ): Stepped {
        if (kind(monster)?.opensDoors != true) return Stepped.Refused
        if (wall !is Maz.WallType.Door || wall.isOpen) return Stepped.Refused

        return Stepped.Turned(
            world = world.monsterTurned(monster.index, way),
            opened = onto,
        )
    }

    /**
     * Where on [onto] this monster would stand, or null if it will not fit.
     *
     * A square takes monsters of one size only, so four small things share it
     * and nothing else may join them, and anything bigger has it to itself.
     *
     * Arriving on an empty square it keeps the corner it was already standing
     * on. That is what lets a monster keep up with a party sidling away from
     * it: a monster's arm reaches only from the two corners on the side it
     * faces, so being moved to a fresh corner would leave it standing beside
     * the party unable to touch them.
     */
    private fun roomOn(
        world: GameState,
        monster: MonsterInstance,
        onto: Location,
        facing: Direction,
    ): SquarePlace? {
        val size = kind(monster)?.size ?: return null
        val already = world.monsters.filter {
            it.index != monster.index && it.x == onto.x && it.y == onto.y
        }

        if (already.isEmpty()) return monster.place
        if (already.any { kind(it)?.size != size } || !size.shares) return null

        val taken = already.map { it.place }.toSet()
        return CORNERS_FACING[facing.ordinal].firstOrNull { it !in taken }
    }

    private fun kind(monster: MonsterInstance): MonsterProperty? =
        kinds.firstOrNull { it.id == monster.type.value }

    private companion object {
        /**
         * Which corner of a crowded square a monster takes, by the way it
         * faces, best first. From the original.
         *
         * The order is not cosmetic: the first two of each row are the two
         * corners its arm reaches from, so a monster squeezing onto an
         * occupied square still ends up able to fight if there is any way to.
         */
        val CORNERS_FACING = listOf(
            listOf(
                SquarePlace.NORTH_WEST,
                SquarePlace.NORTH_EAST,
                SquarePlace.SOUTH_WEST,
                SquarePlace.SOUTH_EAST,
            ),
            listOf(
                SquarePlace.NORTH_EAST,
                SquarePlace.SOUTH_EAST,
                SquarePlace.NORTH_WEST,
                SquarePlace.SOUTH_WEST,
            ),
            listOf(
                SquarePlace.SOUTH_WEST,
                SquarePlace.SOUTH_EAST,
                SquarePlace.NORTH_WEST,
                SquarePlace.NORTH_EAST,
            ),
            listOf(
                SquarePlace.NORTH_WEST,
                SquarePlace.SOUTH_WEST,
                SquarePlace.NORTH_EAST,
                SquarePlace.SOUTH_EAST,
            ),
        )
    }
}
