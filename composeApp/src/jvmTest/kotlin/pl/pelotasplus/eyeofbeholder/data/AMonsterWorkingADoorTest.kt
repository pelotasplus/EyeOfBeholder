package pl.pelotasplus.eyeofbeholder.data

import kotlinx.coroutines.runBlocking
import org.junit.experimental.categories.Category
import pl.pelotasplus.eyeofbeholder.NeedsGameData
import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.GameState
import pl.pelotasplus.eyeofbeholder.data.model.HitPoints
import pl.pelotasplus.eyeofbeholder.data.model.Inf
import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.Maz
import pl.pelotasplus.eyeofbeholder.data.model.MonsterInstance
import pl.pelotasplus.eyeofbeholder.data.model.MonsterPathing
import pl.pelotasplus.eyeofbeholder.data.model.MonsterSlot
import pl.pelotasplus.eyeofbeholder.data.model.MonsterStepping
import pl.pelotasplus.eyeofbeholder.data.model.MonsterTypeId
import pl.pelotasplus.eyeofbeholder.data.model.MonstersTurn
import pl.pelotasplus.eyeofbeholder.data.model.PartyState
import pl.pelotasplus.eyeofbeholder.data.model.SquarePlace
import pl.pelotasplus.eyeofbeholder.data.model.WallSide
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
import kotlin.test.assertTrue

/**
 * A monster working a door, which takes a kind that can and a door that will.
 *
 * The kind is a mark it carries: level one's guards have it, the clerics of
 * the second, fifth and sixth floors, the beholder, the fifteenth floor's
 * mages, and the last floor's mind flayer, salamander and dragon. Everything
 * else is stopped by any door at all.
 *
 * The door is the other half, and the half that keeps this rare: only a door
 * with a button is one anything opens. Most of the doors in the dungeon have
 * none on either face — they belong to a plate or a script, and a monster
 * stands at them as helplessly as the party would.
 *
 * The fifteenth floor's mages are where the two meet. Their door carries its
 * button on their side alone, so it opens for them and never by hand, which is
 * the whole of that room.
 *
 * Opening it is the whole of the monster's turn: it ends facing the door and
 * standing where it was, and comes through on a later one, by which time the
 * door has had its own clock to travel on.
 */
@Category(NeedsGameData::class)
class AMonsterWorkingADoorTest {

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
        ).loadInf("LEVEL15.INF").getOrThrow()
    }

    private val sub get() = level.subLevels[0]
    private val kinds get() = sub.monsters

    private val stepping get() = MonsterStepping(level = LEVEL, subLevel = sub, kinds = kinds)

    private fun world(kind: Int) = GameState(
        party = PartyState(THE_PARTY, Direction.EAST),
        monsters = listOf(
            MonsterInstance(
                index = IT,
                unit = 0,
                location = BEHIND_THE_DOOR,
                place = SquarePlace.MIDDLE,
                direction = Direction.WEST,
                type = MonsterTypeId(kind),
                gfxIndex = 0,
                mode = 0,
                pause = 0,
                weapon = 0,
                pocketItem = 0,
                hitPoints = HitPoints(40, 40),
                provoked = true,
            ),
        ),
    ).arrivingAt(level = LEVEL, places = emptyList(), maz = sub.maz)
        .copy(
            monsters = listOf(
                MonsterInstance(
                    index = IT,
                    unit = 0,
                    location = BEHIND_THE_DOOR,
                    place = SquarePlace.MIDDLE,
                    direction = Direction.WEST,
                    type = MonsterTypeId(kind),
                    gfxIndex = 0,
                    mode = 0,
                    pause = 0,
                    weapon = 0,
                    pocketItem = 0,
                    hitPoints = HitPoints(40, 40),
                    provoked = true,
                ),
            ),
        )

    /** The doorway as the party see it, which is its western face. */
    private fun GameState.itsDoor() =
        wall(LEVEL, THE_DOORWAY, WallSide.WEST) as Maz.WallType.Door

    private fun GameState.theMonster() = monsters.first { it.index == IT }

    private tailrec fun GameState.doorsFullyOpen(): GameState =
        if (swinging.isEmpty()) this else doorsStepped().world.doorsFullyOpen()

    private fun GameState.stepsOntoTheDoorway() =
        stepping.step(this, theMonster(), onto = THE_DOORWAY, facing = Direction.WEST)

    // --- the floor's own arrangement -------------------------------------------

    /**
     * The room as the level lays it out: a shut door with a button on the far
     * side only, so nothing the party do opens it.
     */
    @Test
    fun `the mages wait behind a door the party cannot open`() {
        val door = world(A_MAGE).itsDoor()

        assertTrue(door.isShut, "the door starts open")
        assertFalse(door.hasButton, "the party's side has a button after all")
        assertTrue(
            (world(A_MAGE).wall(LEVEL, THE_DOORWAY, WallSide.EAST) as Maz.WallType.Door).hasButton,
            "nor has the far side, so nothing opens it at all",
        )
        assertTrue(kinds[A_MAGE].opensDoors, "the mage cannot work a door")
        assertFalse(kinds[A_HOUND].opensDoors, "the hound can work a door")
    }

    // --- and what a monster does about it --------------------------------------

    @Test
    fun `a mage sends the door open and stays where it is`() {
        val before = world(A_MAGE)
        val stepped = before.stepsOntoTheDoorway()

        assertTrue(stepped is MonsterStepping.Stepped.Turned, "it did something else: $stepped")
        val after = stepped.worldOr(before)

        assertEquals(
            listOf(THE_DOORWAY to true),
            after.swinging.map { it.at to it.opening },
            "the door was not sent open",
        )
        assertEquals(BEHIND_THE_DOOR, after.theMonster().location, "it walked through a shut door")
        assertEquals(Direction.WEST, after.theMonster().direction, "it is not facing the door")
    }

    /** And the door then travels on its own clock, the way any door does. */
    @Test
    fun `the door slides open afterwards`() {
        val opening = world(A_MAGE).stepsOntoTheDoorway().worldOr(world(A_MAGE))

        assertTrue(opening.itsDoor().isShut, "it opened all at once")
        assertTrue(opening.doorsFullyOpen().itsDoor().isOpen, "it never finished opening")
    }

    /** Once it is open, the same step is a step and the monster comes through. */
    @Test
    fun `and then the monster walks through it`() {
        val before = world(A_MAGE)
        val opened = before.stepsOntoTheDoorway().worldOr(before).doorsFullyOpen()

        val walked = opened.stepsOntoTheDoorway()
        assertTrue(walked is MonsterStepping.Stepped.Moved, "it stayed put: $walked")
        assertEquals(THE_DOORWAY, walked.worldOr(opened).theMonster().location)
    }

    /**
     * And a door with no button on either face is not one anything opens —
     * the same floor's door at 5x9, which belongs to whatever drives it.
     *
     * This is the half of the rule that keeps a monster from walking the
     * dungeon at will. Without it every door in front of a guard, a cleric or
     * a skeleton would swing wide, and the floors that shut monsters in behind
     * a plate-worked door would not shut them in at all.
     */
    @Test
    fun `a door with no button is not one a monster opens`() {
        val before = world(A_MAGE)
        val shut = before.wall(LEVEL, THE_SEALED_DOORWAY, WallSide.EAST) as Maz.WallType.Door
        assertFalse(shut.hasButton, "that doorway has a button after all")

        val stepped = stepping.step(
            before.copy(monsters = before.monsters.map { it.copy(location = Location(6, 9)) }),
            before.theMonster().copy(location = Location(6, 9)),
            onto = THE_SEALED_DOORWAY,
            facing = Direction.WEST,
        )

        assertEquals(MonsterStepping.Stepped.Refused, stepped)
        assertEquals(emptyList(), stepped.worldOr(before).swinging, "it worked a sealed door")
    }

    /** A kind that cannot work one is simply stopped by it. */
    @Test
    fun `a hell hound is stopped by the same door`() {
        val before = world(A_HOUND)
        val stepped = before.stepsOntoTheDoorway()

        assertEquals(MonsterStepping.Stepped.Refused, stepped)
        assertEquals(emptyList(), stepped.worldOr(before).swinging, "something moved the door")
    }

    // --- and that a turn carries it ---------------------------------------------

    /**
     * The whole way through, which is where this was going wrong: the step
     * knew the door should open and the turn threw the answer away, so a mage
     * stood facing a shut door for ever.
     */
    @Test
    fun `a turn taken hunting the party opens it`() {
        val before = world(A_MAGE)
        val after = MonstersTurn(kinds).begun(
            world = before,
            walking = MonsterPathing(stepping = stepping, kinds = kinds),
        ).world

        assertEquals(
            listOf(THE_DOORWAY to true),
            after.swinging.map { it.at to it.opening },
            "a turn spent hunting left the door shut",
        )
    }

    private companion object {
        const val LEVEL = 15

        val IT = MonsterSlot(0)

        /** The corridor west of the mages' room: party, door, monster. */
        val THE_PARTY = Location(4, 5)
        val THE_DOORWAY = Location(5, 5)
        val BEHIND_THE_DOOR = Location(6, 5)

        /** A doorway of the same floor with a button on neither face. */
        val THE_SEALED_DOORWAY = Location(5, 9)

        /** The floor's kinds: the first two are hounds, the rest mages. */
        const val A_HOUND = 0
        const val A_MAGE = 5
    }
}
