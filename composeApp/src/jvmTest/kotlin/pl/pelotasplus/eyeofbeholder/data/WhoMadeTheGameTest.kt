package pl.pelotasplus.eyeofbeholder.data

import kotlinx.coroutines.runBlocking
import org.junit.experimental.categories.Category
import pl.pelotasplus.eyeofbeholder.NeedsGameData
import pl.pelotasplus.eyeofbeholder.data.model.sequence.FinaleFrames
import pl.pelotasplus.eyeofbeholder.data.model.sequence.TheCredits
import pl.pelotasplus.eyeofbeholder.data.repository.CreditsRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.ResourceRepositoryImpl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The list of names the game ends on, read out of its own file.
 *
 * The file is a byte stream rather than text: a marker in front of a line
 * turns it into a picture or into the smaller font, and getting either wrong
 * is not an error — it is a line of names that quietly reads as a control
 * byte, or a title that never appears.
 */
@Category(NeedsGameData::class)
class WhoMadeTheGameTest {

    private val credits = runBlocking {
        CreditsRepositoryImpl(ResourceRepositoryImpl()).credits().getOrThrow()
    }

    /**
     * It opens on a picture and a long silence: the first title, and then
     * fourteen empty lines carrying it up the screen before anything follows.
     */
    @Test
    fun `it opens on a title and a gap`() {
        assertIs<TheCredits.Line.Picture>(credits.first())

        val gap = credits.drop(1)
            .takeWhile { it is TheCredits.Line.Words && it.words.isEmpty() }

        assertEquals(14, gap.size, "the pause before the second title is the wrong length")
    }

    /**
     * Every picture it asks for is one of the shapes its two sheets are cut
     * into. This is the join between the file and the pictures, and nothing
     * else checks it: a number off by one gives a title that is silently
     * missing, or somebody else's shape in its place.
     */
    @Test
    fun `every title it names has been cut`() {
        val cut = listOf(TheCredits.TITLES, TheCredits.MORE_TITLES)
            .flatMap { FinaleFrames.SHAPES[it].orEmpty() }
            .map { it.index }
            .toSet()

        val asked = credits.filterIsInstance<TheCredits.Line.Picture>().map { it.shape }.toSet()

        assertTrue(asked.isNotEmpty(), "the credits show no titles at all")
        assertEquals(
            emptySet(),
            asked - cut,
            "the credits ask for titles that neither sheet is cut into",
        )
    }

    /** And the names themselves are names rather than bytes. */
    @Test
    fun `it says who directed it`() {
        val said = credits.filterIsInstance<TheCredits.Line.Words>().map { it.words }

        assertTrue(
            said.any { it.contains("Director: Brett W. Sperry") },
            "the roll does not name the director",
        )
        assertTrue(
            said.any { it.contains("Philip W. Gorrow") },
            "the roll does not name whose idea it was",
        )
    }
}
