package pl.pelotasplus.eyeofbeholder.data

import kotlinx.coroutines.runBlocking
import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.GameState
import pl.pelotasplus.eyeofbeholder.data.model.Inf
import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.MonsterInstance
import pl.pelotasplus.eyeofbeholder.data.model.MonsterPathing
import pl.pelotasplus.eyeofbeholder.data.model.MonsterPose
import pl.pelotasplus.eyeofbeholder.data.model.MonsterStepping
import pl.pelotasplus.eyeofbeholder.data.model.MonstersTurn
import pl.pelotasplus.eyeofbeholder.data.model.PartyState
import pl.pelotasplus.eyeofbeholder.data.model.SquarePlace
import pl.pelotasplus.eyeofbeholder.data.repository.CpsRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.DecRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.InfRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.MazRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.PalRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.ResourceRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.VcnRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.VmpRepositoryImpl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A monster coming after the party rather than waiting to be walked into.
 *
 * Level 5's temple again: the open squares are 12x8 and 13x8, 12x9 through
 * 15x9, and 12x10 and 13x10.
 */
class MonstersHuntingTest {

    private val resources = ResourceRepositoryImpl()

    private val level: Inf = runBlocking {
        InfRepositoryImpl(
            resourceRepository = resources,
            mazRepository = MazRepositoryImpl(resources),
            vmpRepository = VmpRepositoryImpl(resources),
            vcnRepository = VcnRepositoryImpl(resources),
            palRepository = PalRepositoryImpl(resources),
            cpsRepository = CpsRepositoryImpl(resources),
            decRepository = DecRepositoryImpl(resources),
        ).loadInf("LEVEL5.INF").getOrThrow()
    }

    private val sub get() = level.subLevels[0]
    private val kinds get() = sub.monsters

    private fun world(
        at: Location,
        facing: Direction,
        party: Location,
        alone: Boolean = true,
    ): GameState {
        val placed = GameState(party = PartyState(party, Direction.NORTH)).arrivingAt(
            level = 5,
            places = level.monsterInstances,
            maz = sub.maz,
            kinds = kinds,
        )

        val moved = placed.monsters
            .filter { alone && it.index == CLERIC || !alone }
            .map { it.copy(block = at.asBlock, direction = facing) }

        return placed.copy(monsters = moved)
    }

    private val walking = MonsterPathing(
        stepping = MonsterStepping(level = 5, subLevel = kinds.let { sub }, kinds = kinds),
        kinds = kinds,
    )

    private fun turn() = MonstersTurn(kinds)

    private fun GameState.theCleric() = monsters.first { it.index == CLERIC }
    private fun GameState.whereTheClericIs() = theCleric().let { Location(it.x, it.y) }

    @Test
    fun `a roused monster out of reach comes after the party`() {
        val world = world(
            at = Location(13, 8),
            facing = Direction.SOUTH,
            party = Location(13, 10),
        ).rousedBy(CLERIC)

        val after = turn().begun(world, walking)

        assertEquals(Location(13, 9), after.whereTheClericIs())
    }

    /** Rooted, all it can do from two squares off is nothing at all. */
    @Test
    fun `without walking it stays where it was placed`() {
        val world = world(
            at = Location(13, 8),
            facing = Direction.SOUTH,
            party = Location(13, 10),
        ).rousedBy(CLERIC)

        val after = turn().begun(world)

        assertEquals(Location(13, 8), after.whereTheClericIs())
    }

    /**
     * Arriving is a turn to face and not a step onto the party. It ends the
     * walk in reach, which is the point of the walk.
     */
    @Test
    fun `it stops beside the party facing them`() {
        val world = world(
            at = Location(13, 9),
            facing = Direction.EAST,
            party = Location(13, 10),
        ).rousedBy(CLERIC)

        val after = turn().begun(world, walking)

        assertEquals(Location(13, 9), after.whereTheClericIs())
        assertEquals(Direction.SOUTH, after.theCleric().direction)
        assertTrue(after.theCleric().canReach(after.party), "it arrived out of reach")
    }

    /**
     * A monster's arm reaches only from the two corners of its square on the
     * side it faces. So one sidling after a party who step aside has to keep
     * the corner it was standing on: given a fresh one it arrives beside them
     * and can never touch them, and the fight becomes a procession.
     */
    @Test
    fun `sidling after the party leaves it still able to reach them`() {
        val world = world(
            at = Location(13, 9),
            facing = Direction.SOUTH,
            party = Location(12, 10),
        ).rousedBy(CLERIC)

        assertEquals(SquarePlace.SOUTH_EAST, world.theCleric().place)

        val after = turn().begun(world, walking)

        assertEquals(Location(12, 9), after.whereTheClericIs())
        assertEquals(Direction.SOUTH, after.theCleric().direction, "it turned instead of sidling")
        assertTrue(
            after.theCleric().canReach(after.party),
            "it arrived on a corner it cannot reach from",
        )
    }

    /**
     * The clerics carry the flag for it, so stepping aside buys nothing: they
     * follow and swing in the same turn. Without this a party can strafe from
     * one square to the next for ever, taking a free blow each time, which is
     * no fight at all.
     */
    @Test
    fun `a kind that hits as it moves follows and swings in one turn`() {
        assertTrue(kinds.all { it.hitsAsItMoves }, "the temple's kinds do not")

        // Beside them and facing them, then the party sidle out of the way.
        val world = world(
            at = Location(13, 9),
            facing = Direction.SOUTH,
            party = Location(12, 10),
        ).rousedBy(CLERIC)

        val after = turn().begun(world, walking)

        assertEquals(Location(12, 9), after.whereTheClericIs(), "it did not follow")
        assertEquals(
            MonsterPose.ATTACK_A,
            after.theCleric().striking,
            "it followed but did not swing",
        )
    }

    /** One without the flag spends its turn on the one thing or the other. */
    @Test
    fun `a kind without it has to arrive before it can swing`() {
        val plodding = kinds.map { it.copy(capsFlags = it.capsFlags and 0x8.inv()) }

        val world = world(
            at = Location(13, 9),
            facing = Direction.SOUTH,
            party = Location(12, 10),
        ).rousedBy(CLERIC)

        val after = MonstersTurn(plodding).begun(
            world,
            MonsterPathing(
                stepping = MonsterStepping(level = 5, subLevel = sub, kinds = plodding),
                kinds = plodding,
            ),
        )

        assertEquals(Location(12, 9), after.whereTheClericIs())
        assertNull(after.theCleric().striking, "it moved and swung in one turn")
    }

    /**
     * A pair placed side by side do not act together. Their group is their
     * record's own split by whether their slot is odd, so the two clerics —
     * slots 16 and 17 — take their turns a beat apart, and only one of them
     * answers any given turn.
     */
    @Test
    fun `two monsters placed together take their turns apart`() {
        val world = world(
            at = Location(13, 8),
            facing = Direction.SOUTH,
            party = Location(13, 10),
            alone = false,
        ).rousedBy(CLERIC)

        val groups = world.monsters.map { it.turnGroup }
        assertEquals(2, groups.toSet().size, "the pair share a turn group")

        val itsTurn = turn().begun(world, walking, group = groups.first())
        val whoMoved = itsTurn.monsters.filter { after ->
            world.monsters.first { it.index == after.index }.block != after.block
        }

        assertEquals(1, whoMoved.size, "both moved on one group's turn")
    }

    /**
     * Facing the party from a corner its arm does not reach from, a monster
     * shifts its feet rather than walking off — and one with the square to
     * itself simply stands in the middle of it, where everything is in reach.
     */
    @Test
    fun `a monster facing the party from a bad corner shifts its feet`() {
        val world = world(
            at = Location(13, 9),
            facing = Direction.SOUTH,
            party = Location(13, 10),
        ).let { world ->
            world.copy(
                monsters = world.monsters.map { it.copy(place = SquarePlace.NORTH_WEST) },
            )
        }.rousedBy(CLERIC)

        assertTrue(world.theCleric().facesTheSquareOf(world.party))
        assertFalse(world.theCleric().canReach(world.party), "it could reach all along")

        val after = turn().begun(world, walking)

        assertEquals(Location(13, 9), after.whereTheClericIs(), "it walked off instead")
        assertEquals(SquarePlace.MIDDLE, after.theCleric().place)
        assertTrue(after.theCleric().canReach(after.party))
    }

    /**
     * Three squares is as far as anything sees, and something walking away
     * from the party is not to be crept up behind for ever — but nor does it
     * notice them across a room.
     */
    @Test
    fun `nothing notices the party from four squares off`() {
        val far = world(
            at = Location(13, 8),
            facing = Direction.SOUTH,
            party = Location(13, 12),
        ).theCleric()

        assertFalse(far.notices(PartyState(Location(13, 12), Direction.NORTH)))
        assertTrue(far.notices(PartyState(Location(13, 11), Direction.NORTH)))
    }

    /**
     * Behind it and not right beside it, the party go unnoticed — which is
     * what makes stepping round something worth the trouble.
     */
    @Test
    fun `the party behind it go unnoticed until they are beside it`() {
        val facingAway = world(
            at = Location(13, 9),
            facing = Direction.NORTH,
            party = Location(13, 11),
        ).theCleric()

        assertFalse(facingAway.notices(PartyState(Location(13, 11), Direction.NORTH)))
        assertTrue(facingAway.notices(PartyState(Location(13, 10), Direction.NORTH)))
    }

    /**
     * Something waiting to see what the party do is deaf until it is hit, and
     * walking does not change that: level 5's pair still stand talking.
     */
    @Test
    fun `something standing by is not roused by being noticed`() {
        val world = world(
            at = Location(13, 8),
            facing = Direction.SOUTH,
            party = Location(13, 9),
        )

        assertTrue(world.theCleric().standingBy, "the pair are not placed waiting to see")

        val after = turn().begun(world, walking)

        assertFalse(after.theCleric().provoked)
        assertEquals(Location(13, 8), after.whereTheClericIs())
    }

    private companion object {
        const val CLERIC = 16
    }
}
