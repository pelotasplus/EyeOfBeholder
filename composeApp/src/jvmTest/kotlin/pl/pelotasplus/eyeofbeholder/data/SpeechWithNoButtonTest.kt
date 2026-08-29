package pl.pelotasplus.eyeofbeholder.data

import kotlinx.coroutines.runBlocking
import org.junit.experimental.categories.Category
import pl.pelotasplus.eyeofbeholder.NeedsGameData
import pl.pelotasplus.eyeofbeholder.data.model.DialogueTextId
import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.MessageId
import pl.pelotasplus.eyeofbeholder.data.model.ScriptQuestion
import pl.pelotasplus.eyeofbeholder.data.model.script.Dialog
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
 * A speech is read by clicking the word under it — unless there is no word,
 * and then it is not clicked at all: the speech goes up and the script carries
 * straight on. The engine says so outright, drawing the button only when the
 * page-break string has a first character.
 *
 * Level 8's mouths are why this matters. One speaks, and what follows the
 * speech is the mouth itself moving; a button nobody can see would hold the
 * script there for good.
 */
@Category(NeedsGameData::class)
class SpeechWithNoButtonTest {

    private val speech = ScriptQuestion(
        textId = DialogueTextId(44),
        buttons = listOf(MessageId(10)),
        scene = emptyList(),
        waitsToBeRead = true,
    )

    @Test
    fun `a speech with a word under it is clicked`() {
        assertTrue(speech.hasSomethingToClick(listOf("ok")))
    }

    @Test
    fun `a speech with no word under it is not`() {
        assertFalse(speech.hasSomethingToClick(listOf("")))
        assertFalse(speech.hasSomethingToClick(emptyList()))
    }

    /** A question is its answers, so it waits however they read. */
    @Test
    fun `a question always waits`() {
        val asked = speech.copy(waitsToBeRead = false)

        assertTrue(asked.hasSomethingToClick(listOf("")))
        assertFalse(asked.hasSomethingToClick(emptyList()))
    }

    /**
     * And the level that made the rule necessary: the mouth on level 8 speaks
     * with an empty word under it, while the reply that ends the exchange has
     * "ok" to click.
     */
    @Test
    fun `the mouth on level 8 speaks with nothing to click`() = runBlocking {
        val resources = ResourceRepositoryImpl()
        val inf = InfRepositoryImpl(
            resourceRepository = resources,
            mazRepository = MazRepositoryImpl(resources),
            vmpRepository = VmpRepositoryImpl(resources),
            vcnRepository = VcnRepositoryImpl(resources),
            palRepository = PalRepositoryImpl(resources),
            cpsRepository = CpsRepositoryImpl(resources),
            decRepository = DecRepositoryImpl(resources),
        ).loadInf("LEVEL8.INF").getOrThrow()

        val trigger = inf.triggers.single { it.location == Location(23, 25) }
        val spoken = inf.script
            .dropWhile { it.offset != trigger.script.offset }
            .map { it.token }
            .takeWhile { it !is Dialog.CloseDialog }
            .filterIsInstance<Dialog.DialogText>()

        assertEquals(
            listOf(DialogueTextId(44), DialogueTextId(20)),
            spoken.map { it.textId },
            "the mouth speaks, then the exchange is closed off",
        )
        // a message that says nothing is no message at all, so the word under
        // the mouth's speech is not a blank one — there is none
        assertEquals(null, inf.message(spoken.first().pageBreakLabel))
        assertEquals("ok", inf.message(spoken.last().pageBreakLabel))
    }
}
