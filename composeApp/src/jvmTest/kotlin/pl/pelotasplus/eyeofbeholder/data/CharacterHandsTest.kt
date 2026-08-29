package pl.pelotasplus.eyeofbeholder.data

import kotlinx.coroutines.runBlocking
import org.junit.experimental.categories.Category
import pl.pelotasplus.eyeofbeholder.NeedsGameData
import pl.pelotasplus.eyeofbeholder.data.model.Champion
import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.GameState
import pl.pelotasplus.eyeofbeholder.data.model.Item
import pl.pelotasplus.eyeofbeholder.data.model.ItemIconId
import pl.pelotasplus.eyeofbeholder.data.model.ItemIndex
import pl.pelotasplus.eyeofbeholder.data.model.ItemNameId
import pl.pelotasplus.eyeofbeholder.data.model.ItemProperties
import pl.pelotasplus.eyeofbeholder.data.model.ItemType
import pl.pelotasplus.eyeofbeholder.data.model.ItemTypeId
import pl.pelotasplus.eyeofbeholder.data.model.ItemTypes
import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.PartyState
import pl.pelotasplus.eyeofbeholder.data.model.SquarePlace
import pl.pelotasplus.eyeofbeholder.data.repository.ItemTypesRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.OriginalSaveRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.ResourceRepositoryImpl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Which of a champion's hands are any use, which is what decides whether the
 * panel draws a grid over one.
 *
 * The class rules are asserted against the shipped item types rather than
 * against numbers written down here: what a shield or a spellbook is for is
 * the game's to say.
 */
@Category(NeedsGameData::class)
class CharacterHandsTest {

    private val resources = ResourceRepositoryImpl()

    private val types = runBlocking {
        ItemTypesRepositoryImpl(resources).loadItemTypes().getOrThrow()
    }

    private val save = runBlocking {
        OriginalSaveRepositoryImpl(resources)
            .loadOriginalSave(OriginalSaveRepositoryImpl.QUICK_START).getOrThrow()
    }

    private val world = GameState(
        party = PartyState(Location(0, 0), Direction.NORTH),
        items = save.items,
    )

    private val held = { slot: ItemIndex -> world.item(slot) }

    /** The party the game ships with are equipped for who they are. */
    @Test
    fun `every champion can use what the game gave them`() {
        save.party.filter { it.inTheParty }.forEach { champion ->
            repeat(Champion.HANDS) { hand ->
                assertTrue(
                    types.canStrikeWith(champion, hand, held),
                    "${champion.name} cannot use what is in hand $hand",
                )
            }
        }
    }

    private fun handing(slot: Int, hand: Int, item: ItemIndex): Champion =
        save.party[slot].let { champion ->
            champion.copy(
                carrying = champion.carrying.toMutableList().also { it[hand] = item },
            )
        }

    /** The mage's own spellbook, and the paladin's shield. */
    private val spellbook = save.party[3].carrying[1]
    private val shield = save.party[0].carrying[1]

    @Test
    fun `a spellbook is no use to a paladin`() {
        assertFalse(types.canStrikeWith(handing(slot = 0, hand = 1, spellbook), 1, held))
    }

    @Test
    fun `a spellbook is exactly what a mage wants`() {
        assertTrue(types.canStrikeWith(handing(slot = 3, hand = 1, spellbook), 1, held))
    }

    /**
     * The other half of the same rule, and the one that catches a party out:
     * a shield is for everybody but a mage.
     */
    @Test
    fun `a shield is no use to a mage`() {
        assertFalse(types.canStrikeWith(handing(slot = 3, hand = 0, shield), 0, held))
        assertTrue(types.canStrikeWith(handing(slot = 0, hand = 0, shield), 0, held))
    }

    /**
     * A weapon that wants both hands leaves the second with nothing to hold
     * with, whatever is in it and whoever is holding it.
     */
    @Test
    fun `a two-handed weapon in the first hand puts the second out of use`() {
        val anyone = save.party[0].copy(carrying = listOf(ItemIndex(1), ItemIndex(2)))

        val twoHanded = ItemTypes(
            listOf(
                anyClassMayHold(requiredHands = 2),
                anyClassMayHold(requiredHands = 1),
            )
        )

        val inHand = { slot: ItemIndex ->
            when (slot.value) {
                1 -> itemOfType(0)
                2 -> itemOfType(1)
                else -> null
            }
        }

        assertTrue(twoHanded.canStrikeWith(anyone, 0, inHand))
        assertFalse(twoHanded.canStrikeWith(anyone, 1, inHand))
    }

    /** Nothing in a hand is something anybody may do. */
    @Test
    fun `an empty hand is always usable`() {
        val emptyHanded = save.party[3].copy(
            carrying = List(save.party[3].carrying.size) { ItemIndex(ItemIndex.NOTHING) },
        )

        assertEquals(
            listOf(true, true),
            (0 until Champion.HANDS).map { types.canStrikeWith(emptyHanded, it, held) },
        )
    }

    private fun anyClassMayHold(requiredHands: Int) = ItemType(
        invFlags = 0,
        handFlags = 0,
        armorClass = 0,
        allowedClasses = ALL_CLASSES,
        requiredHands = requiredHands,
        dmgNumDiceS = 0,
        dmgNumPipsS = 0,
        dmgIncS = 0,
        dmgNumDiceL = 0,
        dmgNumPipsL = 0,
        dmgIncL = 0,
        unk1 = 0,
        extraProperties = ItemProperties(0),
    )

    private fun itemOfType(type: Int) = Item(
        nameUnidentified = ItemNameId(0),
        nameIdentified = ItemNameId(0),
        flags = 0,
        icon = ItemIconId(0),
        type = ItemTypeId(type),
        place = SquarePlace.NORTH_WEST,
        location = Location(0, 0),
        next = 0,
        prev = 0,
        level = 0,
        value = 0,
    )

    private companion object {
        /** All six class bits set, so only the handedness rules can bite. */
        const val ALL_CLASSES = 0x3F
    }
}
