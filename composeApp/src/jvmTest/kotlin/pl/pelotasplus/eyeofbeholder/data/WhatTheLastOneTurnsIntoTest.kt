package pl.pelotasplus.eyeofbeholder.data

import kotlinx.coroutines.runBlocking
import org.junit.experimental.categories.Category
import pl.pelotasplus.eyeofbeholder.NeedsGameData
import pl.pelotasplus.eyeofbeholder.data.model.CutScene
import pl.pelotasplus.eyeofbeholder.data.model.TrackIndex
import pl.pelotasplus.eyeofbeholder.data.repository.DialogueTextRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.ResourceRepositoryImpl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The scene the party are shown when the thing at the end of the dungeon is
 * cut down and gets up again.
 *
 * Transcribed the same way the twelfth floor's scene is: which corner of the
 * sheet each of the four pictures is cut from, how long each is held, and
 * where the one pause for the player falls. What it looks like is guarded by
 * the golden frames; this is only that the right things are asked for.
 */
@Category(NeedsGameData::class)
class WhatTheLastOneTurnsIntoTest {

    private val scene = CutScene.WHAT_THE_LAST_ONE_TURNS_INTO

    /**
     * Four pictures out of one sheet, laid out two by two, and its own sheet
     * rather than the one the same shape of scene is cut from on the twelfth.
     */
    @Test
    fun `it is four cuts of its own sheet`() {
        assertEquals(4, scene.beats.size)

        assertTrue(scene.beats.all { it.shows.pictureName == "DRANX" })

        assertEquals(
            listOf(0 to 0, 20 to 0, 0 to 96, 20 to 96),
            scene.beats.map { it.shows.x to it.shows.y },
            "the four corners are not the sheet's",
        )
    }

    /**
     * Two waits of seven and a last of eighteen, and one sound, on the second
     * — the same laugh the twelfth floor's scene is played over.
     */
    @Test
    fun `it is held to its own count`() {
        assertEquals(
            listOf(0, 7, 7, 18),
            scene.beats.map { it.holdsFor.value },
            "the pauses are not the ones written down",
        )

        assertEquals(
            listOf(null, TrackIndex(56), null, null),
            scene.beats.map { it.heardAs },
            "the laugh is on the wrong picture",
        )
    }

    /**
     * It stops once, after the line that opens it, and then runs to the end
     * without asking: what follows the last picture is the fight, so nothing
     * waits there to be dismissed.
     */
    @Test
    fun `it waits for the player once, at the start`() {
        assertEquals(
            listOf("MORE", null, null, null),
            scene.beats.map { it.readOn },
            "the scene stops in the wrong places",
        )
    }

    /** And the line it opens with is the one about getting back up. */
    @Test
    fun `the line is the one about him rising again`() = runBlocking {
        val told = assertNotNull(scene.beats.first().says, "the first picture says nothing")

        val said = DialogueTextRepositoryImpl(ResourceRepositoryImpl())
            .text(told)
            .getOrThrow()
            .pages
            .joinToString(" ")

        assertTrue(
            said.contains("collapses to the floor") && said.contains("starts to rise"),
            "the opening line is not the one that says he is not dead: $said",
        )
    }
}
