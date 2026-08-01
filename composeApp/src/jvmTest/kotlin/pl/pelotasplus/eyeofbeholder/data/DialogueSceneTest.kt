package pl.pelotasplus.eyeofbeholder.data

import kotlinx.coroutines.runBlocking
import pl.pelotasplus.eyeofbeholder.data.model.DialogueScene
import pl.pelotasplus.eyeofbeholder.data.model.Font
import pl.pelotasplus.eyeofbeholder.data.repository.FontRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.ResourceRepositoryImpl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
