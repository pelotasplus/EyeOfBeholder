package pl.pelotasplus.eyeofbeholder.data

import pl.pelotasplus.eyeofbeholder.data.model.CampMenu
import pl.pelotasplus.eyeofbeholder.data.model.MenuChoice
import pl.pelotasplus.eyeofbeholder.data.model.PartySlot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Sending somebody away from the Camp menu.
 *
 * The rule worth having written down is the floor: a party may not go below
 * four, and they are told so rather than shown a list they cannot use. The
 * count happens before the question is put.
 */
class DropCharacterMenuTest {

    @Test
    fun `a party may not go below four`() {
        assertEquals(4, CampMenu.NEVER_FEWER_THAN)
    }

    @Test
    fun `everybody offered gets a line, and there is a way out after them`() {
        val menu = CampMenu.whoLeaves(listOf("Anna", "Bran", "Cwen", "Dai", "Esa"))

        assertEquals(
            listOf("Anna", "Bran", "Cwen", "Dai", "Esa", "Cancel"),
            menu.entries.map { it.label },
        )
    }

    @Test
    fun `each line sends away the one it names`() {
        val menu = CampMenu.whoLeaves(listOf("Anna", "Bran", "Cwen", "Dai", "Esa"))

        assertEquals(
            List(5) { MenuChoice.DropThisOne(PartySlot(it)) },
            menu.entries.dropLast(1).map { it.choice },
        )
    }

    /** The way out goes back where it came from rather than dropping anybody. */
    @Test
    fun `the last line is the way out`() {
        val menu = CampMenu.whoLeaves(listOf("Anna", "Bran", "Cwen", "Dai", "Esa"))

        assertEquals(MenuChoice.OpenGameOptions, menu.entries.last().choice)
    }

    /**
     * The refusal names nobody: a party of four are not asked which of them
     * to lose, they are told they may not lose one.
     */
    @Test
    fun `too few to drop offers nobody`() {
        val menu = CampMenu.tooFewToDrop()

        assertTrue(menu.entries.none { it.choice is MenuChoice.DropThisOne })
        assertEquals(
            listOf("You cannot have", "less than four", "characters."),
            menu.says,
        )
    }

    @Test
    fun `and can still be left`() {
        assertEquals(MenuChoice.OpenGameOptions, CampMenu.tooFewToDrop().entries.last().choice)
    }

    /** The line is reachable now rather than saying it is not written. */
    @Test
    fun `game options offers dropping somebody`() {
        val choices = CampMenu.gameOptions().entries.map { it.choice }

        assertTrue(MenuChoice.DropCharacter in choices)
        assertTrue(
            choices.none { it is MenuChoice.NotYet && it.what == "Drop Character" },
            "the old stub is still there",
        )
    }
}
