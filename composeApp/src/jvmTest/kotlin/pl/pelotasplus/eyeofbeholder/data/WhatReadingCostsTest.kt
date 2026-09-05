package pl.pelotasplus.eyeofbeholder.data

import kotlinx.coroutines.runBlocking
import org.junit.experimental.categories.Category
import pl.pelotasplus.eyeofbeholder.NeedsGameData
import pl.pelotasplus.eyeofbeholder.data.model.CarrySlot
import pl.pelotasplus.eyeofbeholder.data.model.Champion
import pl.pelotasplus.eyeofbeholder.data.model.ChampionFlags
import pl.pelotasplus.eyeofbeholder.data.model.CountedBy
import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.GameState
import pl.pelotasplus.eyeofbeholder.data.model.Item
import pl.pelotasplus.eyeofbeholder.data.model.ItemIndex
import pl.pelotasplus.eyeofbeholder.data.model.ItemKind
import pl.pelotasplus.eyeofbeholder.data.model.ItemTypes
import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.PartySlot
import pl.pelotasplus.eyeofbeholder.data.model.PartyState
import pl.pelotasplus.eyeofbeholder.data.model.ThrownSpell
import pl.pelotasplus.eyeofbeholder.data.model.Wand
import pl.pelotasplus.eyeofbeholder.data.model.WhatCastingCosts
import pl.pelotasplus.eyeofbeholder.data.repository.ItemTypesRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.ItemsRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.ResourceRepositoryImpl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What reading a spell out of something costs the thing it was read from.
 *
 * A scroll goes up with the words. A wand carries a number of uses written on
 * the item rather than on its kind, comes down by one, and goes when the last
 * of them does. One wand is never spent at all.
 *
 * None of it depends on who read it, and neither does the spell: anything cast
 * this way is cast as a ninth-level caster would cast it.
 */
@Category(NeedsGameData::class)
class WhatReadingCostsTest {

    private val resources = ResourceRepositoryImpl()

    private val itemTypes: ItemTypes = runBlocking {
        ItemTypesRepositoryImpl(resources).loadItemTypes().getOrThrow()
    }

    private val dungeon = runBlocking {
        ItemsRepositoryImpl(resources).loadItems().getOrThrow()
    }

    // --- what each kind of thing costs ---------------------------------------

    @Test
    fun `a scroll is read once and gone`() {
        val scroll = dungeon.items.first { itemTypes.kindOf(it) == ItemKind.MAGE_SCROLL }

        assertEquals(WhatCastingCosts.AllOfIt, itemTypes.whatCastingCosts(scroll))
    }

    @Test
    fun `a wand comes down by one use`() {
        val wand = dungeon.items.first {
            itemTypes.kindOf(it) == ItemKind.WAND && Wand.of(it.value) == Wand.OF_MISSILES
        }

        assertEquals(WhatCastingCosts.OneCharge, itemTypes.whatCastingCosts(wand))
    }

    /** Except the one that defends, which answers for ever. */
    @Test
    fun `the wand that defends is never spent`() {
        val wand = dungeon.items.first {
            itemTypes.kindOf(it) == ItemKind.WAND && Wand.of(it.value) == Wand.OF_DEFENCE
        }

        assertEquals(WhatCastingCosts.Nothing, itemTypes.whatCastingCosts(wand))
    }

    /** And a sword is not read from at all, so reading it costs nothing. */
    @Test
    fun `something that is not read from costs nothing`() {
        val sword = dungeon.items.first { itemTypes.kindOf(it) == ItemKind.SWUNG_BY_HAND }

        assertEquals(WhatCastingCosts.Nothing, itemTypes.whatCastingCosts(sword))
    }

    // --- and what that does to the hand holding it ---------------------------

    @Test
    fun `reading a scroll empties the hand`() {
        val after = holding(aScroll()).castOutOf(WHOSE, HAND, itemTypes)

        assertEquals(ItemIndex.NOTHING, after.champions[0].holding(HAND).value)
    }

    @Test
    fun `spending a wand leaves it in the hand with one use fewer`() {
        val wand = aWand(charges = 3)
        val after = holding(wand).castOutOf(WHOSE, HAND, itemTypes)

        val still = after.item(after.champions[0].holding(HAND))
        assertTrue(still != null, "the wand went before it was empty")
        assertEquals(2, still.chargesLeft)
    }

    /** And the last use takes the wand with it, rather than leaving a dead one. */
    @Test
    fun `the last use of a wand takes the wand`() {
        val after = holding(aWand(charges = 1)).castOutOf(WHOSE, HAND, itemTypes)

        assertEquals(ItemIndex.NOTHING, after.champions[0].holding(HAND).value)
    }

    @Test
    fun `a wand spent down to nothing goes on the reading that empties it`() {
        var world = holding(aWand(charges = 2))

        world = world.castOutOf(WHOSE, HAND, itemTypes)
        assertEquals(1, world.item(world.champions[0].holding(HAND))?.chargesLeft)

        world = world.castOutOf(WHOSE, HAND, itemTypes)
        assertEquals(ItemIndex.NOTHING, world.champions[0].holding(HAND).value)
    }

    // --- and how practised the reading is ------------------------------------

    /**
     * Nine, whoever is holding it. A first-level fighter reading a scroll of
     * magic missile throws the four a ninth-level mage throws.
     */
    @Test
    fun `anything read from is read as a ninth-level caster`() {
        assertEquals(9, ThrownSpell.AS_READ_FROM_A_SCROLL)
        assertEquals(
            4,
            CountedBy.EVERY_SECOND_LEVEL.forACasterOf(ThrownSpell.AS_READ_FROM_A_SCROLL),
        )
    }

    // --- the fixture ---------------------------------------------------------

    private fun aScroll() = dungeon.items.first { itemTypes.kindOf(it) == ItemKind.MAGE_SCROLL }

    private fun aWand(charges: Int) = dungeon.items
        .first { itemTypes.kindOf(it) == ItemKind.WAND && Wand.of(it.value) == Wand.OF_MISSILES }
        .let { it.copy(flags = charges) }

    /** A world with [what] in the first champion's first hand. */
    private fun holding(what: Item): GameState {
        val world = GameState(
            party = PartyState(Location(1, 1), Direction.NORTH),
            champions = listOf(Champion.NOBODY.copy(flags = ChampionFlags(1))),
            // Slot zero of the table is what an empty hand points at, so
            // nothing real may live there.
            items = listOf(what, what),
        )

        return world.carrying(WHOSE, HAND, ItemIndex(1))
    }

    private companion object {
        val WHOSE = PartySlot(0)
        val HAND = CarrySlot(0)
    }
}
