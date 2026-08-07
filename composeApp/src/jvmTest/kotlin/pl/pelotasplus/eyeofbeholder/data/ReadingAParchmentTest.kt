package pl.pelotasplus.eyeofbeholder.data

import kotlinx.coroutines.runBlocking
import pl.pelotasplus.eyeofbeholder.data.model.DialogueTextId
import pl.pelotasplus.eyeofbeholder.data.model.Item
import pl.pelotasplus.eyeofbeholder.data.model.ItemTypes
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
        assertEquals(DialogueTextId(15), types.writtenOn(parchment.copy(value = 14)))
        assertEquals(DialogueTextId(1), types.writtenOn(parchment.copy(value = 0)))
    }

    /** A map is a picture, and is not a page of anything. */
    @Test
    fun `nothing is written on a map`() {
        assertNull(types.writtenOn(parchment.copy(value = -1)))
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
        assertNull(types.writtenOn(scroll))
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

        val page = types.writtenOn(hers) ?: error("nothing written on it")
        val text = DialogueTextRepositoryImpl(resources).text(page).getOrThrow()

        assertTrue(
            text.first.startsWith("Direct all travelers to Darkmoon"),
            "read '${text.first}'",
        )
    }
}
