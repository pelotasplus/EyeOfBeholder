package pl.pelotasplus.eyeofbeholder.data

import kotlinx.coroutines.runBlocking
import org.junit.experimental.categories.Category
import pl.pelotasplus.eyeofbeholder.NeedsGameData
import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.GameState
import pl.pelotasplus.eyeofbeholder.data.model.HitPoints
import pl.pelotasplus.eyeofbeholder.data.model.Item
import pl.pelotasplus.eyeofbeholder.data.model.ItemIconId
import pl.pelotasplus.eyeofbeholder.data.model.ItemNameId
import pl.pelotasplus.eyeofbeholder.data.model.ItemTypeId
import pl.pelotasplus.eyeofbeholder.data.model.LevelScriptRunner
import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.MonsterInstance
import pl.pelotasplus.eyeofbeholder.data.model.MonsterSlot
import pl.pelotasplus.eyeofbeholder.data.model.MonsterTypeId
import pl.pelotasplus.eyeofbeholder.data.model.PartyState
import pl.pelotasplus.eyeofbeholder.data.model.ScriptEvent
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

/**
 * The eleventh floor's plate at 13x2, which floors over the pit at 14x7.
 *
 * The pit is a column of six squares, 14x2 down to 14x7, each of which drops
 * the party to the tenth floor. The plate covers the last of them only, so the
 * way past is not the corridor but that one square — and it has to stay
 * covered while the party walk to it, which is what the puzzle is about.
 *
 * The script names three ways to hold it down and one to let it go, and the
 * numbers below are its own: wall 38 is the open pit and wall 25 the floor
 * laid over it.
 */
@Category(NeedsGameData::class)
class ThePlateThatCoversThePitTest {

    private val resources = ResourceRepositoryImpl()

    private val level = runBlocking {
        InfRepositoryImpl(
            resourceRepository = resources,
            mazRepository = MazRepositoryImpl(resources),
            vmpRepository = VmpRepositoryImpl(resources),
            vcnRepository = VcnRepositoryImpl(resources),
            palRepository = PalRepositoryImpl(resources),
            cpsRepository = CpsRepositoryImpl(resources),
            decRepository = DecRepositoryImpl(resources),
        ).loadInf("LEVEL11.INF").getOrThrow()
    }

    private fun world(standingOn: Location) = GameState(
        party = PartyState(standingOn, Direction.WEST),
    ).arrivingAt(level = LEVEL, places = emptyList(), maz = level.subLevels[0].maz)

    private fun GameState.ran(event: ScriptEvent, at: Location = PLATE) = runBlocking {
        LevelScriptRunner(level.script, level = LEVEL).onEvent(
            triggers = level.triggers,
            event = event,
            state = this@ran,
            at = at,
        ).state
    }

    /** What the pit is floored with, which is the same on all four sides. */
    private fun GameState.thePit() = wallByte(LEVEL, PIT, WallSide.NORTH).value

    private fun GameState.withOnThePlate(monster: MonsterInstance) =
        copy(monsters = monsters + monster)

    private val aWeight = Item(
        nameUnidentified = ItemNameId(0),
        nameIdentified = ItemNameId(0),
        flags = 0,
        icon = ItemIconId(3),
        type = ItemTypeId(0),
        place = SquarePlace.NORTH_WEST,
        location = PLATE,
        next = 0,
        prev = 0,
        level = LEVEL,
        value = 0,
    )

    private fun aBulette(at: Location) = MonsterInstance(
        index = MonsterSlot(0),
        unit = 0,
        location = at,
        place = SquarePlace.MIDDLE,
        direction = Direction.SOUTH,
        type = MonsterTypeId(0),
        gfxIndex = 0,
        mode = 0,
        pause = 0,
        weapon = 0,
        pocketItem = 0,
        hitPoints = HitPoints(20, 20),
    )

    @Test
    fun `the pit is open until something holds the plate down`() {
        assertEquals(OPEN, world(standingOn = OFF_THE_PLATE).thePit())
    }

    @Test
    fun `standing on the plate floors the pit over`() {
        val stoodOn = world(standingOn = PLATE).ran(ScriptEvent.PARTY_ENTERED)

        assertEquals(COVERED, stoodOn.thePit())
    }

    /**
     * And walking off opens it again — which is the puzzle. The party cannot
     * hold it down and cross it, so something else has to.
     */
    @Test
    fun `and walking off opens it again`() {
        val stoodOn = world(standingOn = PLATE).ran(ScriptEvent.PARTY_ENTERED)
        val walkedOff = stoodOn
            .copy(party = stoodOn.party.copy(position = OFF_THE_PLATE))
            .ran(ScriptEvent.PARTY_LEFT)

        assertEquals(OPEN, walkedOff.thePit())
    }

    /** One thing left lying on it does instead, with the party stood clear. */
    @Test
    fun `a weight left on it holds the pit covered`() {
        val weighted = world(standingOn = OFF_THE_PLATE).copy(items = listOf(aWeight))

        assertEquals(COVERED, weighted.ran(ScriptEvent.ITEM_PUT_DOWN).thePit())
    }

    /**
     * And so does a monster standing on it, which is the third way the script
     * names and the one that needs nothing carried to the square at all.
     *
     * A monster's step is an event in its own right — it fires on the square
     * left and again on the square arrived at — and a plate cannot tell that
     * weight from a foot.
     */
    @Test
    fun `and a monster wandering onto it holds it down too`() {
        val walkedOn = world(standingOn = OFF_THE_PLATE)
            .withOnThePlate(aBulette(at = PLATE))
            .ran(ScriptEvent.A_MONSTER_ARRIVED)

        assertEquals(COVERED, walkedOn.thePit())
    }

    /**
     * But only an empty plate answers a monster. Something already lying on it
     * puts the count past nothing, and the monster's own branch asks for
     * nothing — so a plate with a thing thrown onto it cannot be worked by a
     * monster standing there as well.
     */
    @Test
    fun `a monster on a plate with something already on it does nothing`() {
        val cluttered = world(standingOn = OFF_THE_PLATE)
            .copy(items = listOf(aWeight))
            .withOnThePlate(aBulette(at = PLATE))
            .ran(ScriptEvent.A_MONSTER_ARRIVED)

        assertEquals(OPEN, cluttered.thePit())
    }

    /**
     * And the weight branch wants exactly one thing, so a second thrown onto
     * the plate leaves neither branch able to fire: it is not one thing any
     * more, and it is not nothing either.
     */
    @Test
    fun `two things on the plate work it no better than none`() {
        val twice = world(standingOn = OFF_THE_PLATE)
            .copy(items = listOf(aWeight, aWeight))
            .ran(ScriptEvent.ITEM_PUT_DOWN)

        assertEquals(OPEN, twice.thePit())
    }

    @Test
    fun `and it opens again when the monster wanders off`() {
        val walkedOn = world(standingOn = OFF_THE_PLATE)
            .withOnThePlate(aBulette(at = PLATE))
            .ran(ScriptEvent.A_MONSTER_ARRIVED)

        val wanderedOff = walkedOn
            .copy(monsters = walkedOn.monsters.map { it.copy(location = OFF_THE_PLATE) })
            .ran(ScriptEvent.A_MONSTER_LEFT)

        assertEquals(OPEN, wanderedOff.thePit())
    }

    /**
     * The way through, in the order it has to be done.
     *
     * The plate cannot be held down by a thing alone — a weight only presses
     * it with nothing else standing there, and the party cannot be standing
     * there either. So the pit is opened by a monster, and then kept open by
     * dropping something on the plate *while the monster is still on it*: the
     * weight does not press the plate, it stops the plate letting go, because
     * every release the script names wants an empty square.
     *
     * Which is why doing it the other way round never works. See the test
     * below.
     */
    @Test
    fun `a monster onto the plate, then a weight, and the pit stays covered`() {
        val monsterOn = world(standingOn = OFF_THE_PLATE)
            .withOnThePlate(aBulette(at = PLATE))
            .ran(ScriptEvent.A_MONSTER_ARRIVED)
        assertEquals(COVERED, monsterOn.thePit(), "the monster did not work the plate")

        val weighted = monsterOn.copy(items = listOf(aWeight)).ran(ScriptEvent.ITEM_PUT_DOWN)
        assertEquals(COVERED, weighted.thePit(), "dropping a weight took the floor away")

        val wanderedOff = weighted
            .copy(monsters = weighted.monsters.map { it.copy(location = OFF_THE_PLATE) })
            .ran(ScriptEvent.A_MONSTER_LEFT)

        assertEquals(COVERED, wanderedOff.thePit(), "the weight did not hold it")
    }

    /**
     * The order matters because a weight cannot be dropped onto an occupied
     * plate: that branch wants the square clear of monsters too. So a weight
     * thrown down while something is standing there does nothing at all — and
     * with the weight then lying on the plate, the monster can do nothing
     * either. Both ways of working it are shut, and the pit stays open until
     * the weight is taken off again.
     */
    @Test
    fun `a weight dropped onto an occupied plate shuts both ways of working it`() {
        val occupied = world(standingOn = OFF_THE_PLATE)
            .withOnThePlate(aBulette(at = PLATE))

        val weightThrownOn = occupied.copy(items = listOf(aWeight)).ran(ScriptEvent.ITEM_PUT_DOWN)
        assertEquals(OPEN, weightThrownOn.thePit(), "the weight worked an occupied plate")

        val andAgain = weightThrownOn.ran(ScriptEvent.A_MONSTER_ARRIVED)
        assertEquals(OPEN, andAgain.thePit(), "the monster worked a weighted plate")
    }

    /**
     * The square marks itself as answering a monster, which is what makes the
     * two events worth raising: without them the plate has no third way to be
     * held down and the puzzle cannot be solved without a spare item.
     */
    @Test
    fun `the plate is marked as answering a monster's step`() {
        val plate = level.triggers.single { it.location == PLATE }

        assertEquals(
            listOf(
                ScriptEvent.PARTY_ENTERED,
                ScriptEvent.PARTY_LEFT,
                ScriptEvent.ITEM_PUT_DOWN,
                ScriptEvent.ITEM_TAKEN,
                ScriptEvent.WALL_CLICKED,
                ScriptEvent.THE_CLOCK_CAME_ROUND,
                ScriptEvent.A_MONSTER_ARRIVED,
                ScriptEvent.A_MONSTER_LEFT,
            ).sortedBy { it.mask },
            ScriptEvent.entries.filter { plate.flags.reactsTo(it) }.sortedBy { it.mask },
        )
    }

    private companion object {
        const val LEVEL = 11

        val PLATE = Location(13, 2)
        val OFF_THE_PLATE = Location(12, 2)
        val PIT = Location(14, 7)

        const val OPEN = 38
        const val COVERED = 25
    }
}
