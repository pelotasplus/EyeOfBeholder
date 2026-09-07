package pl.pelotasplus.eyeofbeholder.data

import kotlinx.coroutines.runBlocking
import org.junit.experimental.categories.Category
import pl.pelotasplus.eyeofbeholder.NeedsGameData
import pl.pelotasplus.eyeofbeholder.data.model.Abilities
import pl.pelotasplus.eyeofbeholder.data.model.Ability
import pl.pelotasplus.eyeofbeholder.data.model.ArmorClass
import pl.pelotasplus.eyeofbeholder.data.model.CarrySlot
import pl.pelotasplus.eyeofbeholder.data.model.Champion
import pl.pelotasplus.eyeofbeholder.data.model.ChampionFlags
import pl.pelotasplus.eyeofbeholder.data.model.CharacterClass
import pl.pelotasplus.eyeofbeholder.data.model.ClassLevel
import pl.pelotasplus.eyeofbeholder.data.model.DialogAnswer
import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.Food
import pl.pelotasplus.eyeofbeholder.data.model.GameState
import pl.pelotasplus.eyeofbeholder.data.model.HitPoints
import pl.pelotasplus.eyeofbeholder.data.model.ItemIndex
import pl.pelotasplus.eyeofbeholder.data.model.ItemTypeId
import pl.pelotasplus.eyeofbeholder.data.model.LevelScriptRunner
import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.PartyState
import pl.pelotasplus.eyeofbeholder.data.model.PortraitId
import pl.pelotasplus.eyeofbeholder.data.model.ScriptEvent
import pl.pelotasplus.eyeofbeholder.data.model.ScriptStage
import pl.pelotasplus.eyeofbeholder.data.model.WallSide
import pl.pelotasplus.eyeofbeholder.data.model.XpPoints
import pl.pelotasplus.eyeofbeholder.data.repository.CpsRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.DecRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.InfRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.ItemsRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.MazRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.PalRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.ResourceRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.VcnRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.VmpRepositoryImpl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The fifteenth floor's tree giant at 21x12, which searches the party.
 *
 * Let it touch you and it goes through everybody's pockets for the two coins
 * picked up earlier on the floor, takes them, and leaves one of its own in
 * their place — the one the twentieth room wants. The alcove is then walled
 * off, so there is no second search.
 *
 * What it looks for is not in the script. The instruction says only that a
 * search happens; which two things it takes is written into the engine, and
 * this is the one place in the game that runs it.
 */
@Category(NeedsGameData::class)
class TheGiantThatSearchesThePartyTest {

    private val resources = ResourceRepositoryImpl()

    private val level = runBlocking {
        InfRepositoryImpl(
            resourceRepository = resources,
            mazRepository = MazRepositoryImpl(resources),
            vmpRepository = VmpRepositoryImpl(resources),
            vcnRepository = VcnRepositoryImpl(resources),
            palRepository = PalRepositoryImpl(resources),
            cpsRepository = CpsRepositoryImpl(resources),
            decRepository = DecRepositoryImpl(resources),
        ).loadInf("LEVEL15.INF").getOrThrow()
    }

    private val dungeon = runBlocking {
        ItemsRepositoryImpl(resources).loadItems().getOrThrow()
    }

    /** The dungeon's own coin of that worth, which is what the giant asks for. */
    private fun theCoinWorth(worth: Int): ItemIndex {
        val at = dungeon.items.indexOfFirst { it.type == COINS && it.value == worth }
        assertTrue(at > 0, "the dungeon holds no coin of kind $COINS worth $worth")
        return ItemIndex(at)
    }

    private val one = theCoinWorth(5)
    private val other = theCoinWorth(6)

    private fun champion(carrying: Map<CarrySlot, ItemIndex>): Champion {
        val slots = CarrySlot.NOTHING_IN_ANY.toMutableList()
        carrying.forEach { (slot, item) -> slots[slot.index] = item }

        return Champion(
            name = "Anselm",
            portrait = PortraitId(0),
            abilities = Abilities(strength = Ability(10, 10), dexterity = Ability(10, 10)),
            hitPoints = HitPoints(40, 40),
            armorClass = ArmorClass(10),
            food = Food(100),
            characterClass = CharacterClass.FIGHTER,
            levels = listOf(ClassLevel(1, XpPoints(0))),
            carrying = slots,
            flags = ChampionFlags(1),
        )
    }

    /**
     * The party standing on the giant's square, one coin in a pack and the
     * other worn by somebody else — the two are found on the floor a long way
     * apart, so nothing says one champion carries both.
     */
    private fun world(
        firstHas: Map<CarrySlot, ItemIndex> = mapOf(DEEP_IN_THE_PACK to one),
        secondHas: Map<CarrySlot, ItemIndex> = mapOf(CarrySlot(0) to other),
        inHand: ItemIndex = ItemIndex(ItemIndex.NOTHING),
    ) = GameState(
        party = PartyState(THE_ALCOVE, Direction.WEST),
        champions = listOf(champion(firstHas), champion(secondHas)) +
            List(4) { champion(emptyMap()) },
        items = dungeon.items,
        inHand = inHand,
    ).arrivingAt(level = LEVEL, places = emptyList(), maz = level.subLevels[0].maz)

    /** Two is the answer that lets it touch them; anything else sends it away. */
    private fun GameState.searched(answering: Int = LET_IT_TOUCH_YOU) = runBlocking {
        LevelScriptRunner(level.script, level = LEVEL).onEvent(
            triggers = level.triggers,
            event = ScriptEvent.PARTY_ENTERED,
            state = this@searched,
            stage = ScriptStage.silent(DialogAnswer(answering)),
            at = THE_ALCOVE,
        ).state
    }

    private fun GameState.stillCarries(worth: Int) = championsCarrying(COINS, worth)

    @Test
    fun `it takes both coins, wherever on the party they are`() {
        val before = world()
        assertEquals(1, before.stillCarries(5), "the fixture starts without one coin")
        assertEquals(1, before.stillCarries(6), "the fixture starts without the other")

        val after = before.searched()

        assertEquals(0, after.stillCarries(5), "it left one coin behind")
        assertEquals(0, after.stillCarries(6), "it left the other behind")
    }

    /**
     * And what it took is gone from the world, not dropped at their feet.
     *
     * Asked of the world rather than of the two slots the coins came from:
     * a thing taken leaves its slot free, and the coin the giant hands over is
     * made in the first free slot there is — which is one of those two. So the
     * question is whether any coin of those worths is left anywhere, and the
     * answer has to be none.
     */
    @Test
    fun `what it takes is gone from the world rather than dropped`() {
        val after = world().searched()

        assertEquals(
            emptyList(),
            after.items.filter { it.exists && it.type == COINS && it.value in WHAT_IT_TAKES },
            "a coin it took is still lying somewhere",
        )
    }

    /** Including one being carried under the pointer, which is nobody's slot. */
    @Test
    fun `a coin held under the pointer goes too`() {
        val after = world(
            firstHas = emptyMap(),
            inHand = one,
        ).searched()

        assertEquals(ItemIndex.NOTHING, after.inHand.value, "the hand kept it")
    }

    /** It leaves one of its own where it stood. */
    @Test
    fun `and leaves its own coin on the square`() {
        val after = world().searched()

        assertTrue(
            after.items.any { it.exists && it.level == LEVEL && it.location == THE_ALCOVE },
            "nothing was left on ${THE_ALCOVE.x}x${THE_ALCOVE.y}",
        )
    }

    /** Then walls itself off, so the search cannot be run twice. */
    @Test
    fun `then walls off the alcove behind it`() {
        val after = world().searched()

        assertEquals(1, after.wallByte(LEVEL, WHERE_IT_STOOD, WallSide.NORTH).value)
    }

    /** Refuse it and it keeps its hands to itself. */
    @Test
    fun `sent away, it takes nothing`() {
        val after = world().searched(answering = SEND_IT_AWAY)

        assertEquals(1, after.stillCarries(5))
        assertEquals(1, after.stillCarries(6))
    }

    private companion object {
        const val LEVEL = 15

        val THE_ALCOVE = Location(21, 12)
        val WHERE_IT_STOOD = Location(20, 12)

        /** The kind the two coins are, and the kind the giant searches for. */
        val COINS = ItemTypeId(46)

        /** Well past the hands and the worn slots. */
        val DEEP_IN_THE_PACK = CarrySlot(20)

        /** The two worths it asks for. */
        val WHAT_IT_TAKES = listOf(5, 6)

        const val LET_IT_TOUCH_YOU = 2
        const val SEND_IT_AWAY = 1
    }
}
