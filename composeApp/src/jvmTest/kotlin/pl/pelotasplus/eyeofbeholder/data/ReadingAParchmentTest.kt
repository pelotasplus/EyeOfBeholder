package pl.pelotasplus.eyeofbeholder.data

import kotlinx.coroutines.runBlocking
import pl.pelotasplus.eyeofbeholder.data.model.DialogueTextId
import pl.pelotasplus.eyeofbeholder.data.model.Item
import pl.pelotasplus.eyeofbeholder.data.model.ItemTypes
import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.OnAParchment
import pl.pelotasplus.eyeofbeholder.data.model.script.ItemOverrides
import pl.pelotasplus.eyeofbeholder.data.repository.DialogueTextRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.ItemTypesRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.ItemsRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.ResourceRepositoryImpl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A letter, a note or a map is the one kind of thing whose value says what is
 * written on it rather than what it does, and the original reads one by
 * putting that page of TEXT.DAT up. The value counts from zero; the texts are
 * numbered from one.
 */
class ReadingAParchmentTest {

    private val resources = ResourceRepositoryImpl()

    private val types: ItemTypes by lazy {
        runBlocking { ItemTypesRepositoryImpl(resources).loadItemTypes().getOrThrow() }
    }

    private val dungeonItems: List<Item> by lazy {
        runBlocking { ItemsRepositoryImpl(resources).loadItems().getOrThrow().items }
    }

    /** The one the old woman on level 4 is carrying, before a script says which. */
    private val parchment: Item by lazy { dungeonItems[28] }

    @Test
    fun `the page written on a parchment is its value and one`() {
        assertEquals(
            OnAParchment.Writing(DialogueTextId(15)),
            types.whatIsOn(parchment.copy(value = 14)),
        )
        assertEquals(
            OnAParchment.Writing(DialogueTextId(1)),
            types.whatIsOn(parchment.copy(value = 0)),
        )
    }

    /**
     * Below zero the value counts the three maps instead, each cut from its own
     * corner of the sheet the three of them share. The game holds one of each:
     * on level 1 at 23x11, on level 8 at 27x2 and on level 12 at 8x27.
     */
    @Test
    fun `a value below zero is one of the three maps`() {
        assertEquals(OnAParchment.Map(0, 0), types.whatIsOn(parchment.copy(value = -1)))
        assertEquals(OnAParchment.Map(160, 0), types.whatIsOn(parchment.copy(value = -2)))
        assertEquals(OnAParchment.Map(0, 96), types.whatIsOn(parchment.copy(value = -3)))
    }

    /** And there is no fourth, so nothing comes back rather than a corner off the sheet. */
    @Test
    fun `there is no fourth map`() {
        assertNull(types.whatIsOn(parchment.copy(value = -4)))
    }

    /**
     * Every map lying in the dungeon is one of the three, one per floor. The
     * parchment above is on no floor at all and is not among them: it is the
     * blank a script copies, and being a map is only what it is until one says
     * otherwise.
     */
    @Test
    fun `the maps lying in the dungeon are the three that exist`() {
        val maps = dungeonItems
            .filter { it.level > Item.CARRIED_LEVEL && it.value < 0 }
            .filter { types.whatIsOn(it.copy(value = 0)) != null }
            .map { it.level to it.location }

        assertEquals(
            listOf(1 to Location(23, 11), 8 to Location(27, 2), 12 to Location(8, 27)),
            maps,
        )
    }

    /**
     * A spell scroll is not read this way, and item 208 is the sharpest case
     * the game holds: a Mage Scroll whose value is also 14. On it the number
     * is the spell written on it — Hold Person — and putting up page 15 of the
     * speeches instead would be nonsense.
     */
    @Test
    fun `a spell scroll is not something to read`() {
        val scroll = dungeonItems[208]

        assertEquals(14, scroll.value, "item 208 is the scroll that shares the value")
        assertNull(types.whatIsOn(scroll))
    }

    /**
     * The whole chain the level walks: the script makes a copy of the parchment
     * and says its value is 14, and reading that copy is what the clerics sent
     * her to do — not the page before it, which is her walking out of the
     * forest.
     */
    @Test
    fun `the old woman's orders are what her parchment says`() = runBlocking {
        val hers = ItemOverrides(value = 14).applyTo(parchment)

        val written = types.whatIsOn(hers) as? OnAParchment.Writing ?: error("nothing written on it")
        val text = DialogueTextRepositoryImpl(resources).text(written.page).getOrThrow()

        assertTrue(
            text.first.startsWith("Direct all travelers to Darkmoon"),
            "read '${text.first}'",
        )
    }
}
