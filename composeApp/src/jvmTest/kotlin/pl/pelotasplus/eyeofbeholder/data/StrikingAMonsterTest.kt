package pl.pelotasplus.eyeofbeholder.data

import kotlinx.coroutines.runBlocking
import org.junit.experimental.categories.Category
import pl.pelotasplus.eyeofbeholder.NeedsGameData
import pl.pelotasplus.eyeofbeholder.data.model.Abilities
import pl.pelotasplus.eyeofbeholder.data.model.Ability
import pl.pelotasplus.eyeofbeholder.data.model.ArmorClass
import pl.pelotasplus.eyeofbeholder.data.model.Blow
import pl.pelotasplus.eyeofbeholder.data.model.CharacterClass
import pl.pelotasplus.eyeofbeholder.data.model.Champion
import pl.pelotasplus.eyeofbeholder.data.model.CarrySlot
import pl.pelotasplus.eyeofbeholder.data.model.ChampionFlags
import pl.pelotasplus.eyeofbeholder.data.model.ClassLevel
import pl.pelotasplus.eyeofbeholder.data.model.XpPoints
import pl.pelotasplus.eyeofbeholder.data.model.Dice
import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.Fighting
import pl.pelotasplus.eyeofbeholder.data.model.Food
import pl.pelotasplus.eyeofbeholder.data.model.GameState
import pl.pelotasplus.eyeofbeholder.data.model.HandRecovering
import pl.pelotasplus.eyeofbeholder.data.model.HitPoints
import pl.pelotasplus.eyeofbeholder.data.model.Inf
import pl.pelotasplus.eyeofbeholder.data.model.ItemIndex
import pl.pelotasplus.eyeofbeholder.data.model.ItemKind
import pl.pelotasplus.eyeofbeholder.data.model.ItemTypes
import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.MonsterSlot
import pl.pelotasplus.eyeofbeholder.data.model.PartySlot
import pl.pelotasplus.eyeofbeholder.data.model.PartyState
import pl.pelotasplus.eyeofbeholder.data.model.PortraitId
import pl.pelotasplus.eyeofbeholder.data.model.needsToHit
import pl.pelotasplus.eyeofbeholder.data.repository.CpsRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.DecRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.InfRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.ItemsRepositoryImpl
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
 * The rules are AD&D and the numbers are transcribed. A champion needs a
 * twenty-sided die at or above `20 - (steps of improvement) - the monster's
 * armour class`, where how big a step is and how often it comes is what tells
 * a fighter from a mage. The clerics are armour class 2.
 */
@Category(NeedsGameData::class)
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
    private val onTheLeft = MonsterSlot(17)
    private val onTheRight = MonsterSlot(16)

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
        levels = listOf(ClassLevel(level, XpPoints(0))),
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

    private val dungeon = runBlocking {
        ItemsRepositoryImpl(resources).loadItems().getOrThrow()
    }

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

        assertEquals(18, mage.copy(levels = listOf(ClassLevel(1, XpPoints(0)))).needsToHit(2))
        assertEquals(18, mage.copy(levels = listOf(ClassLevel(3, XpPoints(0)))).needsToHit(2))
        assertEquals(17, mage.copy(levels = listOf(ClassLevel(4, XpPoints(0)))).needsToHit(2))
    }

    /** A cleric improves two points every three levels, so it comes in jumps. */
    @Test
    fun `a cleric improves two points every three levels`() {
        val cleric = fighter().copy(characterClass = CharacterClass.CLERIC)

        assertEquals(18, cleric.copy(levels = listOf(ClassLevel(3, XpPoints(0)))).needsToHit(2))
        assertEquals(16, cleric.copy(levels = listOf(ClassLevel(4, XpPoints(0)))).needsToHit(2))
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
        assertEquals(
            before.monsters.map { it.hitPoints },
            struck.world.monsters.map { it.hitPoints },
        )
    }

    /**
     * Swinging at one of a pair rouses both, and a miss rouses them as surely
     * as a hit: the two on level 5 greet the party together and stop talking
     * together the moment either is swung at.
     */
    @Test
    fun `a swing rouses everything that was waiting to see`() {
        val missed = fighting(alwaysOne).strike(world(), PartySlot(0), CarrySlot(0)).world

        assertTrue(missed.monsters.all { it.provoked }, "the pair are still talking")
    }

    /** Until then they are scenery, however long the party stand there. */
    @Test
    fun `nothing is roused until it is swung at`() {
        assertTrue(world().monsters.none { it.provoked })
    }

    /** And the highest lands, taking the damage off what it hit. */
    @Test
    fun `a high roll lands and hurts what it hit`() {
        val before = world()
        val struck = fighting(alwaysTwenty).strike(before, PartySlot(0), CarrySlot(0))

        val hit = assertIs<Blow.Hit>(struck.blow)
        assertTrue(hit.damage.landed, "a landed blow that does nothing is not a landed blow")

        val was = before.monsters.first { it.index == hit.monster }.hitPoints.current
        val now = struck.world.monsters.first { it.index == hit.monster }.hitPoints.current
        assertEquals(was - hit.damage.points, now)
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

    /**
     * A kill is worth what its kind is worth, and the party split it. The
     * cleric is left with a single hit point so one blow finishes it, and the
     * two thousand it carries is shared between the two who were there.
     */
    @Test
    fun `killing a monster shares out what it was worth`() {
        val nearlyDead = world().let { w ->
            w.copy(monsters = w.monsters.map {
                if (it.index == onTheLeft) it.copy(hitPoints = HitPoints(1, 1)) else it
            })
        }
        val worth = kinds.first { it.id == nearlyDead.monsters.first { m -> m.index == onTheLeft }.type.value }
            .experience

        val struck = fighting(alwaysTwenty).strike(nearlyDead, PartySlot(0), CarrySlot(0))

        assertTrue(struck.world.monsters.none { it.index == onTheLeft }, "the cleric survived")
        struck.world.champions.forEach {
            assertEquals(XpPoints(worth / 2L), it.levels.first().experience, "${it.name} was not paid")
        }
    }

    /**
     * A champion knocked down does not swing. Their slots are already drawn
     * barred over to say so, and until this the bars were the only thing
     * stopping them: the click went straight past and they fought on at
     * nothing left.
     */
    @Test
    fun `somebody down does not swing`() {
        val down = world(listOf(fighter().copy(hitPoints = HitPoints(0, 20)), fighter()))

        val struck = fighting(alwaysTwenty).strike(down, PartySlot(0), CarrySlot(0))

        assertEquals(Blow.Unable, struck.blow)
        assertEquals(down.monsters, struck.world.monsters, "they hurt something anyway")
        assertTrue(
            !struck.world.isRecovering(PartySlot(0), CarrySlot(0)),
            "a swing that never happened cost them the wait for one",
        )
    }

    /** And the one beside them, who is on their feet, still can. */
    @Test
    fun `the one beside them still swings`() {
        val down = world(listOf(fighter().copy(hitPoints = HitPoints(0, 20)), fighter()))

        assertIs<Blow.Hit>(fighting(alwaysTwenty).strike(down, PartySlot(1), CarrySlot(0)).blow)
    }

    /** A blow that does not land pays nobody. */
    @Test
    fun `missing earns no experience`() {
        val struck = fighting(alwaysOne).strike(world(), PartySlot(0), CarrySlot(0))

        assertTrue(struck.world.champions.all { it.levels.first().experience == XpPoints(0) })
    }

    // --- the hand coming back to rest ----------------------------------------

    // --- what a hand may swing -----------------------------------------------

    /**
     * The original sorts a hand by the kind of thing in it, and only three of
     * the twenty kinds are swung: the one that stays in the hand, the one that
     * is thrown, and the one that is fired from. Everything else is worn,
     * drunk, read or blown, and asking it to strike answers with a line of
     * text rather than a blow.
     *
     * [holding] takes the first thing in the dungeon of [kind] and puts it in
     * the first champion's first hand.
     */
    private fun holding(kind: ItemKind): GameState {
        val thing = dungeon.items.firstOrNull { itemTypes.kindOf(it) == kind }
        assertTrue(thing != null, "the dungeon holds nothing of kind $kind")

        val world = world()

        // Slot zero of the table is what an empty hand points at, so nothing
        // real may live there.
        val table = world.items.ifEmpty { listOf(thing) }
        val at = ItemIndex(table.size)

        return world.copy(
            items = table + thing,
            champions = world.champions.mapIndexed { slot, champion ->
                if (slot != 0) champion
                else champion.copy(carrying = champion.carrying.toMutableList().also { it[0] = at })
            },
        )
    }

    @Test
    fun `a shield is not a thing to hit with`() {
        val struck = fighting(alwaysTwenty).strike(holding(ItemKind.ARMOUR), PartySlot(0), CarrySlot(0))

        assertEquals(Blow.NotAWeapon, struck.blow)
        assertTrue(
            !struck.world.isRecovering(PartySlot(0), CarrySlot(0)),
            "a swing that never happened cost the hand the wait for one",
        )
    }

    /** And the lock picks, which are carried rather than wielded. */
    @Test
    fun `lock picks are not a thing to hit with either`() {
        val struck =
            fighting(alwaysTwenty).strike(holding(ItemKind.AN_ODDMENT), PartySlot(0), CarrySlot(0))

        assertEquals(Blow.NotAWeapon, struck.blow)
    }

    /**
     * An empty hand is not one of those. A fist swings like anything else and
     * rolls 1d2, which is the one case where nothing in the hand is the point
     * rather than the problem.
     */
    @Test
    fun `a bare hand swings and hurts`() {
        val struck = fighting(alwaysTwenty).strike(world(), PartySlot(0), CarrySlot(0))

        val hit = assertIs<Blow.Hit>(struck.blow, "an empty hand is a fist")
        assertTrue(hit.damage.points > 0, "a fist that lands takes something off")
    }

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

    /**
     * An arm that never went anywhere costs the shorter wait — long enough for
     * the slot to say so and no longer. The full one is for a swing that
     * happened, and the two are 18 and 18 plus 36.
     */
    @Test
    fun `a hand that cannot reach costs only the time it spends saying so`() {
        val cannot = fighting(alwaysTwenty).strike(world(), PartySlot(2), CarrySlot(0))
        val swung = fighting(alwaysTwenty).strike(world(), PartySlot(0), CarrySlot(0))

        assertEquals(
            HandRecovering.REPORTING.value,
            cannot.world.recovering.single().ticksLeft,
        )
        assertEquals(
            HandRecovering.AFTER_A_SWING.value,
            swung.world.recovering.single().ticksLeft,
        )
    }

    // --- what the slot says --------------------------------------------------

    /** A blow that landed is reported as the number it came to. */
    @Test
    fun `the slot says what a blow came to`() {
        val struck = fighting(alwaysTwenty).strike(world(), PartySlot(0), CarrySlot(0))
        val hit = assertIs<Blow.Hit>(struck.blow)

        val said = struck.world.reportIn(PartySlot(0), CarrySlot(0))
        assertEquals(listOf("${hit.damage.points}"), said?.lines)
    }

    /** A miss says so in a word, on the same splash of blood. */
    @Test
    fun `the slot says a miss`() {
        val struck = fighting(alwaysOne).strike(world(), PartySlot(0), CarrySlot(0))
        val said = struck.world.reportIn(PartySlot(0), CarrySlot(0))

        assertEquals(listOf("MISS"), said?.lines)
        assertTrue(said?.onABloodySplash == true)
    }

    /**
     * And an arm that never went says so in the colour the interface warns in
     * rather than on blood, there being none.
     */
    @Test
    fun `the slot warns that a champion cannot reach`() {
        val struck = fighting(alwaysTwenty).strike(world(), PartySlot(2), CarrySlot(0))
        val said = struck.world.reportIn(PartySlot(2), CarrySlot(0))

        assertEquals(listOf("CAN'T", "REACH"), said?.lines)
        assertTrue(said?.onABloodySplash == false)
    }

    /**
     * The slot stops saying before the hand comes back: the report is the
     * first 18 ticks of the 54, and the weapon is shown under the grid for
     * the rest.
     */
    @Test
    fun `the slot stops reporting before the hand is free`() {
        var world = fighting(alwaysTwenty).strike(world(), PartySlot(0), CarrySlot(0)).world

        while (world.reportIn(PartySlot(0), CarrySlot(0)) != null) {
            world = world.recoveryStepped()
        }

        assertTrue(
            world.isRecovering(PartySlot(0), CarrySlot(0)),
            "the hand came free the moment it stopped saying",
        )
    }

    // --- showing that it landed ----------------------------------------------

    /**
     * What is hit shows it, and only what is hit: the other cleric standing
     * beside it is drawn as itself.
     */
    @Test
    fun `only what was hit flashes`() {
        val after = fighting(alwaysTwenty).strike(world(), PartySlot(0), CarrySlot(0)).world

        assertTrue(after.anythingFlashing)
        assertTrue(after.monsters.single { it.index == onTheLeft }.struck)
        assertTrue(!after.monsters.single { it.index == onTheRight }.struck)
    }

    /** A miss shows nothing, there being nothing to show. */
    @Test
    fun `a miss flashes nothing`() {
        val after = fighting(alwaysOne).strike(world(), PartySlot(0), CarrySlot(0)).world

        assertTrue(!after.anythingFlashing)
    }

    /** And the moment passes. */
    @Test
    fun `the flash fades`() {
        val after = fighting(alwaysTwenty).strike(world(), PartySlot(0), CarrySlot(0)).world

        assertTrue(!after.flashesFaded().anythingFlashing)
    }

    /**
     * A blow that kills leaves nothing to flash. The monster is gone from the
     * world, so nothing is left holding a silhouette that would never fade.
     */
    @Test
    fun `a killing blow flashes nothing`() {
        var world = world()
        while (world.monsters.any { it.index == onTheLeft }) {
            world = fighting(alwaysTwenty)
                .strike(world.copy(recovering = emptyList()), PartySlot(0), CarrySlot(0)).world
        }

        assertTrue(!world.anythingFlashing, "something dead is still flashing")
    }
}
