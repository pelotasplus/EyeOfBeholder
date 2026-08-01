package pl.pelotasplus.eyeofbeholder.data

import kotlinx.coroutines.runBlocking
import pl.pelotasplus.eyeofbeholder.data.model.DialogueTextId
import pl.pelotasplus.eyeofbeholder.data.repository.DialogueTextRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.ResourceRepositoryImpl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DialogueTextTest {

    private val texts = DialogueTextRepositoryImpl(ResourceRepositoryImpl())

    private fun text(id: Int) = runBlocking { texts.text(DialogueTextId(id)).getOrThrow() }

    /** The clerics ask their question and stop; Joril answers on the next page. */
    @Test
    fun `a speech breaks where the original stops to be read`() {
        val pages = text(23).pages

        assertEquals(2, pages.size, "expected two pages, got $pages")
        assertTrue(
            pages[0].endsWith("""Have you seen her?""""),
            "first page should stop at the question, ends '${pages[0].takeLast(30)}'",
        )
        assertTrue(
            pages[1].startsWith("Joril looks quizzically"),
            "second page should start with the answer, starts '${pages[1].take(30)}'",
        )
    }

    @Test
    fun `a speech with nothing to break is a single page`() {
        assertEquals(1, text(22).pages.size)
    }

    /**
     * A colour code takes the byte after it as its colour. Reading that byte as
     * a character leaves it in the speech.
     */
    @Test
    fun `the colour a speech asks for does not end up in the speech`() {
        val pages = text(23).pages

        assertTrue(
            pages[0].startsWith("\"We have come from Waterdeep"),
            "first page starts '${pages[0].take(30)}'",
        )
        pages.forEach { page ->
            assertTrue(
                page.none { it.code < 0x20 && it != '\n' },
                "a control byte survived into '$page'",
            )
        }
    }
}
