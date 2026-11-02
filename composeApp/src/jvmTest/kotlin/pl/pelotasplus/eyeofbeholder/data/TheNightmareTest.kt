package pl.pelotasplus.eyeofbeholder.data

import pl.pelotasplus.eyeofbeholder.data.model.CutScene
import pl.pelotasplus.eyeofbeholder.data.model.DialogueTextId
import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.GameState
import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.PartyState
import pl.pelotasplus.eyeofbeholder.data.model.Rest
import pl.pelotasplus.eyeofbeholder.data.model.Resting
import pl.pelotasplus.eyeofbeholder.data.model.TrackIndex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * The third floor's refusal to let anybody sleep. The cuts and waits are the
 * game's table of thirteen steps; the sheet is two by two.
 */
class TheNightmareTest {

    private val world = GameState(party = PartyState(Location(5, 5), Direction.NORTH))

    @Test
    fun `a rest on the third floor is a nightmare and no rest`() {
        assertIs<Rest.Dreamt>(Resting(kinds = emptyList(), level = 3).begin(world))
    }

    @Test
    fun `a floor that forbids rest forbids it before anybody dreams`() {
        assertIs<Rest.NotHere>(Resting(kinds = emptyList(), level = 3).begin(world.copy(preventRest = true)))
    }

    @Test
    fun `no other floor dreams`() {
        listOf(1, 2, 4, 5, 16).forEach {
            assertIs<Rest.Slept>(Resting(kinds = emptyList(), level = it).begin(world))
        }
    }

    @Test
    fun `it plays the thirteen steps and waits to be read`() {
        val beats = CutScene.THE_NIGHTMARE.beats
        val corners = mapOf(0 to (0 to 0), 1 to (20 to 0), 2 to (0 to 96), 3 to (20 to 96))
        val steps = listOf(1, 2, 3, 2, 1, 0, 1, 2, 3, 3, 2, 1, 0)

        assertEquals(steps.map { corners.getValue(it) } + (0 to 0), beats.map { it.shows.x to it.shows.y })
        assertEquals(listOf(7, 7, 12, 7, 7, 12, 7, 7, 12, 12, 7, 7, 12, 0), beats.map { it.holdsFor.value })
        assertEquals("OK", beats.last().readOn)
        assertEquals(listOf(TrackIndex(54)), beats.mapNotNull { it.heardAs })
        assertEquals(listOf(DialogueTextId(99)), beats.mapNotNull { it.says }, "the line is written after itself")
    }
}
