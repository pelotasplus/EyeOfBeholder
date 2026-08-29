package pl.pelotasplus.eyeofbeholder.data

import pl.pelotasplus.eyeofbeholder.data.model.CampMenu
import pl.pelotasplus.eyeofbeholder.data.model.MenuChoice
import pl.pelotasplus.eyeofbeholder.data.model.MenuEntry
import pl.pelotasplus.eyeofbeholder.data.model.Naming
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What a mouse can still do while a save is being named.
 *
 * The slots go deaf, so that a click meant for the caret cannot land on
 * another row and take the naming with it. Everything else stays live —
 * Cancel above all, which is the only way out a mouse has.
 */
class CancellingANameTest {

    private val slots = CampMenu.slots(saving = true) { null }

    private val cancel = slots.entries.last()

    /** The middle of an entry, in the coordinates a click arrives in. */
    private fun clickOn(menu: CampMenu, entry: MenuEntry) = menu.clicked(
        x = menu.left + entry.left + entry.width / 2,
        y = menu.top + entry.top + entry.height / 2,
    )

    @Test
    fun `the last entry is the one that cancels`() {
        assertEquals("Cancel", cancel.label)
    }

    @Test
    fun `cancel works while nothing is being named`() {
        assertEquals(MenuChoice.OpenGameOptions, clickOn(slots, cancel))
    }

    @Test
    fun `cancel still works while a name is being typed`() {
        val naming = slots.copy(naming = Naming(slot = 0, typed = "half a na"))

        assertEquals(
            MenuChoice.OpenGameOptions,
            clickOn(naming, cancel),
            "the only way out a mouse has does nothing",
        )
    }

    /**
     * The rows themselves stay deaf, which is what the deafness was for: a
     * click on another slot must not move the caret off a half-typed name.
     */
    @Test
    fun `the slots are deaf while a name is being typed`() {
        val naming = slots.copy(naming = Naming(slot = 0, typed = "half a na"))

        val onAnotherRow = clickOn(naming, naming.rowOf(3))

        assertNull(onAnotherRow, "clicking another slot interrupted the naming")
    }

    @Test
    fun `a slot answers normally when nothing is being named`() {
        val chosen = clickOn(slots, slots.rowOf(3))

        assertTrue(chosen is MenuChoice.UseSlot && chosen.slot == 3, "the row did not answer")
    }
}
