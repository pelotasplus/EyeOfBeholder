package pl.pelotasplus.eyeofbeholder.data

import kotlinx.coroutines.runBlocking
import org.junit.experimental.categories.Category
import pl.pelotasplus.eyeofbeholder.NeedsGameData
import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.WallSide
import pl.pelotasplus.eyeofbeholder.features.view_cone_debug.ViewConeDebugViewModel.Event
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * That a square told something has happened to it actually gets its say.
 *
 * Raising the event and running the script are two different pieces — the
 * world model decides that a foot has landed on a plate, the view model runs
 * what the plate has to say about it — and the join between them is its own
 * thing to get wrong. Nothing below this level can see it go wrong: the world
 * model raises the event quite correctly, the plate is drawn as the party left
 * it, and the only sign is a door on the far side of the room that never
 * opens.
 *
 * What is checked is a change rather than a state. Both of these plates start
 * the floor at a value of their own, and a test that asked only what they hold
 * afterwards would pass on a floor where nothing had run at all.
 */
@Category(NeedsGameData::class)
class ScriptsTheFieldRunsTest {

    /**
     * The plate underfoot. Treading on 18x17 puts it down, which is the whole
     * of what that square does and the middle of how the room is worked.
     */
    @Test
    fun `a plate trodden on goes down`() = runBlocking {
        val field = ThePlayField()
        field.opened("LEVEL12.INF", at = Location(17, 17), facing = Direction.EAST)

        assertEquals(PLATE_UP, field.wallAt(THE_MIDDLE_PLATE), "it did not start up")

        field.viewModel.onEvent(Event.MoveForward)
        field.until("the plate to go down") {
            field.wallAt(THE_MIDDLE_PLATE) == PLATE_DOWN
        }
    }

    /**
     * And a plate whose say is about somewhere else. Treading on 17x17 disarms
     * the teleporter across the room at 19x13 and arms its own at 17x21, which
     * is a script running on a square nobody is standing on or looking at.
     */
    @Test
    fun `a plate trodden on reaches across the room`() = runBlocking {
        val field = ThePlayField()
        field.opened("LEVEL12.INF", at = Location(17, 16), facing = Direction.SOUTH)

        assertEquals(ARMED, field.wallAt(THE_FAR_TELEPORTER), "it did not start armed")

        field.viewModel.onEvent(Event.MoveForward)
        field.until("the far teleporter to be put out") {
            field.wallAt(THE_FAR_TELEPORTER) == CLEARED
        }

        assertEquals(
            PLATE_DOWN,
            field.wallAt(THE_WEST_PLATE),
            "the plate reached across the room without going down itself",
        )
    }

    private fun ThePlayField.wallAt(at: Location) =
        viewModel.state.value.game.wallByte(LEVEL, at, WallSide.NORTH).value

    private companion object {
        const val LEVEL = 12

        /** The values the room's own script reads and writes. */
        const val PLATE_UP = 35
        const val PLATE_DOWN = 36
        const val ARMED = 44
        const val CLEARED = 0

        val THE_MIDDLE_PLATE = Location(18, 17)
        val THE_WEST_PLATE = Location(17, 17)
        val THE_FAR_TELEPORTER = Location(19, 13)
    }
}
