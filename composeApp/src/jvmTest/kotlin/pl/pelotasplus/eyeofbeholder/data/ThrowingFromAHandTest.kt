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
import pl.pelotasplus.eyeofbeholder.data.model.ItemIndex
import pl.pelotasplus.eyeofbeholder.data.model.ItemNames
import pl.pelotasplus.eyeofbeholder.data.model.ItemTypes
import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.PartySlot
import pl.pelotasplus.eyeofbeholder.data.model.PartyState
import pl.pelotasplus.eyeofbeholder.data.model.Projectile
import pl.pelotasplus.eyeofbeholder.data.model.ViewPlace
import pl.pelotasplus.eyeofbeholder.data.model.XpPoints
import pl.pelotasplus.eyeofbeholder.data.repository.ItemTypesRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.ItemsRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.ResourceRepositoryImpl
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A dagger, a dart or a rock used from a hand is thrown rather than swung, and
 * the hand is filled again from the belt — the lowest of its three slots
 * holding anything, whatever it holds, and never from the pack.
 */
@Category(NeedsGameData::class)
class ThrowingFromAHandTest {

    private val resources = ResourceRepositoryImpl()

    private val types: ItemTypes = runBlocking {
        ItemTypesRepositoryImpl(resources).loadItemTypes().getOrThrow()
    }

    private val dungeon = runBlocking { ItemsRepositoryImpl(resources).loadItems().getOrThrow() }
    private val names: ItemNames get() = dungeon.names

    private fun indexOf(name: String) =
        ItemIndex(dungeon.items.indexOfFirst { names[it.nameUnidentified] == name })

    private val dagger = indexOf("Dagger")
    private val rock = indexOf("Rock")
    private val dart = indexOf("Dart")
    private val potion = indexOf("Potion")

    private val thrower = PartySlot(1)
    private val hand = CarrySlot(0)

    private fun world(vararg holding: Pair<CarrySlot, ItemIndex>): GameState {
        val slots = CarrySlot.NOTHING_IN_ANY.toMutableList()
        holding.forEach { (slot, item) -> slots[slot.index] = item }

        return GameState(
            party = PartyState(Location(13, 9), Direction.EAST),
            champions = List(6) {
                Champion.NOBODY.copy(
                    name = "Anselm",
                    flags = ChampionFlags(1),
                    hitPoints = HitPoints(20, 20),
                    levels = listOf(ClassLevel(1, XpPoints(0))),
                    carrying = slots,
                )
            },
            items = dungeon.items,
        )
    }

    private fun GameState.held(slot: CarrySlot) = championIn(thrower)!!.holding(slot)

    @Test
    fun `a dagger, a dart and a rock are thrown and a sword is swung`() {
        listOf("Dagger", "Dart", "Rock", "Spear").forEach { name ->
            val item = dungeon.items[indexOf(name).value]
            assertEquals(HandUse.Throw, types.whatAHandDoesWith(item), name)
        }
        assertEquals(HandUse.Swing, types.whatAHandDoesWith(dungeon.items[indexOf("Long Sword").value]))
    }

    @Test
    fun `the thing goes the way the party face, from the thrower's own quarter`() {
        val after = world(hand to dagger).thrownFromHand(thrower, hand, types)

        val flying = after.inFlight.single()
        assertEquals(dagger, flying.what)
        assertEquals(Location(13, 9), flying.at)
        assertEquals(Direction.EAST, flying.going)
        assertEquals(ViewPlace.FAR_RIGHT.onASquareFacing(Direction.EAST), flying.place)
        assertEquals(Projectile.Thrower.AChampion(thrower), flying.thrownBy)
    }

    @Test
    fun `the hand is empty with nothing on the belt`() {
        val after = world(hand to dagger).thrownFromHand(thrower, hand, types)

        assertEquals(ItemIndex(ItemIndex.NOTHING), after.held(hand))
    }

    @Test
    fun `the hand is filled from the bottom of the belt first`() {
        val after = world(
            hand to dagger,
            CarrySlot(22) to rock,
            CarrySlot(24) to dart,
        ).thrownFromHand(thrower, hand, types)

        assertEquals(dart, after.held(hand))
        assertEquals(ItemIndex(ItemIndex.NOTHING), after.held(CarrySlot(24)))
        assertEquals(rock, after.held(CarrySlot(22)), "the rest of the belt was touched")
    }

    @Test
    fun `whatever is on the belt comes up, thrown weapon or not`() {
        val after = world(hand to dagger, CarrySlot(23) to potion).thrownFromHand(thrower, hand, types)

        assertEquals(potion, after.held(hand))
    }

    @Test
    fun `nothing comes up from the pack`() {
        val after = world(hand to dagger, CarrySlot(2) to rock).thrownFromHand(thrower, hand, types)

        assertEquals(ItemIndex(ItemIndex.NOTHING), after.held(hand))
        assertEquals(rock, after.held(CarrySlot(2)))
    }

    @Test
    fun `the hand waits as long as after a swing, reporting nothing`() {
        val after = world(hand to dagger).thrownFromHand(thrower, hand, types)

        assertEquals(
            listOf(HandRecovering(thrower, hand, HandRecovering.AFTER_A_SWING.value, came = null)),
            after.recovering,
        )
    }

    @Test
    fun `an empty hand throws nothing`() {
        val before = world()

        assertEquals(before, before.thrownFromHand(thrower, hand, types))
    }
}
