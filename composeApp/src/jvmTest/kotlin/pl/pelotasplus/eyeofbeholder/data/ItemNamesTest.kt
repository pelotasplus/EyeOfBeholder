package pl.pelotasplus.eyeofbeholder.data

import kotlinx.coroutines.runBlocking
import org.junit.experimental.categories.Category
import pl.pelotasplus.eyeofbeholder.NeedsGameData
import pl.pelotasplus.eyeofbeholder.data.model.Item
import pl.pelotasplus.eyeofbeholder.data.model.ItemKind
import pl.pelotasplus.eyeofbeholder.data.model.ItemMessages
import pl.pelotasplus.eyeofbeholder.data.model.ItemNames
import pl.pelotasplus.eyeofbeholder.data.model.ItemTypes
import pl.pelotasplus.eyeofbeholder.data.model.Sex
import pl.pelotasplus.eyeofbeholder.data.repository.ItemTypesRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.ItemsRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.OriginalSaveRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.ResourceRepositoryImpl
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * What the game calls a thing when it is picked up.
 *
 * The names are the shipped ones, so what a champion's own gear is called is
 * the game's answer and not one written down here.
 */
@Category(NeedsGameData::class)
class ItemNamesTest {

    private val resources = ResourceRepositoryImpl()

    private val dungeon = runBlocking {
        ItemsRepositoryImpl(resources).loadItems().getOrThrow()
    }

    private val names: ItemNames = dungeon.names

    private val types: ItemTypes = runBlocking {
        ItemTypesRepositoryImpl(resources).loadItemTypes().getOrThrow()
    }

    private val save = runBlocking {
        OriginalSaveRepositoryImpl(resources)
            .loadOriginalSave(OriginalSaveRepositoryImpl.QUICK_START).getOrThrow()
    }

    private fun carried(champion: Int, slot: Int): Item =
        save.items[save.party[champion].carrying[slot].value]

    /**
     * The paladin starts enchanted in both hands, and neither of those has a
     * name of its own — so one test covers a thing called by its name and a
     * thing called by its number.
     */
    @Test
    fun `the party's own gear is called what it is`() {
        assertEquals("+1 Short sword", names.of(carried(0, 0), types))
        assertEquals("+1 Shield", names.of(carried(0, 1), types))
        assertEquals("Lock picks", names.of(carried(1, 1), types))
        assertEquals("Spellbook", names.of(carried(3, 1), types))
    }

    /** Until the party know better, a thing is called what it looks like. */
    @Test
    fun `an unidentified thing keeps the name it looks like`() {
        val sword = carried(0, 0)
        val unknown = sword.copy(flags = sword.flags and IDENTIFIED.inv())

        assertEquals(names[sword.nameUnidentified], names.of(unknown, types))
    }

    /**
     * A plain enchanted weapon has no name of its own, only a number, so it
     * is called what it looks like with the number on the front.
     */
    @Test
    fun `an enchanted weapon is named by its bonus`() {
        val sword = carried(0, 0)

        assertEquals("+2 Short sword", names.of(sword.copy(value = 2), types))
        assertEquals("-1 Cursed Short sword", names.of(sword.copy(value = -1), types))
    }

    /** A bonus of nothing is no bonus, so nothing is put on the front. */
    @Test
    fun `an enchanted weapon with no bonus is just itself`() {
        assertEquals("Short sword", names.of(carried(0, 0).copy(value = 0), types))
    }

    /**
     * A magical thing has no name of its own in the file — it is called what
     * it is and then what it does, and what it does is its value.
     */
    @Test
    fun `a potion is named by what is in it`() {
        val potion = dungeonNamed("Potion")

        assertEquals("Potion of Healing", names.of(potion.copy(value = 1), types))
        assertEquals("Potion of Extra Healing", names.of(potion.copy(value = 2), types))
    }

    /**
     * Both kinds of scroll read the one list of spells, the cleric spells
     * following the mage ones — so a cleric scroll's own value is already far
     * enough along it to land among them.
     */
    @Test
    fun `a scroll is named by the spell written on it`() {
        assertEquals(
            "Mage Scroll of fireball",
            names.of(dungeonOfKind(ItemKind.MAGE_SCROLL).copy(value = 12), types),
        )
        assertEquals(
            "Cleric Scroll of cure light wounds",
            names.of(dungeonOfKind(ItemKind.CLERIC_SCROLL).copy(value = 34), types),
        )
    }

    /** The first thing in the dungeon of a given kind, taken as identified. */
    private fun dungeonOfKind(kind: ItemKind): Item = dungeon.items
        .first { types.kindOf(it) == kind }
        .let { it.copy(flags = it.flags or IDENTIFIED) }

    /** Whatever the name table has, an unidentified thing goes by its look. */
    @Test
    fun `an unidentified potion is just a potion`() {
        val potion = dungeonNamed("Potion")
        val unknown = potion.copy(flags = potion.flags and IDENTIFIED.inv(), value = 2)

        assertEquals("Potion", names.of(unknown, types))
    }

    /** The first item in the dungeon that goes by [name] before it is known. */
    private fun dungeonNamed(name: String): Item = dungeon.items
        .first { names[it.nameUnidentified] == name }
        .let { it.copy(flags = it.flags or IDENTIFIED) }

    /** A thing a blow took is reported as the champion's loss, and by name. */
    @Test
    fun `what a blow destroyed says whose it was`() {
        val sword = names.of(dungeonNamed("Long Sword"), types)

        assertEquals("Anselm has lost his Long Sword.", ItemMessages.ruined("Anselm", Sex.MALE, sword))
        assertEquals("Ileria has lost her Long Sword.", ItemMessages.ruined("Ileria", Sex.FEMALE, sword))
    }

    @Test
    fun `what is picked up says so`() {
        assertEquals("Lock picks taken.", ItemMessages.taken(names.of(carried(1, 1), types)))
    }

    private companion object {
        const val IDENTIFIED = 0x40
    }
}
