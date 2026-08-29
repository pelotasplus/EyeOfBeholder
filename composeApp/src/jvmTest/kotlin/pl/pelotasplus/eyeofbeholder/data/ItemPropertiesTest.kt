package pl.pelotasplus.eyeofbeholder.data

import kotlinx.coroutines.runBlocking
import org.junit.experimental.categories.Category
import pl.pelotasplus.eyeofbeholder.NeedsGameData
import pl.pelotasplus.eyeofbeholder.data.model.Item
import pl.pelotasplus.eyeofbeholder.data.model.ItemKind
import pl.pelotasplus.eyeofbeholder.data.model.ItemNames
import pl.pelotasplus.eyeofbeholder.data.model.ItemProperties
import pl.pelotasplus.eyeofbeholder.data.model.ItemTypes
import pl.pelotasplus.eyeofbeholder.data.repository.ItemTypesRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.ItemsRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.ResourceRepositoryImpl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The word ITEMTYPE.DAT ends each record with, read as what it says: what kind
 * of thing every item of that type is, and whether a monster's blow can ruin
 * one.
 *
 * The names of the kinds are asserted against what the shipped items actually
 * are — a Long Sword is swung, a Dark Moon Key is a key — because the numbers
 * mean nothing on their own and a table read one place out would still parse.
 */
@Category(NeedsGameData::class)
class ItemPropertiesTest {

    private val resources = ResourceRepositoryImpl()

    private val types: ItemTypes = runBlocking {
        ItemTypesRepositoryImpl(resources).loadItemTypes().getOrThrow()
    }

    private val dungeon = runBlocking {
        ItemsRepositoryImpl(resources).loadItems().getOrThrow()
    }

    private val names: ItemNames get() = dungeon.names

    /** By either name: what a thing is called before it is known, or after. */
    private fun dungeonNamed(name: String): Item = dungeon.items.first {
        names[it.nameUnidentified] == name || names[it.nameIdentified] == name
    }

    @Test
    fun `the shipped items are the kinds they look like`() {
        listOf(
            "Long Sword" to ItemKind.SWUNG_BY_HAND,
            "Dagger" to ItemKind.THROWN,
            "Bow" to ItemKind.A_LAUNCHER,
            "Chainmail" to ItemKind.ARMOUR,
            "Lock picks" to ItemKind.AN_ODDMENT,
            "Spellbook" to ItemKind.SPELLBOOK,
            "Cleric Holy symbol" to ItemKind.HOLY_SYMBOL,
            "Iron Rations" to ItemKind.FOOD,
            "Set of bones" to ItemKind.BONES,
            "Parchment" to ItemKind.SOMETHING_TO_READ,
            "Stone Gem" to ItemKind.A_STONE_SHAPE,
            "Dark Moon Key" to ItemKind.KEY,
            "Potion" to ItemKind.POTION,
            "Blue Gem" to ItemKind.GEM,
            "Ring" to ItemKind.RING,
            "Wand" to ItemKind.WAND,
            "Horn" to ItemKind.A_HORN,
        ).forEach { (name, kind) ->
            assertEquals(kind, types.kindOf(dungeonNamed(name)), name)
        }
    }

    /**
     * Every kind in the table is one this knows the name of. A number nobody
     * has named is the one thing this cannot say anything about, and it is
     * better to find out here than by treating it as armour.
     */
    @Test
    fun `every kind the game uses has a name`() {
        dungeon.items.filter { it.exists }.forEach {
            assertNotNull(types.kindOf(it), "item type ${it.type.value} is of no named kind")
        }
    }

    /** The one number the game leaves out, and the one that answers nothing. */
    @Test
    fun `a kind the game does not use is no kind at all`() {
        assertNull(ItemKind.of(17))
        assertNull(ItemProperties(17).kind)
    }

    /**
     * What a rusting blow can eat is metal and what it cannot is not: the
     * table is asked about nowhere else in the game, so its own contents are
     * the only thing that can say it was read right.
     */
    @Test
    fun `what perishes is the metal and the worked things`() {
        listOf("Long Sword", "Chainmail", "Shield", "Helmet", "Ring", "Wand")
            .forEach { assertTrue(types.perishes(dungeonNamed(it)), "$it does not perish") }

        listOf("Robe", "Cloak", "Leather armor", "Staff", "Rations")
            .forEach { assertFalse(types.perishes(dungeonNamed(it)), "$it perishes") }
    }

    /**
     * What a door wants is safe from being eaten, which is the game's own
     * doing: a horn and a key are kinds of their own, and neither kind is
     * marked. A party who lost one to a rusting blow could not go on.
     */
    @Test
    fun `nothing a door asks for can be destroyed`() {
        listOf("Horn", "Dark Moon Key", "Skull Key", "Tooth", "Tuning Fork")
            .forEach { assertFalse(types.perishes(dungeonNamed(it)), "$it perishes") }
    }

    /** The kind is the low seven bits and perishing is the one above them. */
    @Test
    fun `the two halves of the word are read apart`() {
        assertEquals(ItemKind.SWUNG_BY_HAND, ItemProperties(1).kind)
        assertFalse(ItemProperties(1).perishes)

        assertEquals(ItemKind.SWUNG_BY_HAND, ItemProperties(0x81).kind)
        assertTrue(ItemProperties(0x81).perishes)

        assertEquals(ItemKind.ARMOUR, ItemProperties(0x80).kind)
        assertTrue(ItemProperties(0x80).perishes)
    }
}
