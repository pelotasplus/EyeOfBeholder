package pl.pelotasplus.eyeofbeholder.data.model

/**
 * The fan of flame thrown up over the view while a burning hands goes off.
 *
 * It has no frames and no clock of its own — it is drawn, held for a moment,
 * and taken off again — so there is nothing in it to carry: its being there is
 * the whole of it.
 */
data object AFanOfFlame {

    /** How long it is held up before the flame reaches anything. */
    val HELD_FOR = Ticks(2)

    /**
     * The three pictures the fan is made of, cut from the sheet of things that
     * can be thrown.
     *
     * Each is drawn twice, the second mirrored, which is why the fan is
     * symmetrical: it is the same three shapes both ways round.
     */
    enum class Beam { INNER, MIDDLE, OUTER }

    /** One of the six, and which way round it goes. */
    data class Flame(
        val beam: Beam,
        val at: ScreenX,
        val and: ScreenY,
        val mirrored: Boolean,
    )

    /**
     * Where each of the six goes. Data, and the six abut exactly: the outer
     * pair reach the edges of the view and the inner pair meet in the middle
     * of it, so a digit changed by eye shows as a gap or an overlap in the
     * frozen frame and nowhere else.
     */
    val FLAMES: List<Flame> = listOf(
        Flame(Beam.OUTER, ScreenX(0), ScreenY(64), mirrored = false),
        Flame(Beam.OUTER, ScreenX(152), ScreenY(64), mirrored = true),
        Flame(Beam.MIDDLE, ScreenX(24), ScreenY(56), mirrored = false),
        Flame(Beam.MIDDLE, ScreenX(120), ScreenY(56), mirrored = true),
        Flame(Beam.INNER, ScreenX(56), ScreenY(56), mirrored = false),
        Flame(Beam.INNER, ScreenX(88), ScreenY(56), mirrored = true),
    )

    /**
     * The places on the square ahead the flame reaches, in the order it
     * reaches them, for a party facing each way.
     *
     * Data. Reasoning about it will not check it and a digit changed by eye
     * has nothing to catch it but [BurningHands]'s own tests, so it is read
     * out rather than worked out — and two things in it read like mistakes.
     *
     * Facing north the flame reaches the near-left corner twice and the
     * far-left corner not at all, where facing any other way it reaches each
     * of the five once. A creature standing in the far-left corner of the
     * square ahead is therefore burnt by this from three sides of it and not
     * from the fourth, and one standing in the near-left corner burns twice —
     * two rolls, two throws against it. The other three rows show what that
     * row was meant to be; it is the game's, and sorting it out would make
     * the spell quietly better than it is.
     *
     * The last of every row names a place no creature is ever at, which is
     * why it is nothing here.
     */
    val REACHES: Map<Direction, List<SquarePlace?>> = mapOf(
        Direction.NORTH to listOf(
            SquarePlace.SOUTH_WEST,
            SquarePlace.SOUTH_EAST,
            SquarePlace.MIDDLE,
            SquarePlace.NORTH_EAST,
            SquarePlace.SOUTH_WEST,
            null,
        ),
        Direction.EAST to listOf(
            SquarePlace.NORTH_WEST,
            SquarePlace.SOUTH_WEST,
            SquarePlace.MIDDLE,
            SquarePlace.NORTH_EAST,
            SquarePlace.SOUTH_EAST,
            null,
        ),
        Direction.SOUTH to listOf(
            SquarePlace.NORTH_WEST,
            SquarePlace.NORTH_EAST,
            SquarePlace.MIDDLE,
            SquarePlace.SOUTH_WEST,
            SquarePlace.SOUTH_EAST,
            null,
        ),
        Direction.WEST to listOf(
            SquarePlace.NORTH_EAST,
            SquarePlace.SOUTH_EAST,
            SquarePlace.MIDDLE,
            SquarePlace.NORTH_WEST,
            SquarePlace.SOUTH_WEST,
            null,
        ),
    )
}

/**
 * Burning hands: the square in front of the party burnt, and no further.
 *
 * Nothing flies and nothing is aimed. The flame covers one square the moment
 * it is read, so there is no getting out of its way and nothing to intercept
 * it — and no wall to shelter behind either, a creature in a closed doorway
 * burning as one in an open one does.
 *
 * What it reaches is decided by where on that square a creature stands, which
 * is [AFanOfFlame.REACHES] and the one odd thing about the spell: the same
 * creature in the same doorway takes this differently depending on which way
 * the party came at it.
 */
class BurningHands(
    private val kinds: List<MonsterProperty> = emptyList(),
    private val itemTypes: ItemTypes? = null,
    private val dice: Dice = Dice.random,
) {
    /** The world afterwards, and everything the flame reached, in order. */
    data class Struck(
        val world: GameState,
        val burned: List<MonsterSlot> = emptyList(),
    )

    fun castBy(world: GameState, casterLevel: Int): Struck {
        val ahead = world.party.facing.oneStepFrom(world.party.position)
        val reaches = AFanOfFlame.REACHES[world.party.facing].orEmpty()

        return reaches.fold(Struck(world)) { struck, place ->
            if (place == null) return@fold struck

            // Looked up in the world the flame has reached so far rather than
            // the one it started in, so that the corner it reaches twice is
            // not burnt again after the first reach killed what stood there.
            val caught = struck.world.monsters.firstOrNull {
                Location(it.x, it.y) == ahead && it.place == place
            } ?: return@fold struck

            val kind = kinds.firstOrNull { it.id == caught.type.value }

            struck.copy(
                world = struck.world.monsterHurt(
                    caught.index,
                    dealtTo(kind, casterLevel),
                    kinds,
                    itemTypes,
                    dice,
                ),
                burned = struck.burned + caught.index,
            )
        }
    }

    /**
     * A die of three and two for every level of the caster, so a scroll read
     * by anybody at all burns for eighteen and a die.
     *
     * The level buys the flat part and not another die, which makes this the
     * one attack spell whose worst roll grows and whose spread does not.
     *
     * The order the four questions are asked in is the game's and matters,
     * because each of them throws dice: what it rolls, then the creature's own
     * throw to take half of it, then what its hide turns aside, then whether
     * it was quick enough to be clear of the flame altogether.
     */
    private fun dealtTo(kind: MonsterProperty?, casterLevel: Int): Damage {
        val rolled = dice.roll(1, DIE, casterLevel * WHAT_A_LEVEL_IS_WORTH)
        val thrownOff = if (kind?.saves(SavingThrow.A_SPELL, dice) == true) rolled shr 1 else rolled

        val dealt = Damage(thrownOff.coerceAtLeast(0))
        val softened = kind?.immunities?.softened(dealt, DealtBy.Magic) ?: dealt

        if (kind != null && dice.roll(1, 100, 0) < kind.dmgModifierEvade) return Damage(0)
        if (kind?.immunities?.shrugsOff(HURTS_BY) == true) return Damage(0)

        return softened
    }

    companion object {
        /**
         * Fire, and magic as every spell is. Anything that shrugs off either
         * takes none of it — and unlike a flame blade, which only looks like
         * fire, this one really is and a creature that lives in fire is clear
         * of it.
         */
        val HURTS_BY = setOf(HarmKind.MAGIC, HarmKind.FIRE)

        private const val DIE = 3
        private const val WHAT_A_LEVEL_IS_WORTH = 2
    }
}
