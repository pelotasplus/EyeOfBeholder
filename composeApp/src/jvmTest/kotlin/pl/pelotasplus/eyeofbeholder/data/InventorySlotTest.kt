package pl.pelotasplus.eyeofbeholder.data

import kotlinx.coroutines.runBlocking
import pl.pelotasplus.eyeofbeholder.data.model.Champion
import pl.pelotasplus.eyeofbeholder.data.model.InventorySlot
import pl.pelotasplus.eyeofbeholder.data.model.Item
import pl.pelotasplus.eyeofbeholder.data.model.ItemTypes
import pl.pelotasplus.eyeofbeholder.data.model.inventorySlotAt
import pl.pelotasplus.eyeofbeholder.data.model.inventorySlotPositions
import pl.pelotasplus.eyeofbeholder.data.repository.ItemTypesRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.ItemsRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.OriginalSaveRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.ResourceRepositoryImpl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Which of a champion's slots will take what is being held. */
class InventorySlotTest {

    private val resources = ResourceRepositoryImpl()

    private val types: ItemTypes = runBlocking {
        ItemTypesRepositoryImpl(resources).loadItemTypes().getOrThrow()
    }

    private val dungeon = runBlocking {
        ItemsRepositoryImpl(resources).loadItems().getOrThrow()
    }

    private val save = runBlocking {
        OriginalSaveRepositoryImpl(resources)
            .loadOriginalSave(OriginalSaveRepositoryImpl.QUICK_START).getOrThrow()
    }

    private val paladin = save.party[0]

    private fun slot(at: Int) = inventorySlotPositions[at]

    /** Slots 2 to 15, the two columns down the left of the page. */
    private val pack = (2..15).map(::slot)

    /**
     * The point of a pack: everything the party carry goes in it, whatever it
     * is and whoever is carrying it.
     */
    @Test
    fun `the pack takes anything anyone is carrying`() {
        val carried = save.party.filter { it.inTheParty }
            .flatMap { it.carrying }
            .mapNotNull { save.items.getOrNull(it.value) }
            .filter { it.exists || it.location == Item.NOWHERE }

        carried.forEach { item ->
            pack.forEach { pocket ->
                assertTrue(
                    types.willSwap(paladin, pocket, held = item, inSlot = null),
                    "a pocket refused ${item.type}",
                )
            }
        }
    }

    /** An empty hand may be put anywhere: it is taking, not putting. */
    @Test
    fun `every slot takes an empty hand`() {
        inventorySlotPositions.filterNot { it.isQuiver }.forEach { slot ->
            assertTrue(
                types.willSwap(paladin, slot, held = null, inSlot = null),
                "slot ${slot.slot} refused an empty hand",
            )
        }
    }

    /**
     * A worn slot takes one kind of thing. The party's own armour is what the
     * armour slot is for, and a shield is not.
     */
    @Test
    fun `armour goes where armour goes and a shield does not`() {
        val armour = save.items[paladin.carrying[WORN_ARMOUR].value]
        val shield = save.items[paladin.carrying[1].value]

        assertTrue(types.willSwap(paladin, slot(WORN_ARMOUR), armour, inSlot = null))
        assertFalse(types.willSwap(paladin, slot(WORN_ARMOUR), shield, inSlot = null))
    }

    /** The mage's own armour is no use to the mage, so she may not put it on. */
    @Test
    fun `armour a champion may not wear does not go on`() {
        val mage = save.party[3]
        val plate = save.items[paladin.carrying[WORN_ARMOUR].value]

        assertTrue(types.willSwap(paladin, slot(WORN_ARMOUR), plate, inSlot = null))
        assertFalse(types.willSwap(mage, slot(WORN_ARMOUR), plate, inSlot = null))
    }

    /**
     * The quiver is not a swap but a stack, so the ordinary rule refuses it
     * and it is worked its own way.
     */
    @Test
    fun `the quiver is no ordinary slot`() {
        assertTrue(slot(InventorySlot.QUIVER).isQuiver)
        assertFalse(types.willSwap(paladin, slot(InventorySlot.QUIVER), null, null))
    }

    /** An arrow is the one thing a quiver holds, and it holds nothing else. */
    @Test
    fun `an arrow goes in a quiver and a dagger does not`() {
        val quiver = slot(InventorySlot.QUIVER)
        val arrow = dungeonNamed("Arrow")
        val dagger = dungeonNamed("Dagger")

        assertTrue(types.mayGoIn(quiver.takes, arrow))
        assertFalse(types.mayGoIn(quiver.takes, dagger))
    }

    /** And an arrow goes nowhere else that is worn. */
    @Test
    fun `an arrow does not go in a worn slot`() {
        assertFalse(types.mayGoIn(slot(WORN_ARMOUR).takes, dungeonNamed("Arrow")))
    }

    private fun dungeonNamed(name: String): Item =
        dungeon.items.first { dungeon.names[it.nameUnidentified] == name }

    /** Only a hand holds a thing a curse will not let go of. */
    @Test
    fun `a cursed thing cannot be taken out of a hand but can out of a pocket`() {
        val cursed = save.items[paladin.carrying[0].value].copy(flags = CURSED)

        assertFalse(types.willSwap(paladin, slot(0), held = null, inSlot = cursed))
        assertTrue(types.willSwap(paladin, pack.first(), held = null, inSlot = cursed))
    }

    // --- where the boxes are ------------------------------------------------

    @Test
    fun `a click finds the pocket it landed in`() {
        assertEquals(2, inventorySlotAt(185, 45)?.slot)
        assertEquals(3, inventorySlotAt(203, 45)?.slot)
    }

    /** The last two boxes are smaller, so the gap between them is nobody's. */
    @Test
    fun `the small boxes at the bottom are only ten across`() {
        assertEquals(25, inventorySlotAt(230, 140)?.slot)
        assertNull(inventorySlotAt(238, 140)?.slot)
        assertEquals(26, inventorySlotAt(242, 140)?.slot)
    }

    @Test
    fun `a click on the figure lands on no slot at all`() {
        assertNull(inventorySlotAt(260, 80))
    }

    private companion object {
        const val WORN_ARMOUR = 17

        /** The flag that sticks a thing to the slot it is in. */
        const val CURSED = 0x20
    }
}
