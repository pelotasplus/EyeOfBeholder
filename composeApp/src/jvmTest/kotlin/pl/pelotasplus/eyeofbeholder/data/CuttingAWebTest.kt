package pl.pelotasplus.eyeofbeholder.data

import kotlinx.coroutines.runBlocking
import org.junit.experimental.categories.Category
import pl.pelotasplus.eyeofbeholder.NeedsGameData
import pl.pelotasplus.eyeofbeholder.data.model.Abilities
import pl.pelotasplus.eyeofbeholder.data.model.Ability
import pl.pelotasplus.eyeofbeholder.data.model.ArmorClass
import pl.pelotasplus.eyeofbeholder.data.model.Blow
import pl.pelotasplus.eyeofbeholder.data.model.CarrySlot
import pl.pelotasplus.eyeofbeholder.data.model.Champion
import pl.pelotasplus.eyeofbeholder.data.model.ChampionFlags
import pl.pelotasplus.eyeofbeholder.data.model.CharacterClass
import pl.pelotasplus.eyeofbeholder.data.model.ClassLevel
import pl.pelotasplus.eyeofbeholder.data.model.Dice
import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.Fighting
import pl.pelotasplus.eyeofbeholder.data.model.Food
import pl.pelotasplus.eyeofbeholder.data.model.GameState
import pl.pelotasplus.eyeofbeholder.data.model.HitPoints
import pl.pelotasplus.eyeofbeholder.data.model.Inf
import pl.pelotasplus.eyeofbeholder.data.model.Item
import pl.pelotasplus.eyeofbeholder.data.model.ItemIndex
import pl.pelotasplus.eyeofbeholder.data.model.ItemTypeId
import pl.pelotasplus.eyeofbeholder.data.model.ItemTypes
import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.PartySlot
import pl.pelotasplus.eyeofbeholder.data.model.PartyState
import pl.pelotasplus.eyeofbeholder.data.model.PortraitId
import pl.pelotasplus.eyeofbeholder.data.model.WallByte
import pl.pelotasplus.eyeofbeholder.data.model.WallSide
import pl.pelotasplus.eyeofbeholder.data.model.WhatTheBlowCameTo
import pl.pelotasplus.eyeofbeholder.data.model.XpPoints
import pl.pelotasplus.eyeofbeholder.data.repository.CpsRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.DecRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.InfRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.ItemTypesRepositoryImpl
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
 * The web strung across the first floor's corridor at (27,8), and cutting it.
 *
 * A web is the one wall in the game that answers a weapon rather than a
 * click. Swinging at it with nothing standing in the way takes it down — no
 * roll, no damage, no monster — and what is left is the next wall along in the
 * level's own table: a torn web, still drawn and walked through.
 *
 * The slot says which of two words, and that is the only thing the weapon
 * decides: an edged one hacks, a blunt one and a bare fist bash.
 */
@Category(NeedsGameData::class)
class CuttingAWebTest {

    private val resources = ResourceRepositoryImpl()

    private val level: Inf = runBlocking {
        InfRepositoryImpl(
            resourceRepository = resources,
            mazRepository = MazRepositoryImpl(resources),
            vmpRepository = VmpRepositoryImpl(resources),
            vcnRepository = VcnRepositoryImpl(resources),
            palRepository = PalRepositoryImpl(resources),
            cpsRepository = CpsRepositoryImpl(resources),
            decRepository = DecRepositoryImpl(resources),
        ).loadInf("LEVEL1.INF").getOrThrow()
    }

    private val itemTypes: ItemTypes = runBlocking {
        ItemTypesRepositoryImpl(resources).loadItemTypes().getOrThrow()
    }

    private val dungeonItems: List<Item> = runBlocking {
        ItemsRepositoryImpl(resources).loadItems().getOrThrow().items
    }

    /** The webs live in the sublevel that has a wall for them. */
    private val sublevel = level.subLevels.first { it.wallsThatGiveWay.isNotEmpty() }

    private fun champion(
        holding: ItemIndex = ItemIndex(ItemIndex.NOTHING),
        isA: CharacterClass = CharacterClass.FIGHTER,
    ) = Champion(
        name = "Anselm",
        portrait = PortraitId(0),
        abilities = Abilities(strength = Ability(10, 10), dexterity = Ability(10, 10)),
        hitPoints = HitPoints(20, 20),
        armorClass = ArmorClass(10),
        food = Food(100),
        characterClass = isA,
        levels = listOf(ClassLevel(1, XpPoints(0))),
        carrying = List(27) { if (it == 0) holding else ItemIndex(ItemIndex.NOTHING) },
        flags = ChampionFlags(1),
    )

    private fun world(
        holding: ItemIndex = ItemIndex(ItemIndex.NOTHING),
        isA: CharacterClass = CharacterClass.FIGHTER,
    ) = GameState(
        party = PartyState(BEFORE_THE_WEB, Direction.NORTH),
        champions = listOf(champion(holding, isA), champion()),
        items = dungeonItems,
    ).arrivingAt(
        level = LEVEL,
        places = emptyList(),
        maz = sublevel.maz,
        kinds = sublevel.monsters,
    )

    private fun swing(world: GameState, whose: PartySlot = PartySlot(0)) = Fighting(
        itemTypes = itemTypes,
        kinds = sublevel.monsters,
        dice = Dice { times, pips, modifier -> times * pips + modifier },
        level = LEVEL,
        wallsThatGiveWay = sublevel.wallsThatGiveWay,
    ).strike(world, whose, CarrySlot(0))

    private fun GameState.sidesOf(square: Location) =
        WallSide.entries.map { wallByte(LEVEL, square, it).value }

    /**
     * Something of [type] out of the dungeon. Slot zero is skipped: it is the
     * number an empty hand holds, so a champion given it holds nothing.
     */
    private fun anItemOfType(type: ItemTypeId) = ItemIndex(
        dungeonItems.withIndex().drop(1).first { it.value.type == type }.index
    )

    @Test
    fun `the corridor is webbed across both faces of the square`() {
        assertEquals(setOf(WallByte(WEB)), sublevel.wallsThatGiveWay)
        assertEquals(listOf(WEB, 0, WEB, 0), world().sidesOf(WEBBED))
    }

    @Test
    fun `a swing at it takes it down`() {
        val struck = swing(world())

        assertEquals(Blow.CutDown(edged = false), struck.blow)
        assertEquals(listOf(TORN, 0, TORN, 0), struck.world.sidesOf(WEBBED))
    }

    /** Both faces go, so the far side of it does not stay standing. */
    @Test
    fun `the whole of it goes and not only the face swung at`() {
        val struck = swing(world())

        assertEquals(TORN, struck.world.wallByte(LEVEL, WEBBED, WallSide.NORTH).value)
        assertEquals(TORN, struck.world.wallByte(LEVEL, WEBBED, WallSide.SOUTH).value)
    }

    /** What is left is a wall the party walk through, which is the point of it. */
    @Test
    fun `the torn web is walked through`() {
        val torn = sublevel.decorations.first { it.decorationWallIndex == TORN }

        assertTrue(torn.flags.letThePartyThrough)
        assertTrue(torn.decorationID >= 0, "a torn web is still drawn")
    }

    @Test
    fun `a sword hacks`() {
        val struck = swing(world(holding = anItemOfType(LONG_SWORD)))

        assertEquals(Blow.CutDown(edged = true), struck.blow)
        assertEquals(WhatTheBlowCameTo.Hacked, WhatTheBlowCameTo.of(struck.blow))
    }

    @Test
    fun `a mace bashes`() {
        val struck = swing(world(holding = anItemOfType(MACE)))

        assertEquals(Blow.CutDown(edged = false), struck.blow)
        assertEquals(WhatTheBlowCameTo.Bashed, WhatTheBlowCameTo.of(struck.blow))
    }

    /** And so does a bare fist, which has no edge to hack with. */
    @Test
    fun `a bare hand bashes`() {
        assertEquals(WhatTheBlowCameTo.Bashed, WhatTheBlowCameTo.of(swing(world()).blow))
    }

    /**
     * The hand is asked what it holds and never who is holding it. A cleric
     * carries a mace and so bashes, but that is the mace's doing: put a sword
     * in the same hand and the same cleric hacks.
     *
     * Which is not a hole. A class is warned off what it may not use and then
     * allowed to try anyway, so a cleric holding a sword is a position the
     * game lets the player reach.
     */
    @Test
    fun `a cleric hacks or bashes by what is in the hand`() {
        val withAMace = swing(world(anItemOfType(MACE), isA = CharacterClass.CLERIC))
        val withASword = swing(world(anItemOfType(LONG_SWORD), isA = CharacterClass.CLERIC))

        assertEquals(WhatTheBlowCameTo.Bashed, WhatTheBlowCameTo.of(withAMace.blow))
        assertEquals(WhatTheBlowCameTo.Hacked, WhatTheBlowCameTo.of(withASword.blow))
    }

    /** And either way the web is down, which no class is barred from. */
    @Test
    fun `every class takes it down`() {
        CharacterClass.entries.forEach { isA ->
            assertEquals(
                listOf(TORN, 0, TORN, 0),
                swing(world(isA = isA)).world.sidesOf(WEBBED),
                "a $isA should have taken the web down",
            )
        }
    }

    /**
     * Whichever of the three it was, the web is down. Nothing about the weapon
     * decides that — only which word the slot says while it happens.
     */
    @Test
    fun `all three take it down`() {
        listOf(anItemOfType(LONG_SWORD), anItemOfType(MACE), ItemIndex(ItemIndex.NOTHING))
            .forEach { held ->
                assertEquals(
                    listOf(TORN, 0, TORN, 0),
                    swing(world(holding = held)).world.sidesOf(WEBBED),
                    "holding $held should have taken the web down",
                )
            }
    }

    /**
     * Once it is down there is nothing there, and the next swing is a miss.
     * The one beside them swings it: the hand that cut it is still coming back
     * to rest and would answer nothing at all.
     */
    @Test
    fun `swinging at what is left of it hits nothing`() {
        val again = swing(swing(world()).world, whose = PartySlot(1))

        assertEquals(Blow.Nothing, again.blow)
        assertEquals(listOf(TORN, 0, TORN, 0), again.world.sidesOf(WEBBED))
    }

    /** An arm that does not reach the square does not reach the web on it. */
    @Test
    fun `somebody in the back rank cuts nothing`() {
        val struck = swing(world(), whose = PartySlot(2))

        assertEquals(Blow.OutOfReach, struck.blow)
        assertEquals(listOf(WEB, 0, WEB, 0), struck.world.sidesOf(WEBBED))
    }

    /** A square with no web on it is swung at and missed, as before. */
    @Test
    fun `a plain wall is not cut`() {
        val elsewhere = world().copy(
            party = PartyState(Location(27, 11), Direction.NORTH),
        )

        assertEquals(Blow.Nothing, swing(elsewhere).blow)
    }

    private companion object {
        const val LEVEL = 1

        /** The web's wall byte, and what a blow turns it into. */
        const val WEB = 59
        const val TORN = 60

        /** One of each, to say which way round hacking and bashing go. */
        val LONG_SWORD = ItemTypeId(1)
        val MACE = ItemTypeId(11)

        val BEFORE_THE_WEB = Location(27, 9)
        val WEBBED = Location(27, 8)
    }
}
