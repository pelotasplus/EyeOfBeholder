package pl.pelotasplus.eyeofbeholder.data

import kotlinx.coroutines.runBlocking
import org.junit.experimental.categories.Category
import pl.pelotasplus.eyeofbeholder.NeedsGameData
import pl.pelotasplus.eyeofbeholder.data.model.CarrySlot
import pl.pelotasplus.eyeofbeholder.data.model.Champion
import pl.pelotasplus.eyeofbeholder.data.model.ChampionFlags
import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.GameState
import pl.pelotasplus.eyeofbeholder.data.model.HitPoints
import pl.pelotasplus.eyeofbeholder.data.model.Item
import pl.pelotasplus.eyeofbeholder.data.model.ItemIndex
import pl.pelotasplus.eyeofbeholder.data.model.ItemTypes
import pl.pelotasplus.eyeofbeholder.data.model.LaidOnAChampion
import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.PartySlot
import pl.pelotasplus.eyeofbeholder.data.model.PartyState
import pl.pelotasplus.eyeofbeholder.data.model.Spell
import pl.pelotasplus.eyeofbeholder.data.repository.ItemTypesRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.ItemsRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.ResourceRepositoryImpl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Remove curse, on the cursed weapons the dungeon really has.
 *
 * A curse is a mark on the thing, and it does one thing: whatever carries it
 * cannot be taken out of a hand. The spell takes the mark off everything one
 * champion carries and tells the party what each freed thing is — and that is
 * all. It does not mend: a −3 sword is let go of, named, and still −3.
 */
@Category(NeedsGameData::class)
class WhatARemoveCurseFreesTest {

    private val resources = ResourceRepositoryImpl()

    private val loaded = runBlocking { ItemsRepositoryImpl(resources).loadItems().getOrThrow() }

    private val types: ItemTypes = runBlocking {
        ItemTypesRepositoryImpl(resources).loadItemTypes().getOrThrow()
    }

    /** Cursed weapons the party can find, nobody yet knowing what they are. */
    private val cursedWeapons: List<ItemIndex> =
        loaded.items.withIndex()
            .filter { (_, it) -> it.exists && it.stuckToItsSlot && it.value < 0 && !it.identified }
            .map { ItemIndex(it.index) }

    private val aCursedSword get() = cursedWeapons[0]
    private val anotherCursedThing get() = cursedWeapons[1]

    private fun champion(vararg holding: Pair<CarrySlot, ItemIndex>) = Champion.NOBODY.copy(
        name = "Anselm",
        flags = ChampionFlags(IN_THE_PARTY),
        hitPoints = HitPoints(40, 40),
        carrying = List(CarrySlot.ALL_OF_THEM) { slot ->
            holding.firstOrNull { it.first.index == slot }?.second ?: ItemIndex(ItemIndex.NOTHING)
        },
    )

    private fun world(vararg party: Champion, items: List<Item> = loaded.items) = GameState(
        party = PartyState(Location(13, 9), Direction.NORTH),
        champions = party.toList(),
        items = items,
    )

    private fun GameState.lifted(whom: Int = 0) =
        withTheSpellLaidOn(PartySlot(whom), LaidOnAChampion.Uncurses, byWhom = champions[whom])

    @Test
    fun `the dungeon has cursed weapons to find`() {
        assertTrue(cursedWeapons.size >= 2, "found ${cursedWeapons.size}")
    }

    /** Asks which champion, the way a cure does, and has no clock. */
    @Test
    fun `it is laid on whoever is pointed at`() {
        assertEquals(LaidOnAChampion.Uncurses, Spell.REMOVE_CURSE.laidOn)
        assertNull(Spell.REMOVE_CURSE.lasts)
    }

    @Test
    fun `a cursed sword in the hand is freed`() {
        val world = world(champion(CarrySlot(0) to aCursedSword))

        val after = world.lifted()

        assertFalse(
            after.item(aCursedSword)!!.stuckToItsSlot,
            "the sword is still stuck to the hand",
        )
    }

    /**
     * And known for what it is. The party learn what the thing they could not
     * put down was — which is the moment a cursed sword is found out.
     */
    @Test
    fun `a freed thing is known for what it is`() {
        val world = world(champion(CarrySlot(0) to aCursedSword))
        val sword = world.item(aCursedSword)!!

        val after = world.lifted()

        assertFalse(sword.identified, "it should have been unknown to begin with")
        assertTrue(after.item(aCursedSword)!!.identified)
        assertEquals(
            loaded.names.of(sword, types, evenIfUnknown = true),
            loaded.names.of(after.item(aCursedSword)!!, types),
            "the party should now call it by its true name",
        )
    }

    /** Freed, named, and no better. What was wrong with it is still wrong. */
    @Test
    fun `it does not mend what it frees`() {
        val world = world(champion(CarrySlot(0) to aCursedSword))
        val before = world.item(aCursedSword)!!.value

        assertTrue(before < 0, "the sword should be a poor one to begin with")
        assertEquals(before, world.lifted().item(aCursedSword)!!.value)
    }

    /**
     * Everything carried, not only the hands. A cursed thing in the pack does
     * nothing until it is put in a hand, and then sticks — so a spell that
     * freed only the hands would leave that one waiting.
     */
    @Test
    fun `what is in the pack is freed as well`() {
        val world = world(
            champion(CarrySlot(0) to aCursedSword, CarrySlot(IN_THE_PACK) to anotherCursedThing),
        )

        val after = world.lifted()

        assertFalse(after.item(aCursedSword)!!.stuckToItsSlot)
        assertFalse(after.item(anotherCursedThing)!!.stuckToItsSlot, "the pack was passed over")
    }

    /** Only whoever was pointed at. The champion beside them keeps theirs. */
    @Test
    fun `it reaches only the champion it is laid on`() {
        val world = world(
            champion(CarrySlot(0) to aCursedSword),
            champion(CarrySlot(0) to anotherCursedThing),
        )

        val after = world.lifted(whom = 0)

        assertFalse(after.item(aCursedSword)!!.stuckToItsSlot)
        assertTrue(after.item(anotherCursedThing)!!.stuckToItsSlot, "the wrong champion was freed")
    }

    // --- asking first ----------------------------------------------------------

    /** Somebody with nothing cursed is told so, and the scroll is kept. */
    @Test
    fun `it would do nothing for somebody carrying nothing cursed`() {
        val bare = champion()

        assertFalse(LaidOnAChampion.Uncurses.wouldHelp(bare, world(bare)))
    }

    @Test
    fun `it is wanted by anybody carrying something cursed, wherever it is`() {
        val inHand = champion(CarrySlot(0) to aCursedSword)
        val inPack = champion(CarrySlot(IN_THE_PACK) to aCursedSword)

        assertTrue(LaidOnAChampion.Uncurses.wouldHelp(inHand, world(inHand)))
        assertTrue(LaidOnAChampion.Uncurses.wouldHelp(inPack, world(inPack)))
    }

    private companion object {
        const val IN_THE_PARTY = 1

        /** A slot that is neither hand nor finger. */
        const val IN_THE_PACK = 10
    }
}
