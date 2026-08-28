package pl.pelotasplus.eyeofbeholder.data

import kotlinx.coroutines.runBlocking
import pl.pelotasplus.eyeofbeholder.data.model.Abilities
import pl.pelotasplus.eyeofbeholder.data.model.Ability
import pl.pelotasplus.eyeofbeholder.data.model.ArmorClass
import pl.pelotasplus.eyeofbeholder.data.model.CarrySlot
import pl.pelotasplus.eyeofbeholder.data.model.Champion
import pl.pelotasplus.eyeofbeholder.data.model.ChampionFlags
import pl.pelotasplus.eyeofbeholder.data.model.CharacterClass
import pl.pelotasplus.eyeofbeholder.data.model.ClassLevel
import pl.pelotasplus.eyeofbeholder.data.model.Dice
import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.Food
import pl.pelotasplus.eyeofbeholder.data.model.GameState
import pl.pelotasplus.eyeofbeholder.data.model.HitPoints
import pl.pelotasplus.eyeofbeholder.data.model.Inf
import pl.pelotasplus.eyeofbeholder.data.model.Item
import pl.pelotasplus.eyeofbeholder.data.model.ItemIndex
import pl.pelotasplus.eyeofbeholder.data.model.ItemNames
import pl.pelotasplus.eyeofbeholder.data.model.ItemTypes
import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.MonsterInstance
import pl.pelotasplus.eyeofbeholder.data.model.MonsterProperty
import pl.pelotasplus.eyeofbeholder.data.model.MonsterSlot
import pl.pelotasplus.eyeofbeholder.data.model.MonsterTypeId
import pl.pelotasplus.eyeofbeholder.data.model.MonstersTurn
import pl.pelotasplus.eyeofbeholder.data.model.PartySlot
import pl.pelotasplus.eyeofbeholder.data.model.PartyState
import pl.pelotasplus.eyeofbeholder.data.model.PortraitId
import pl.pelotasplus.eyeofbeholder.data.model.SquarePlace
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What a monster's blow destroys of what the champion was carrying.
 *
 * Level 3's first kind is the one that does it and its second is one that does
 * not, so the same floor gives both halves of the rule. Three blows in four
 * take something; where the search starts is random and it takes the first
 * perishable thing from there, so the bottom of the pack is no safer than the
 * hand.
 */
class MonsterRuiningWhatItHitsTest {

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
        ).loadInf("LEVEL3.INF").getOrThrow()
    }

    private val kinds: List<MonsterProperty> get() = level.subLevels[0].monsters

    private val itemTypes: ItemTypes = runBlocking {
        ItemTypesRepositoryImpl(resources).loadItemTypes().getOrThrow()
    }

    private val dungeon = runBlocking {
        ItemsRepositoryImpl(resources).loadItems().getOrThrow()
    }

    private val items: List<Item> get() = dungeon.items
    private val names: ItemNames get() = dungeon.names

    /** The first thing in the dungeon that goes by [name] before it is known. */
    private fun dungeonNamed(name: String): ItemIndex {
        val at = items.indexOfFirst { names[it.nameUnidentified] == name }
        assertTrue(at > 0, "the dungeon holds no $name")
        return ItemIndex(at)
    }

    /** A sword is metal and goes; a robe is cloth and stays. */
    private val aSword = dungeonNamed("Long Sword")
    private val aRobe = dungeonNamed("Robe")
    private val chainmail = dungeonNamed("Chainmail")

    private fun champion(carrying: List<ItemIndex>, armour: Int) = Champion(
        name = "Anselm",
        portrait = PortraitId(0),
        abilities = Abilities(strength = Ability(10, 10), dexterity = Ability(10, 10)),
        hitPoints = HitPoints(40, 40),
        armorClass = ArmorClass(armour),
        food = Food(100),
        characterClass = CharacterClass.FIGHTER,
        levels = listOf(ClassLevel(1, XpPoints(0))),
        carrying = carrying,
        flags = ChampionFlags(1),
    )

    /**
     * The party at 13x10 with one monster of [kind] filling 13x9 and facing
     * them, which is the square its arm reaches from.
     */
    private fun world(
        kind: Int = RUINS_THINGS,
        holding: Map<CarrySlot, ItemIndex> = mapOf(CarrySlot(0) to aSword),
        armour: Int = UNARMOURED,
    ): GameState {
        val slots = CarrySlot.NOTHING_IN_ANY.toMutableList()
        holding.forEach { (slot, item) -> slots[slot.index] = item }

        return GameState(
            party = PartyState(Location(13, 10), Direction.NORTH),
            champions = List(6) { champion(slots, armour) },
            items = items,
            monsters = listOf(
                MonsterInstance(
                    index = THE_ONE_IN_FRONT,
                    unit = 0,
                    location = Location(13, 9),
                    place = SquarePlace.MIDDLE,
                    direction = Direction.SOUTH,
                    type = MonsterTypeId(kind),
                    gfxIndex = 0,
                    mode = 0,
                    pause = 0,
                    weapon = 0,
                    pocketItem = 0,
                    hitPoints = HitPoints(40, 40),
                    provoked = true,
                ),
            ),
        )
    }

    /**
     * @param ruinRoll what `1d4` comes up: four spares whatever the blow would
     *   have taken, and anything else takes it.
     * @param from which of the twenty-seven slots the search starts at.
     * @param toHit what `1d20` comes up when the monster swings.
     */
    private fun dice(ruinRoll: Int = 1, from: Int = 0, toHit: Int = 20) =
        Dice { times, pips, modifier ->
            when {
                times == 1 && pips == IN_FOUR && modifier == 0 -> ruinRoll
                times == 1 && pips == CarrySlot.ALL_OF_THEM -> from
                times == 1 && pips == 20 && modifier == 0 -> toHit
                else -> times * pips + modifier
            }
        }

    private fun blowLandedBy(
        world: GameState,
        ruinRoll: Int = 1,
        from: Int = 0,
        toHit: Int = 20,
    ): MonstersTurn.Taken = MonstersTurn(
        kinds = kinds,
        dice = dice(ruinRoll, from, toHit),
        itemTypes = itemTypes,
    ).landed(world, listOf(THE_ONE_IN_FRONT))

    /** Whom the blow fell on, which the tables decide and not this test. */
    private fun MonstersTurn.Taken.whoWasHit(): PartySlot {
        assertEquals(1, struck.size, "the monster did not land its one blow")
        return struck.single().at
    }

    private fun GameState.held(whose: PartySlot, slot: CarrySlot) =
        championIn(whose)!!.holding(slot)

    @Test
    fun `a blow takes a perishable thing off the champion it lands on`() {
        val taken = blowLandedBy(world())
        val whose = taken.whoWasHit()

        assertEquals(listOf(MonstersTurn.Ruined(whose, aSword)), taken.ruined)
        assertNull(
            taken.world.item(taken.world.held(whose, CarrySlot(0))),
            "the slot still holds it",
        )
    }

    /** The fourth blow in four spares whatever it would have taken. */
    @Test
    fun `one blow in four ruins nothing`() {
        val taken = blowLandedBy(world(), ruinRoll = SPARES_IT)

        assertEquals(emptyList(), taken.ruined)
        assertEquals(aSword, taken.world.held(taken.whoWasHit(), CarrySlot(0)))
    }

    /** Being hit is not enough: the kind has to be one that ruins things. */
    @Test
    fun `a kind that does not ruin things leaves what it hits alone`() {
        val taken = blowLandedBy(world(kind = RUINS_NOTHING))

        assertEquals(emptyList(), taken.ruined)
        assertEquals(aSword, taken.world.held(taken.whoWasHit(), CarrySlot(0)))
    }

    /**
     * The search starts somewhere at random and goes on from there, so a slot
     * holding something that does not perish is passed over rather than ending
     * it.
     */
    @Test
    fun `a thing that cannot be ruined is passed over for one that can`() {
        val taken = blowLandedBy(
            world(holding = mapOf(CarrySlot(0) to aRobe, CarrySlot(1) to aSword)),
            from = 0,
        )
        val whose = taken.whoWasHit()

        assertEquals(listOf(MonstersTurn.Ruined(whose, aSword)), taken.ruined)
        assertEquals(aRobe, taken.world.held(whose, CarrySlot(0)), "the robe went instead")
    }

    /** It goes round the twenty-seven rather than stopping at the last. */
    @Test
    fun `the search goes round the slots from wherever it starts`() {
        val taken = blowLandedBy(world(), from = CarrySlot.ALL_OF_THEM - 1)

        assertEquals(listOf(MonstersTurn.Ruined(taken.whoWasHit(), aSword)), taken.ruined)
    }

    /** Nothing to eat is nothing lost, and the blow lands all the same. */
    @Test
    fun `a champion carrying nothing perishable loses nothing`() {
        val taken = blowLandedBy(world(holding = mapOf(CarrySlot(0) to aRobe)))

        assertTrue(taken.struck.isNotEmpty(), "it did not land a blow at all")
        assertEquals(emptyList(), taken.ruined)
    }

    /** A swing that touched air takes nothing off anybody. */
    @Test
    fun `a blow that misses ruins nothing`() {
        val taken = blowLandedBy(world(armour = UNTOUCHABLE), toHit = 1)

        assertEquals(emptyList(), taken.struck, "the blow was supposed to miss")
        assertEquals(emptyList(), taken.ruined)
    }

    /**
     * Armour eaten off a champion is armour they no longer have the benefit
     * of, so it is worked out again rather than left at the number they were
     * carrying — which is the number they had while they still wore it.
     */
    @Test
    fun `armour that is ruined stops protecting the champion`() {
        val wearing = world(
            holding = mapOf(CarrySlot.WORN_ARMOUR to chainmail),
            armour = WHILE_IT_WAS_WORN,
        )
        val taken = blowLandedBy(wearing)
        val whose = taken.whoWasHit()

        assertEquals(listOf(MonstersTurn.Ruined(whose, chainmail)), taken.ruined)
        assertEquals(
            ArmorClass(UNARMOURED),
            taken.world.championIn(whose)!!.armorClass,
            "a champion in nothing at all is still counted as armoured",
        )
    }

    /** What it ruins, it ruins on the way in: the blow lands as well. */
    @Test
    fun `the champion is hurt by the same blow that took it`() {
        val before = world()
        val taken = blowLandedBy(before)
        val whose = taken.whoWasHit()

        assertEquals(
            before.championIn(whose)!!.hitPoints.current - taken.struck.single().damage.points,
            taken.world.championIn(whose)!!.hitPoints.current,
        )
    }

    private companion object {
        /** Level 3's first kind, which is the one that ruins what it hits. */
        const val RUINS_THINGS = 0

        /** And its second, which does not. */
        const val RUINS_NOTHING = 1

        val THE_ONE_IN_FRONT = MonsterSlot(7)

        /** How often it does: three of these, the fourth sparing it. */
        const val IN_FOUR = 4
        const val SPARES_IT = 4

        /** A champion in nothing at all, and one nothing can hit. */
        const val UNARMOURED = 10
        const val UNTOUCHABLE = -20

        /** Whatever they were saved wearing, which the blow makes stale. */
        const val WHILE_IT_WAS_WORN = 5
    }
}
