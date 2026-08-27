package pl.pelotasplus.eyeofbeholder.data

import pl.pelotasplus.eyeofbeholder.data.model.Champion
import pl.pelotasplus.eyeofbeholder.data.model.CharacterSheet
import pl.pelotasplus.eyeofbeholder.data.model.ChampionFlags
import pl.pelotasplus.eyeofbeholder.data.model.PartySlot
import pl.pelotasplus.eyeofbeholder.data.model.SheetChoice
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Walking along the party with the arrows on an open page.
 *
 * The party has six slots and the game ships with four of them filled, so the
 * rule that is followed — the arrows walk from champion to champion and
 * round from the last to the first — is not the same as counting to six.
 */
class CharacterSheetTest {

    private val somebody = Champion.NOBODY.copy(flags = ChampionFlags(1))

    /** Four champions and the last two slots empty, as the game ships. */
    private val party = List(Champion.PARTY_SLOTS) { slot ->
        if (slot < 4) somebody else Champion.NOBODY
    }

    private fun walk(from: Int, step: Int) =
        CharacterSheet(PartySlot(from)).walked(step, party).slot.index

    @Test
    fun `the arrows step from one champion to the next`() {
        assertEquals(1, walk(from = 0, step = 1))
        assertEquals(2, walk(from = 1, step = 1))
        assertEquals(0, walk(from = 1, step = -1))
    }

    @Test
    fun `walking past the last champion comes round to the first`() {
        assertEquals(0, walk(from = 3, step = 1))
    }

    @Test
    fun `walking back from the first champion comes round to the last`() {
        assertEquals(3, walk(from = 0, step = -1))
    }

    @Test
    fun `a party of one has nowhere to walk to`() {
        val alone = List(Champion.PARTY_SLOTS) { slot ->
            if (slot == 2) somebody else Champion.NOBODY
        }
        assertEquals(PartySlot(2), CharacterSheet(PartySlot(2)).walked(1, alone).slot)
        assertEquals(PartySlot(2), CharacterSheet(PartySlot(2)).walked(-1, alone).slot)
    }

    /** Which page is open is not what the arrows are for. */
    @Test
    fun `walking keeps the page it was turned to`() {
        val stats = CharacterSheet(PartySlot(0), CharacterSheet.Page.STATS)
        assertEquals(CharacterSheet.Page.STATS, stats.walked(1, party).page)
    }

    // --- the place laid on the belongings page --------------------------------

    private val belongings = CharacterSheet(PartySlot(0), CharacterSheet.Page.BELONGINGS)

    /** The middle of the plate, which sits between the pack and the arrows. */
    private val onThePlate = 250 to 42

    @Test
    fun `the plate is clicked to eat what is held`() {
        assertEquals(SheetChoice.Eat, belongings.clicked(onThePlate.first, onThePlate.second))
    }

    /**
     * It is laid on the belongings side only: the other side of the page is
     * the champion's numbers, and there is nowhere to put a meal down.
     */
    @Test
    fun `there is no plate on the other side of the page`() {
        val stats = CharacterSheet(PartySlot(0), CharacterSheet.Page.STATS)

        assertEquals(null, stats.clicked(onThePlate.first, onThePlate.second))
    }

    /** And it does not reach the arrows beside it, which walk the party. */
    @Test
    fun `the plate does not swallow the arrows next to it`() {
        assertEquals(SheetChoice.Walk(-1), belongings.clicked(280, 42))
        assertEquals(SheetChoice.Walk(1), belongings.clicked(305, 42))
    }
}
