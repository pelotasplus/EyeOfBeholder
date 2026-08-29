package pl.pelotasplus.eyeofbeholder.data

import kotlinx.coroutines.runBlocking
import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.GameState
import pl.pelotasplus.eyeofbeholder.data.model.LevelScriptRunner
import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.Maz
import pl.pelotasplus.eyeofbeholder.data.model.PartyState
import pl.pelotasplus.eyeofbeholder.data.model.ScriptEvent
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
 * Three rooms on the second floor that are not rooms until you walk up to
 * them.
 *
 * Each is three squares sealed behind wall 26, a riveted blue panel, so that
 * the corridor reads as a dead end. Standing in the doorway dissolves all
 * three at once:
 *
 * ```
 * 606  if wall 8x4 == 26  ->  make every side of 8x4 wall 0
 * 622  if wall 7x5 == 26  ->  make every side of 7x5 wall 0
 * 638  if wall 9x5 == 26  ->  make every side of 9x5 wall 0
 * 654  end
 * ```
 *
 * The three squares are the one two ahead and the two diagonally ahead —
 * the far row of what the doorway frames — and the same script is written
 * three times, once per doorway, rotated to suit which way it faces.
 *
 * Asking `== 26` before each is what makes it once-only: a second visit finds
 * nothing to dissolve and falls through to `end`.
 */
class DissolvingTheSealedRoomsTest {

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
        ).loadInf("LEVEL2.INF").getOrThrow()
    }

    /** Which doorway opens which three, read off the three scripts. */
    private val doorways = mapOf(
        Location(2, 3) to listOf(Location(2, 1), Location(1, 2), Location(3, 2)),
        Location(8, 6) to listOf(Location(8, 4), Location(7, 5), Location(9, 5)),
        Location(10, 7) to listOf(Location(12, 7), Location(11, 6), Location(11, 8)),
    )

    private fun world(standingOn: Location) = GameState(
        party = PartyState(standingOn, Direction.NORTH),
    ).arrivingAt(level = LEVEL, places = emptyList(), maz = level.subLevels[0].maz)

    private fun GameState.steppingOnto(doorway: Location) = runBlocking {
        LevelScriptRunner(level.script, level = LEVEL).onEvent(
            triggers = level.triggers,
            event = ScriptEvent.PARTY_ENTERED,
            state = this@steppingOnto,
            at = doorway,
        ).state
    }

    private fun GameState.sidesOf(square: Location) =
        WallSide.entries.map { wallByte(LEVEL, square, it).value }

    @Test
    fun `the nine squares start sealed on every side`() {
        val world = world(Location(8, 6))

        doorways.values.flatten().forEach { square ->
            assertEquals(
                listOf(PANEL, PANEL, PANEL, PANEL),
                world.sidesOf(square),
                "$square should be walled in on all four sides",
            )
        }
    }

    /**
     * A panel is a decoration rather than a plain wall, and the number the
     * script compares against is the decoration's own index. Reading it as
     * anything else makes all three tests fail and the rooms never open.
     */
    @Test
    fun `a panel answers the number the script asks for`() {
        val north = level.subLevels[0].maz[8, 4].north

        assertEquals(Maz.WallType.Decoration(PANEL), north)
        assertEquals(PANEL, world(Location(8, 6)).wallByte(LEVEL, Location(8, 4), WallSide.NORTH).value)
    }

    @Test
    fun `stepping into a doorway dissolves the three squares it frames`() {
        doorways.forEach { (doorway, sealed) ->
            val opened = world(doorway).steppingOnto(doorway)

            sealed.forEach { square ->
                assertEquals(
                    listOf(0, 0, 0, 0),
                    opened.sidesOf(square),
                    "$doorway should have opened $square on every side",
                )
            }
        }
    }

    /** One doorway is not the others: each opens only its own three. */
    @Test
    fun `a doorway leaves the other two rooms sealed`() {
        val opened = world(Location(8, 6)).steppingOnto(Location(8, 6))

        (doorways - Location(8, 6)).values.flatten().forEach { square ->
            assertEquals(
                listOf(PANEL, PANEL, PANEL, PANEL),
                opened.sidesOf(square),
                "$square is behind another doorway and should be untouched",
            )
        }
    }

    /**
     * Walking back in finds the squares already open, so every test fails and
     * the script reaches `end` having done nothing. This is the half of it
     * that a trace shows, and the half that reads like a script doing nothing
     * at all.
     */
    @Test
    fun `walking in a second time changes nothing`() {
        val doorway = Location(8, 6)
        val opened = world(doorway).steppingOnto(doorway)
        val again = opened.steppingOnto(doorway)

        assertEquals(opened, again)
    }

    private companion object {
        const val LEVEL = 2

        /** The riveted blue panel the three rooms are sealed behind. */
        const val PANEL = 26
    }
}
