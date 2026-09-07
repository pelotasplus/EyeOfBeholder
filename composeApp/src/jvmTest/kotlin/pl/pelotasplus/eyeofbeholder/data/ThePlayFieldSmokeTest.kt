package pl.pelotasplus.eyeofbeholder.data

import kotlinx.coroutines.runBlocking
import org.junit.experimental.categories.Category
import pl.pelotasplus.eyeofbeholder.NeedsGameData
import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.features.view_cone_debug.ViewConeDebugViewModel
import kotlin.test.Test
import kotlin.test.assertEquals

@Category(NeedsGameData::class)
class ThePlayFieldSmokeTest {

    @Test
    fun `the field opens a level and stands the party on it`() = runBlocking {
        val field = ThePlayField()

        field.viewModel.onEvent(
            ViewConeDebugViewModel.Event.Initialize(
                level = "LEVEL12.INF",
                startX = 19,
                startY = 15,
                startDirection = Direction.NORTH,
            ),
        )
        field.until("the level to open") { field.viewModel.state.value.inf != null }

        assertEquals(Location(19, 15), field.viewModel.state.value.game.party.position)
    }
}
