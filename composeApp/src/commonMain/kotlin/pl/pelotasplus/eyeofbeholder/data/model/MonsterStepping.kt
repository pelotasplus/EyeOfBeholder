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

        /** The world after it — [unchanged] where nothing happened. */
        fun worldOr(unchanged: GameState): GameState = when (this) {
            is Moved -> world
            is Turned -> world
            Refused -> unchanged
        }
    }

    /**
     * A monster steps onto [onto], or turns to [facing] where it stands when
     * given nowhere to go.
     *
     * Where it ends up facing is [facing], and it need not be where it is
     * going: a monster reaching a square off its shoulder sidles onto it
     * still facing the way it was.
     *
     * The wall it asks about is the one across the face it is *looking* at,
     * not the one it is walking through. That reads like a mistake and is not:
     * it is what stops a monster sidling along a corridor for ever. Sidling
     * west while facing south, it asks about the north face of the square
     * beside it, and where that is shut it is refused. Being refused is what
     * sends it to the fan, which goes the same way but turns it as it goes —
     * so it arrives no longer facing the party and owes a turn before it can
     * swing. Corrected to the travelling face, a monster never fails a
     * sidestep, never reaches the fan, and slides along beside a party for
     * ever swinging as it goes, which is not the game.
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

        if (Direction.entries.none { it.oneStepFrom(from) == onto }) return Stepped.Refused

        val wall = world.wall(level, onto, way.wallSideFacingBack)
        if (!subLevel.canBeWalkedOntoByAMonster(wall)) {
            return openingTheDoor(world, monster, onto, way, wall)
        }

        val room = roomOn(world, monster, onto, way) ?: return Stepped.Refused

        return Stepped.Moved(
            world = room.world
                .monsterMoved(monster.index, onto, way, room.place)
                .facingTogetherOn(monster, onto, way),
            heard = kind(monster)?.sound2?.takeIf { it > 0 }?.let { TrackIndex(it) },
        )
    }

    /**
     * Whether the square [way] of this one has an opening onto it.
     *
     * The wall and nothing else — not who is standing there, not where the
     * party are. It is what a straying monster glances at as it goes past, and
     * seeing a way through is not the same as being able to take it.
     */
    fun opensOnto(world: GameState, monster: MonsterInstance, way: Direction): Boolean {
        val onto = way.oneStepFrom(Location(monster.x, monster.y))
        return subLevel.canBeWalkedOntoByAMonster(
            world.wall(level, onto, way.wallSideFacingBack),
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
        val size = kind(monster)?.size ?: return world
        val taken = world.monsters
            .filter { it.index != monster.index && it.x == monster.x && it.y == monster.y }
            .map { it.place }
            .toSet()

        val place = if (taken.isEmpty()) {
            SquarePlace.MIDDLE
        } else {
            placesOn(size, monster.direction).firstOrNull { it !in taken }
        }

        return place?.let { world.monsterShifted(monster.index, it) } ?: world
    }

    /**
     * Turning is a step. It costs the same turn moving would and makes the
     * same noise, which is why something heavy is heard rounding a corner.
     */
    private fun turn(world: GameState, monster: MonsterInstance, way: Direction): Stepped =
        Stepped.Turned(
            world = world
                .monsterTurned(monster.index, way)
                .facingTogetherOn(monster, Location(monster.x, monster.y), way),
            opened = null,
        )

    /**
     * A door in the way, for a monster whose kind can work one.
     *
     * The kind is half of it; the door is the other half, and two things about
     * it decide the rest.
     *
     * It must be a door with a button — the same doors a hand can open. One
     * with a button on neither face belongs to whatever plate or script drives
     * it, and a monster stands at it as helplessly as the party would. A
     * button on one face alone is enough for whoever is on that side, which is
     * how a door can open for a monster and never by hand.
     *
     * And it must be standing fully shut. One caught halfway is left to finish
     * whatever it was already doing.
     *
     * Either way the turn is spent: the monster ends facing the door and
     * standing where it started, and comes through on a later one, by which
     * time the door has had its own clock to travel on. What it does not do is
     * make its own noise over the door's.
     *
     * Sent open rather than opened, so it slides the way any door does. Being
     * shut is what a doorway with something in it refuses — nothing refuses
     * this.
     */
    private fun openingTheDoor(
        world: GameState,
        monster: MonsterInstance,
        onto: Location,
        way: Direction,
        wall: Maz.WallType,
    ): Stepped {
        if (kind(monster)?.opensDoors != true) return Stepped.Refused
        if (wall !is Maz.WallType.Door || !wall.hasButton) return Stepped.Refused

        val turned = world.monsterTurned(monster.index, way)
        if (!wall.isShut) return Stepped.Turned(turned, opened = null)

        return Stepped.Turned(
            world = turned.doorSetGoing(level, onto, way.wallSideFacingBack, opening = true),
            opened = onto,
        )
    }

    /** Room for one more on a square: where it stands, and who stood aside. */
    private data class Room(val place: SquarePlace, val world: GameState)

    /**
     * Where on [onto] this monster would stand, or null if it will not fit.
     *
     * A square takes monsters of one size only, so its own kind of crowd
     * gathers on it or none does: four small things one to a corner, two
     * bigger ones in opposite corners, and the biggest have a square to
     * themselves.
     *
     * Whoever is already there and standing in the middle stands aside as this
     * one arrives, which is the only time a monster is moved by somebody
     * else's step.
     *
     * Arriving on an empty square it keeps the corner it was already standing
     * on. That is what lets a monster keep up with a party sidling away from
     * it: a small monster's arm reaches only from the two corners on the side
     * it faces, so being moved to a fresh corner would leave it standing
     * beside the party unable to touch them.
     */
    private fun roomOn(
        world: GameState,
        monster: MonsterInstance,
        onto: Location,
        facing: Direction,
    ): Room? {
        val size = kind(monster)?.size ?: return null
        val already = world.monsters.filter {
            it.index != monster.index && it.x == onto.x && it.y == onto.y
        }

        if (already.isEmpty()) return Room(monster.place, world)
        if (already.any { kind(it)?.size != size } || !size.shares) return null
        if (already.size >= size.toASquare) return null

        val standingAside = already.associate { it.index to it.standsAsideAs(size) }
        val free = placesOn(size, facing).firstOrNull { it !in standingAside.values } ?: return null

        return Room(free, world.monstersShifted(standingAside))
    }

    /**
     * Where one that was alone on a square goes when another arrives. The
     * middle is only for as long as nobody is sharing.
     */
    private fun MonsterInstance.standsAsideAs(size: MonsterSize): SquarePlace =
        if (place != SquarePlace.MIDDLE) place
        else when (size) {
            MonsterSize.TWO_TO_A_SQUARE -> PAIRED.first()
            else -> CORNER_FACING[direction.ordinal]
        }

    /** The places a crowd of that size stands on, best first. */
    private fun placesOn(size: MonsterSize, facing: Direction): List<SquarePlace> =
        when (size) {
            MonsterSize.TWO_TO_A_SQUARE -> PAIRED
            else -> CORNERS_FACING[facing.ordinal]
        }

    /**
     * The world with every monster on [at] facing [way].
     *
     * Two sharing a square turn as one: a whole square turns with whichever
     * of the pair moved, and without it one of them ends up facing a
     * wall while the other fights, which is a wolf standing about.
     */
    private fun GameState.facingTogetherOn(
        monster: MonsterInstance,
        at: Location,
        way: Direction,
    ): GameState {
        if (kind(monster)?.size != MonsterSize.TWO_TO_A_SQUARE) return this

        return monstersTurnedToFace(
            monsters.filter { it.x == at.x && it.y == at.y }.map { it.index to way },
        )
    }

    private fun kind(monster: MonsterInstance): MonsterProperty? =
        kinds.firstOrNull { it.id == monster.type.value }

    private companion object {
        /**
         * The two corners a pair share a square on, whichever way either of
         * them faces. Transcribed, which has no other pair of places
         * for them: a third of that size is turned away at the edge.
         */
        val PAIRED = listOf(SquarePlace.NORTH_WEST, SquarePlace.SOUTH_EAST)

        /**
         * Which corner one standing in the middle steps back to when it has
         * to make room, by the way it faces. Transcribed.
         */
        val CORNER_FACING = listOf(
            SquarePlace.NORTH_WEST,
            SquarePlace.NORTH_EAST,
            SquarePlace.SOUTH_EAST,
            SquarePlace.SOUTH_WEST,
        )

        /**
         * Which corner of a crowded square a monster takes, by the way it
         * faces, best first. Transcribed.
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
