package pl.pelotasplus.eyeofbeholder.data

import kotlinx.coroutines.runBlocking
import org.junit.experimental.categories.Category
import pl.pelotasplus.eyeofbeholder.NeedsGameData
import pl.pelotasplus.eyeofbeholder.data.model.DialogueScene
import pl.pelotasplus.eyeofbeholder.data.model.Font
import pl.pelotasplus.eyeofbeholder.data.repository.FontRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.ResourceRepositoryImpl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Category(NeedsGameData::class)
class DialogueSceneTest {

    private val font: Font = runBlocking {
        FontRepositoryImpl(ResourceRepositoryImpl()).loadFont("FONT6.FNT").getOrThrow()
    }

    private fun layout(vararg labels: String) = DialogueScene.layout(
        frame = null,
        portrait = null,
        text = "well?",
        buttonLabels = labels.toList(),
        font = font,
    )

    /** A speech is read on a corner of its own, wherever the speech ends. */
    @Test
    fun `the button a speech is read on keeps its own corner`() {
        val short = DialogueScene.layout(
            frame = null, portrait = null, text = "one line",
            buttonLabels = listOf(DialogueScene.MORE), font = font, waitsToBeRead = true,
        ).buttons.single()

        val long = DialogueScene.layout(
            frame = null, portrait = null, text = "a speech that runs on ".repeat(20),
            buttonLabels = listOf(DialogueScene.MORE), font = font, waitsToBeRead = true,
        ).buttons.single()

        assertEquals(221, short.left)
        assertEquals(189, short.top)
        assertEquals(short, long, "the page break must not move with the text")
    }

    @Test
    fun `answers still follow the speech down the box`() {
        val short = layout("yes", "no").buttons.first()
        val long = DialogueScene.layout(
            frame = null, portrait = null, text = "a speech that runs on ".repeat(20),
            buttonLabels = listOf("yes", "no"), font = font,
        ).buttons.first()

        assertTrue(long.top > short.top, "answers should sit below the speech")
    }

    @Test
    fun `two answers sit inset`() {
        assertEquals(listOf(59, 166), layout("yes", "no").buttons.map { it.left })
    }

    /** Level 5's temple asks three: inquire, attack, leave. */
    @Test
    fun `three answers spread across the full width`() {
        assertEquals(
            listOf(4, 112, 220),
            layout("inquire", "attack", "leave").buttons.map { it.left },
        )
    }

    @Test
    fun `three answers do not overlap each other`() {
        val buttons = layout("inquire", "attack", "leave").buttons

        buttons.zipWithNext { left, right ->
            assertTrue(
                left.left + DialogueScene.Button.WIDTH <= right.left,
                "'${left.label}' runs into '${right.label}'",
            )
        }
    }

    @Test
    fun `every answer is clickable where it is drawn`() {
        layout("inquire", "attack", "leave").buttons.forEachIndexed { index, button ->
            assertTrue(
                button.contains(button.left, button.top),
                "button $index does not answer a click on itself",
            )
        }
    }
}
