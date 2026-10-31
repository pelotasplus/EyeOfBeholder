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
import pl.pelotasplus.eyeofbeholder.data.model.ItemIndex
import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.PartySlot
import pl.pelotasplus.eyeofbeholder.data.model.PartyState
import pl.pelotasplus.eyeofbeholder.data.model.Spell
import pl.pelotasplus.eyeofbeholder.data.repository.ItemsRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.ResourceRepositoryImpl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Improved identify: whatever the caster holds, known for what it is.
 *
 * The hands and nothing else. It asks nobody — it is the caster's own hands —
 * and it lasts no time at all.
 */
@Category(NeedsGameData::class)
class WhatAnImprovedIdentifyNamesTest {

    private val loaded = runBlocking {
        ItemsRepositoryImpl(ResourceRepositoryImpl()).loadItems().getOrThrow()
    }

    /** Things lying in the dungeon that nobody knows yet. */
    private val unknown: List<ItemIndex> =
        loaded.items.withIndex()
            .filter { (index, it) -> index > 0 && it.exists && !it.identified }
            .map { ItemIndex(it.index) }

    private val cursedAndUnknown: ItemIndex =
        loaded.items.withIndex()
            .first { (_, it) -> it.exists && it.stuckToItsSlot && !it.identified }
            .let { ItemIndex(it.index) }

    private fun champion(vararg holding: Pair<CarrySlot, ItemIndex>) = Champion.NOBODY.copy(
        name = "Anselm",
        flags = ChampionFlags(IN_THE_PARTY),
        hitPoints = HitPoints(40, 40),
        carrying = List(CarrySlot.ALL_OF_THEM) { slot ->
            holding.firstOrNull { it.first.index == slot }?.second ?: ItemIndex(ItemIndex.NOTHING)
        },
    )

    private fun world(vararg party: Champion) = GameState(
        party = PartyState(Location(13, 9), Direction.NORTH),
        champions = party.toList(),
        items = loaded.items,
    )

    @Test
    fun `it is the caster's own, asks nobody and lasts no time`() {
        assertNull(Spell.IMPROVED_IDENTIFY.laidOn)
        assertNull(Spell.IMPROVED_IDENTIFY.lasts)
        assertTrue(Spell.IMPROVED_IDENTIFY.namesWhatIsHeld)
    }

    @Test
    fun `both hands are named`() {
        val (left, right) = unknown
        val world = world(champion(CarrySlot(0) to left, CarrySlot(1) to right))

        val after = world.heldThingsNamed(PartySlot(0))

        assertTrue(after.item(left)!!.identified, "the first hand")
        assertTrue(after.item(right)!!.identified, "the second hand")
    }

    /** The pack is not touched: a thing wanted named is taken out and held. */
    @Test
    fun `nothing in the pack is named`() {
        val (held, packed) = unknown
        val world = world(champion(CarrySlot(0) to held, CarrySlot(IN_THE_PACK) to packed))

        val after = world.heldThingsNamed(PartySlot(0))

        assertTrue(after.item(held)!!.identified)
        assertFalse(after.item(packed)!!.identified, "the pack was looked into")
    }

    /** Only the caster's hands, not the champion beside them. */
    @Test
    fun `only the caster's hands`() {
        val (mine, theirs) = unknown
        val world = world(
            champion(CarrySlot(0) to mine),
            champion(CarrySlot(0) to theirs),
        )

        val after = world.heldThingsNamed(PartySlot(0))

        assertTrue(after.item(mine)!!.identified)
        assertFalse(after.item(theirs)!!.identified)
    }

    /**
     * Knowing a sword is cursed does not free the hand it is stuck to. That
     * is what a remove curse is for; this only says what the trouble is.
     */
    @Test
    fun `naming a cursed thing leaves it cursed`() {
        val world = world(champion(CarrySlot(0) to cursedAndUnknown))

        val after = world.heldThingsNamed(PartySlot(0))

        assertTrue(after.item(cursedAndUnknown)!!.identified)
        assertTrue(after.item(cursedAndUnknown)!!.stuckToItsSlot, "naming it freed it")
    }

    /**
     * The spells that spark over the caster's own box. Data — read off the
     * spell table, not reasoned out — so a test names them.
     *
     * A spell that asks which champion sparks too, over the one it was
     * pointed at rather than over whoever read it, and is not among these:
     * the table marks the two cases apart, and the asking is what carries
     * the second.
     */
    @Test
    fun `the spells that spark over the caster are the ones the table marks`() {
        assertEquals(
            setOf(
                Spell.SHIELD,
                Spell.SHOCKING_GRASP,
                Spell.BLUR,
                Spell.IMPROVED_IDENTIFY,
                Spell.MAGICAL_VESTMENT,
            ),
            Spell.entries.filter { it.sparksOverTheCaster }.toSet(),
        )
    }

    private companion object {
        const val IN_THE_PARTY = 1

        /** A slot that is neither hand nor finger. */
        const val IN_THE_PACK = 10
    }
}
