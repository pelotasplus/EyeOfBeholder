package pl.pelotasplus.eyeofbeholder.data

import kotlinx.coroutines.runBlocking
import org.junit.experimental.categories.Category
import pl.pelotasplus.eyeofbeholder.NeedsGameData
import pl.pelotasplus.eyeofbeholder.data.model.CarrySlot
import pl.pelotasplus.eyeofbeholder.data.model.Champion
import pl.pelotasplus.eyeofbeholder.data.model.ChampionFlags
import pl.pelotasplus.eyeofbeholder.data.model.ClassLevel
import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.GameState
import pl.pelotasplus.eyeofbeholder.data.model.HandRecovering
import pl.pelotasplus.eyeofbeholder.data.model.HandUse
import pl.pelotasplus.eyeofbeholder.data.model.HitPoints
import pl.pelotasplus.eyeofbeholder.data.model.Item
import pl.pelotasplus.eyeofbeholder.data.model.ItemIndex
import pl.pelotasplus.eyeofbeholder.data.model.ItemNames
import pl.pelotasplus.eyeofbeholder.data.model.ItemTypes
import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.PartySlot
import pl.pelotasplus.eyeofbeholder.data.model.PartyState
import pl.pelotasplus.eyeofbeholder.data.model.TrackIndex
import pl.pelotasplus.eyeofbeholder.data.model.WhatTheBlowCameTo
import pl.pelotasplus.eyeofbeholder.data.model.XpPoints
import pl.pelotasplus.eyeofbeholder.data.repository.ItemTypesRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.ItemsRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.ResourceRepositoryImpl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A bow shoots arrows and a sling stones. Both take both hands. An arrow in
 * either hand goes before one off the quiver; a stone comes from wherever it
 * is carried; and with nothing to shoot the slot says so.
 */
@Category(NeedsGameData::class)
class ShootingFromAHandTest {

    private val resources = ResourceRepositoryImpl()

    private val types: ItemTypes = runBlocking {
        ItemTypesRepositoryImpl(resources).loadItemTypes().getOrThrow()
    }

    private val dungeon = runBlocking { ItemsRepositoryImpl(resources).loadItems().getOrThrow() }
    private val names: ItemNames get() = dungeon.names

    private fun named(name: String): Item = dungeon.items.first { names[it.nameUnidentified] == name }

    /** Slot 0 is nothing; after it a bow, a sling, three arrows and a rock. */
    private val table = listOf(
        named("Bow"),
        named("Bow"),
        named("Sling"),
        named("Arrow"),
        named("Arrow"),
        named("Arrow"),
        named("Rock"),
    ).map { it.copy(location = Item.CARRIED, level = Item.CARRIED_LEVEL, next = 0, prev = 0) }

    private val bow = ItemIndex(1)
    private val sling = ItemIndex(2)
    private val firstArrow = ItemIndex(3)
    private val secondArrow = ItemIndex(4)
    private val arrowInHand = ItemIndex(5)
    private val rock = ItemIndex(6)

    private val archer = PartySlot(4)
    private val hand = CarrySlot(0)

    private fun world(vararg holding: Pair<CarrySlot, ItemIndex>, quiver: List<ItemIndex> = emptyList()): GameState {
        val slots = CarrySlot.NOTHING_IN_ANY.toMutableList()
        holding.forEach { (slot, item) -> slots[slot.index] = item }

        var world = GameState(
            party = PartyState(Location(13, 9), Direction.SOUTH),
            champions = List(6) {
                Champion.NOBODY.copy(
                    name = "Anselm",
                    flags = ChampionFlags(1),
                    hitPoints = HitPoints(20, 20),
                    levels = listOf(ClassLevel(1, XpPoints(0))),
                    carrying = slots,
                )
            },
            items = table,
        )

        var head = ItemIndex(ItemIndex.NOTHING)
        quiver.forEach { arrow ->
            val stacked = world.holding(arrow).stacking(head)
            world = stacked.world
            head = stacked.head
        }
        return world.carrying(archer, CarrySlot.QUIVER, head)
    }

    private fun GameState.held(slot: CarrySlot) = championIn(archer)!!.holding(slot)

    @Test
    fun `a bow and a sling are shot from, and want both hands`() {
        listOf("Bow", "Sling").forEach { name ->
            val launcher = named(name)
            assertEquals(HandUse.Shoot, types.whatAHandDoesWith(launcher), name)
            assertEquals(2, types[launcher.type]?.requiredHands, name)
        }
    }

    @Test
    fun `a bow shoots an arrow off the quiver, the way the party face`() {
        val shooting = world(hand to bow, quiver = listOf(firstArrow, secondArrow))
            .shotFromHand(archer, hand, types)

        val flying = shooting.world.inFlight.single()
        assertEquals(Direction.SOUTH, flying.going)
        assertEquals(Location(13, 9), flying.at)
        assertTrue(flying.what == firstArrow || flying.what == secondArrow)
        assertEquals(1, shooting.world.stackedIn(shooting.world.held(CarrySlot.QUIVER)))
        assertEquals(bow, shooting.world.held(hand), "the bow went with it")
        assertEquals(TrackIndex(26), shooting.heard)
    }

    @Test
    fun `an arrow already in a hand is shot before the quiver is touched`() {
        val shooting = world(hand to bow, CarrySlot(1) to arrowInHand, quiver = listOf(firstArrow))
            .shotFromHand(archer, hand, types)

        assertEquals(arrowInHand, shooting.world.inFlight.single().what)
        assertEquals(ItemIndex(ItemIndex.NOTHING), shooting.world.held(CarrySlot(1)))
        assertEquals(1, shooting.world.stackedIn(shooting.world.held(CarrySlot.QUIVER)))
    }

    @Test
    fun `the last arrow empties the quiver`() {
        val shooting = world(hand to bow, quiver = listOf(firstArrow)).shotFromHand(archer, hand, types)

        assertEquals(firstArrow, shooting.world.inFlight.single().what)
        assertEquals(ItemIndex(ItemIndex.NOTHING), shooting.world.held(CarrySlot.QUIVER))
    }

    @Test
    fun `with no arrows the slot says there is no ammunition, for a refusal's wait`() {
        val shooting = world(hand to bow, CarrySlot(2) to rock).shotFromHand(archer, hand, types)

        assertTrue(shooting.world.inFlight.isEmpty())
        assertNull(shooting.heard)
        assertEquals(WhatTheBlowCameTo.NoAmmunition, shooting.world.reportIn(archer, hand))
        assertEquals(
            listOf(HandRecovering.REPORTING.value),
            shooting.world.recovering.map { it.ticksLeft },
        )
    }

    @Test
    fun `a sling shoots a stone from anywhere it is carried, and is heard as a throw`() {
        val shooting = world(hand to sling, CarrySlot(9) to rock).shotFromHand(archer, hand, types)

        assertEquals(rock, shooting.world.inFlight.single().what)
        assertEquals(ItemIndex(ItemIndex.NOTHING), shooting.world.held(CarrySlot(9)))
        assertEquals(TrackIndex(11), shooting.heard)
    }

    @Test
    fun `a sling does not shoot arrows`() {
        val shooting = world(hand to sling, quiver = listOf(firstArrow)).shotFromHand(archer, hand, types)

        assertTrue(shooting.world.inFlight.isEmpty())
        assertEquals(WhatTheBlowCameTo.NoAmmunition, shooting.world.reportIn(archer, hand))
    }

    @Test
    fun `a shot costs the hand a swing's wait and reports nothing`() {
        val shooting = world(hand to bow, quiver = listOf(firstArrow)).shotFromHand(archer, hand, types)

        assertEquals(
            listOf(HandRecovering(archer, hand, HandRecovering.AFTER_A_SWING.value, came = null)),
            shooting.world.recovering,
        )
    }

    @Test
    fun `shooting leaves what the mouse holds alone`() {
        val holding = world(hand to bow, quiver = listOf(firstArrow)).holding(rock)

        assertEquals(rock, holding.shotFromHand(archer, hand, types).world.inHand)
    }
}
