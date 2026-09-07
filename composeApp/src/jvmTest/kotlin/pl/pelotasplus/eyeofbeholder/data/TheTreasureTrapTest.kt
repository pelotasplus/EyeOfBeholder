package pl.pelotasplus.eyeofbeholder.data

import kotlinx.coroutines.runBlocking
import org.junit.experimental.categories.Category
import pl.pelotasplus.eyeofbeholder.NeedsGameData
import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.GameState
import pl.pelotasplus.eyeofbeholder.data.model.HitPoints
import pl.pelotasplus.eyeofbeholder.data.model.LevelScriptRunner
import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.MonsterInstance
import pl.pelotasplus.eyeofbeholder.data.model.MonsterSlot
import pl.pelotasplus.eyeofbeholder.data.model.MonsterTypeId
import pl.pelotasplus.eyeofbeholder.data.model.PartyState
import pl.pelotasplus.eyeofbeholder.data.model.ScriptEvent
import pl.pelotasplus.eyeofbeholder.data.model.SquarePlace
import pl.pelotasplus.eyeofbeholder.data.model.WallSide
import pl.pelotasplus.eyeofbeholder.data.model.script.SetFlag
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
 * The treasure on the third floor's (13,14), and what taking it costs.
 *
 * Three things lie there — a cursed long sword, bracers, a shield — and the
 * square watches for one of them leaving rather than for the party arriving:
 *
 * ```
 * 826  if level flag 8 == 0  ->  ...
 * 836      close the door on 12x12
 * 839      make a noise on 14x14
 * 843      open every side of 14x13
 * 848      open every side of 14x15
 * 853  end
 * ```
 *
 * The two squares opened hold five waiting monsters, and the door shut is the
 * way out.
 *
 * The guard on the front of it never becomes false: no script on the third
 * floor sets that floor's flag 8, so the trap springs afresh for each of the
 * three things taken. Nothing here fixes that — it is what the level data
 * says, and the second springing is inaudible anyway, since a shut door
 * cannot shut and an open square cannot open. This pins it so that it is not
 * mistaken later for a bug in the runner.
 */
@Category(NeedsGameData::class)
class TheTreasureTrapTest {

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
        ).loadInf("LEVEL3.INF").getOrThrow()
    }

    /**
     * The party standing on the treasure with the way out open, which is how
     * they got there: the door starts shut and a switch out in the corridor
     * opens it.
     */
    private fun world() = GameState(
        party = PartyState(TREASURE, Direction.EAST),
    )
        .arrivingAt(level = LEVEL, places = emptyList(), maz = level.subLevels[0].maz)
        .doorSetGoing(LEVEL, WAY_OUT, WallSide.NORTH, opening = true)
        .doorsFullyOpen()

    private tailrec fun GameState.doorsFullyOpen(): GameState =
        if (swinging.isEmpty()) this else doorsStepped().world.doorsFullyOpen()

    private fun GameState.onTheSquare(event: ScriptEvent) = runBlocking {
        LevelScriptRunner(level.script, level = LEVEL).onEvent(
            triggers = level.triggers,
            event = event,
            state = this@onTheSquare,
            at = TREASURE,
        ).state
    }

    private fun GameState.sidesOf(square: Location) =
        WallSide.entries.map { wallByte(LEVEL, square, it).value }

    private fun GameState.cagesAreOpen() =
        CAGES.all { sidesOf(it) == listOf(0, 0, 0, 0) }

    @Test
    fun `the cages start walled in`() {
        assertFalse(world().cagesAreOpen())
    }

    @Test
    fun `taking something opens the cages`() {
        assertTrue(world().onTheSquare(ScriptEvent.ITEM_TAKEN).cagesAreOpen())
    }

    @Test
    fun `taking something shuts the way out`() {
        val sprung = world().onTheSquare(ScriptEvent.ITEM_TAKEN)

        assertEquals(
            listOf(WAY_OUT to false),
            sprung.swinging.map { it.at to it.opening },
        )
    }

    /**
     * But not onto anything standing in it. A door never comes down on a
     * monster, and one of the five just let out has a clear walk to that
     * doorway — so the trap's own guards can hold the way out open.
     *
     * The refusal is the door's alone: the rest of the script runs, and the
     * cages open behind them as they always do.
     */
    @Test
    fun `and does not shut it on a monster standing in the doorway`() {
        val blocked = world()
            .withAMonsterOn(WAY_OUT)
            .onTheSquare(ScriptEvent.ITEM_TAKEN)

        assertEquals(emptyList(), blocked.swinging, "the door came down on a monster")
        assertTrue(blocked.cagesAreOpen(), "the refusal stopped the rest of the script")
    }

    /** And the party in a doorway hold it open the same way. */
    @Test
    fun `nor on the party themselves`() {
        assertTrue(world().doorwayIsClear(WAY_OUT))
        assertFalse(
            world().copy(party = PartyState(WAY_OUT, Direction.EAST)).doorwayIsClear(WAY_OUT),
        )
    }

    private fun GameState.withAMonsterOn(at: Location) = copy(
        monsters = listOf(
            MonsterInstance(
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
            ),
        ),
    )

    /** The square is not stepped on to spring it — it is robbed. */
    @Test
    fun `walking onto the square does nothing`() {
        assertFalse(world().onTheSquare(ScriptEvent.PARTY_ENTERED).cagesAreOpen())
    }

    /**
     * The one thing that would make it once-only, and the level data has none
     * of it — so the assertion is about the whole floor rather than about one
     * routine, which is where it would have to be written.
     */
    @Test
    fun `nothing on the floor ever sets the flag the trap asks about`() {
        assertEquals(
            emptyList(),
            level.script
                .map { it.token }
                .filterIsInstance<SetFlag.LevelFlag>()
                .filter { it.bit.index == ARMED },
        )
    }

    @Test
    fun `so the second thing taken springs it again`() {
        val twice = world()
            .onTheSquare(ScriptEvent.ITEM_TAKEN)
            .copy(swinging = emptyList())
            .onTheSquare(ScriptEvent.ITEM_TAKEN)

        assertEquals(
            listOf(WAY_OUT to false),
            twice.swinging.map { it.at to it.opening },
        )
    }

    private companion object {
        const val LEVEL = 3

        /** The bit the trap asks about, which nothing writes. */
        const val ARMED = 8

        val TREASURE = Location(13, 14)
        val WAY_OUT = Location(12, 12)
        val CAGES = listOf(Location(14, 13), Location(14, 15))
    }
}
