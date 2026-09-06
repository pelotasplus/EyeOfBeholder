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
 * The scene a party get for taking the twelfth floor's advice.
 *
 * Everything here is transcribed rather than composed: which corner of the
 * sheet each of the four pictures is cut from, how long each is held, which of
 * the game's own lines is written under it, and where the two pauses for the
 * player fall. Nudging any of it by eye would go unnoticed, since nothing in
 * the dungeon plays this scene — see [CutScene.THE_TRICK_ON_THE_TWELFTH].
 */
@Category(NeedsGameData::class)
class TheTrickOnTheTwelfthTest {

    private val scene = CutScene.THE_TRICK_ON_THE_TWELFTH

    /**
     * Four pictures out of one sheet, laid out two by two. A picture's across
     * is given in eighths of the screen and its down in whole pixels, which is
     * how the instruction that puts one up reads them — so the pair of numbers
     * for one corner look nothing like each other.
     */
    @Test
    fun `it is four cuts of one sheet in a square`() {
        assertEquals(4, scene.beats.size)

        assertTrue(scene.beats.all { it.shows.pictureName == "KHELDRAN" })

        assertEquals(
            listOf(0 to 0, 20 to 0, 0 to 96, 20 to 96),
            scene.beats.map { it.shows.x to it.shows.y },
            "the four corners are not the sheet's",
        )
    }

    /** Two waits of ten and a last of seven, and one sound, on the second. */
    @Test
    fun `it is held to its own count`() {
        assertEquals(
            listOf(0, 10, 10, 7),
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
     * It stops twice for the player and nowhere else: once after the first
     * line, and once at the end. The pictures between those two run on their
     * own, which is what makes them an animation rather than pages.
     */
    @Test
    fun `it waits for the player twice`() {
        assertEquals(
            listOf("MORE", null, null, "OK"),
            scene.beats.map { it.readOn },
            "the scene stops in the wrong places",
        )
    }

    /**
     * The first line is the game's own and is written out here, having no
     * number to be looked up by. The last is looked up, and is the one that
     * gives the trick away.
     */
    @Test
    fun `he gloats twice, and the second time explains it`() = runBlocking {
        assertEquals("    Such trusting whelps!", scene.beats.first().spoken)

        val told = assertNotNull(scene.beats.last().says, "the last picture says nothing")
        val gloat = DialogueTextRepositoryImpl(ResourceRepositoryImpl())
            .text(told)
            .getOrThrow()
            .pages
            .joinToString(" ")

        assertTrue(
            gloat.contains("altering my appearance") && gloat.contains("Khelben"),
            "the last line is not the one that gives it away: $gloat",
        )
    }
}
