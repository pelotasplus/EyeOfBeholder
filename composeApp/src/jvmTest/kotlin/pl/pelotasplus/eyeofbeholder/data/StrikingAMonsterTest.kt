package pl.pelotasplus.eyeofbeholder.data

import kotlinx.coroutines.runBlocking
import pl.pelotasplus.eyeofbeholder.data.model.Abilities
import pl.pelotasplus.eyeofbeholder.data.model.Ability
import pl.pelotasplus.eyeofbeholder.data.model.ArmorClass
import pl.pelotasplus.eyeofbeholder.data.model.Blow
import pl.pelotasplus.eyeofbeholder.data.model.CharacterClass
import pl.pelotasplus.eyeofbeholder.data.model.Champion
import pl.pelotasplus.eyeofbeholder.data.model.CarrySlot
import pl.pelotasplus.eyeofbeholder.data.model.ChampionFlags
import pl.pelotasplus.eyeofbeholder.data.model.ClassLevel
import pl.pelotasplus.eyeofbeholder.data.model.Dice
import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.Fighting
import pl.pelotasplus.eyeofbeholder.data.model.Food
import pl.pelotasplus.eyeofbeholder.data.model.GameState
import pl.pelotasplus.eyeofbeholder.data.model.HitPoints
import pl.pelotasplus.eyeofbeholder.data.model.Inf
import pl.pelotasplus.eyeofbeholder.data.model.ItemIndex
import pl.pelotasplus.eyeofbeholder.data.model.ItemTypes
import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.PartySlot
import pl.pelotasplus.eyeofbeholder.data.model.PartyState
import pl.pelotasplus.eyeofbeholder.data.model.PortraitId
import pl.pelotasplus.eyeofbeholder.data.model.needsToHit
import pl.pelotasplus.eyeofbeholder.data.repository.CpsRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.DecRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.InfRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.ItemTypesRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.MazRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.PalRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.ResourceRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.VcnRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.VmpRepositoryImpl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Swinging a hand weapon at level 5's clerics, who stand together on 13x8.
 *
 * The rules are AD&D and the numbers are the original's. A champion needs a
 * twenty-sided die at or above `20 - (steps of improvement) - the monster's
 * armour class`, where how big a step is and how often it comes is what tells
 * a fighter from a mage. The clerics are armour class 2.
 */
class StrikingAMonsterTest {

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
        ).loadInf("LEVEL5.INF").getOrThrow()
    }

    private val itemTypes: ItemTypes = runBlocking {
        ItemTypesRepositoryImpl(resources).loadItemTypes().getOrThrow()
    }

    private val kinds get() = level.subLevels[0].monsters

    /**
     * The pair, by the slots the file puts them in. Coming up from 13x9 facing
     * north, the near corners of their square are its southern two: slot 17
     * stands in the south-west, on the party's left, and slot 16 in the
     * south-east, on their right.
     */
    private val onTheLeft = 17
    private val onTheRight = 16

    private fun fighter(level: Int = 1, strength: Int = 10) = Champion(
        name = "Anselm",
        portrait = PortraitId(0),
        abilities = Abilities(
            strength = Ability(strength, strength),
            dexterity = Ability(10, 10),
        ),
        hitPoints = HitPoints(20, 20),
        armorClass = ArmorClass(10),
        food = Food(100),
        characterClass = CharacterClass.FIGHTER,
        levels = listOf(ClassLevel(level, 0)),
        carrying = List(27) { ItemIndex(ItemIndex.NOTHING) },
        flags = ChampionFlags(1),
    )

    private fun world(party: List<Champion> = listOf(fighter(), fighter())) = GameState(
        party = PartyState(Location(13, 9), Direction.NORTH),
        champions = party,
    ).arrivingAt(
        level = 5,
        places = level.monsterInstances,
        maz = level.subLevels[0].maz,
        kinds = kinds,
        dice = Dice { times, pips, modifier -> times * pips + modifier },
    )

    /** Every die comes up its highest, so a d20 is 20 and nothing can miss. */
    private val alwaysTwenty = Dice { times, pips, modifier -> times * pips + modifier }

    /** And its lowest, so a d20 is 1 and only an impossible target is met. */
    private val alwaysOne = Dice { times, _, modifier -> times + modifier }

    private fun fighting(dice: Dice) = Fighting(itemTypes, kinds, dice)

    // --- what a champion needs to roll ---------------------------------------

    /**
     * A fighter improves a point a level, so a first-level one needs 18 against
     * armour class 2 and a ninth-level one needs 10.
     */
    @Test
    fun `a fighter improves a point a level`() {
        assertEquals(18, fighter(level = 1).needsToHit(2))
        assertEquals(17, fighter(level = 2).needsToHit(2))
        assertEquals(10, fighter(level = 9).needsToHit(2))
    }

    /**
     * A mage improves a point every three levels, so the first three levels are
     * all the same and the fourth is the first that is better.
     */
    @Test
    fun `a mage improves a point every three levels`() {
        val mage = fighter().copy(characterClass = CharacterClass.MAGE)

        assertEquals(18, mage.copy(levels = listOf(ClassLevel(1, 0))).needsToHit(2))
        assertEquals(18, mage.copy(levels = listOf(ClassLevel(3, 0))).needsToHit(2))
        assertEquals(17, mage.copy(levels = listOf(ClassLevel(4, 0))).needsToHit(2))
    }

    /** A cleric improves two points every three levels, so it comes in jumps. */
    @Test
    fun `a cleric improves two points every three levels`() {
        val cleric = fighter().copy(characterClass = CharacterClass.CLERIC)

        assertEquals(18, cleric.copy(levels = listOf(ClassLevel(3, 0))).needsToHit(2))
        assertEquals(16, cleric.copy(levels = listOf(ClassLevel(4, 0))).needsToHit(2))
    }

    /** Worse armour is easier to hit, and the two move together point for point. */
    @Test
    fun `armour class moves the target one for one`() {
        assertEquals(20, fighter().needsToHit(0))
        assertEquals(18, fighter().needsToHit(2))
        assertEquals(10, fighter().needsToHit(10))
    }

    // --- the swing -----------------------------------------------------------

    @Test
    fun `a champion in the back rank cannot reach`() {
        val struck = fighting(alwaysTwenty)
            .strike(world(), PartySlot(2), CarrySlot(0))

        assertEquals(Blow.OutOfReach, struck.blow)
    }

    @Test
    fun `a swing at nothing hits nothing`() {
        val empty = world().copy(monsters = emptyList())
        val struck = fighting(alwaysTwenty).strike(empty, PartySlot(0), CarrySlot(0))

        assertEquals(Blow.Nothing, struck.blow)
    }

    /**
     * A first-level fighter of ordinary strength needs 18 against these two,
     * so the lowest roll there is misses and the monster is untouched.
     */
    @Test
    fun `a low roll misses and takes nothing off`() {
        val before = world()
        val struck = fighting(alwaysOne).strike(before, PartySlot(0), CarrySlot(0))

        assertIs<Blow.Missed>(struck.blow)
        assertEquals(before.monsters, struck.world.monsters)
    }

    /** And the highest lands, taking the damage off what it hit. */
    @Test
    fun `a high roll lands and hurts what it hit`() {
        val before = world()
        val struck = fighting(alwaysTwenty).strike(before, PartySlot(0), CarrySlot(0))

        val hit = assertIs<Blow.Hit>(struck.blow)
        assertTrue(hit.damage > 0, "a landed blow that does nothing is not a landed blow")

        val was = before.monsters.first { it.index == hit.monster }.hitPoints.current
        val now = struck.world.monsters.first { it.index == hit.monster }.hitPoints.current
        assertEquals(was - hit.damage, now)
    }

    /**
     * The two of the front rank reach across the square differently, so they do
     * not both hit the same one while the other stands untouched beside it.
     */
    @Test
    fun `the two in front reach for different corners`() {
        val left = fighting(alwaysTwenty).strike(world(), PartySlot(0), CarrySlot(0)).blow
        val right = fighting(alwaysTwenty).strike(world(), PartySlot(1), CarrySlot(0)).blow

        assertEquals(Blow.Hit::class, left::class)
        assertEquals(onTheLeft, (left as Blow.Hit).monster)
        assertEquals(onTheRight, (right as Blow.Hit).monster)
    }

    /** Enough blows and the cleric is gone, which is the whole point of it. */
    @Test
    fun `enough blows kill one of them`() {
        var world = world()
        var swings = 0

        while (world.monsters.any { it.index == onTheLeft } && swings < 100) {
            world = fighting(alwaysTwenty)
                .strike(world.copy(recovering = emptyList()), PartySlot(0), CarrySlot(0)).world
            swings++
        }

        assertTrue(swings < 100, "the cleric never died")
        assertEquals(1, world.monstersOn(Location(13, 8)))
    }

    // --- the hand coming back to rest ----------------------------------------

    /**
     * A weapon is not swung as fast as the mouse can be clicked: the hand is
     * out of use afterwards, and a second click on it does nothing at all
     * rather than striking again.
     */
    @Test
    fun `a hand that has swung will not swing again at once`() {
        val once = fighting(alwaysTwenty).strike(world(), PartySlot(0), CarrySlot(0))
        assertTrue(once.world.isRecovering(PartySlot(0), CarrySlot(0)))

        val twice = fighting(alwaysTwenty).strike(once.world, PartySlot(0), CarrySlot(0))

        assertEquals(Blow.StillRecovering, twice.blow)
        assertEquals(once.world.monsters, twice.world.monsters, "it struck again anyway")
    }

    /** The other hand is its own, and so is everybody else's. */
    @Test
    fun `only the hand that swung is out of use`() {
        val struck = fighting(alwaysTwenty).strike(world(), PartySlot(0), CarrySlot(0)).world

        assertTrue(struck.isRecovering(PartySlot(0), CarrySlot(0)))
        assertTrue(!struck.isRecovering(PartySlot(0), CarrySlot(1)), "the shield hand went too")
        assertTrue(!struck.isRecovering(PartySlot(1), CarrySlot(0)), "the other champion too")
    }

    /**
     * Swinging at nothing costs the same wait. The arm is committed the moment
     * it goes back, and a free swing would otherwise be a free look at whether
     * anything is standing round the corner.
     */
    @Test
    fun `a swing at nothing still costs the wait`() {
        val empty = world().copy(monsters = emptyList())
        val struck = fighting(alwaysTwenty).strike(empty, PartySlot(0), CarrySlot(0))

        assertEquals(Blow.Nothing, struck.blow)
        assertTrue(struck.world.isRecovering(PartySlot(0), CarrySlot(0)))
    }

    /** And the wait runs out, after which the hand is good again. */
    @Test
    fun `the hand comes back to rest and can swing again`() {
        var world = fighting(alwaysTwenty).strike(world(), PartySlot(0), CarrySlot(0)).world

        var steps = 0
        while (world.recovering.isNotEmpty() && steps < 100) {
            world = world.recoveryStepped()
            steps++
        }

        assertTrue(steps < 100, "the hand never came back")
        assertTrue(!world.isRecovering(PartySlot(0), CarrySlot(0)))
        assertIs<Blow.Hit>(fighting(alwaysTwenty).strike(world, PartySlot(0), CarrySlot(0)).blow)
    }

    /** A hand out of reach is refused before it is committed to anything. */
    @Test
    fun `a hand that cannot reach costs nothing`() {
        val struck = fighting(alwaysTwenty).strike(world(), PartySlot(2), CarrySlot(0))

        assertEquals(emptyList(), struck.world.recovering)
    }
}
