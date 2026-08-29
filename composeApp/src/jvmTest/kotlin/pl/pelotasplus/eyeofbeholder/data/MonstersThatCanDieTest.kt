package pl.pelotasplus.eyeofbeholder.data

import kotlinx.coroutines.runBlocking
import org.junit.experimental.categories.Category
import pl.pelotasplus.eyeofbeholder.NeedsGameData
import pl.pelotasplus.eyeofbeholder.data.model.Damage
import pl.pelotasplus.eyeofbeholder.data.model.Dice
import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.GameState
import pl.pelotasplus.eyeofbeholder.data.model.Inf
import pl.pelotasplus.eyeofbeholder.data.model.LevelScriptRunner
import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.MonsterSlot
import pl.pelotasplus.eyeofbeholder.data.model.PartyState
import pl.pelotasplus.eyeofbeholder.data.model.ScriptEvent
import pl.pelotasplus.eyeofbeholder.data.repository.CpsRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.DecRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.InfRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.MazRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.PalRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.ResourceRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.VcnRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.VmpRepositoryImpl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A monster that can be killed, and the level that has been waiting for one.
 *
 * Level 5's two clerics stand together on 13x8 and both of their scenes are
 * guarded on somebody still standing there — the approach at 13x9 and the
 * doorway at 11x9. The guards were always right; nothing could satisfy them,
 * so the pair went on greeting the party for ever however the party answered.
 *
 * Their kind is 8d8+8, which is 16 hit points at worst and 72 at best.
 */
@Category(NeedsGameData::class)
class MonstersThatCanDieTest {

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

    private val theyStand = Location(13, 8)
    private val theApproach = Location(13, 9)

    /** The two of them, by the slots the level's file puts them in. */
    private val firstCleric = MonsterSlot(16)
    private val secondCleric = MonsterSlot(17)

    private val kinds get() = level.subLevels[0].monsters

    /** Every die comes up its highest, so 8d8+8 is 72. */
    private val everythingMax = Dice { times, pips, modifier -> times * pips + modifier }

    /** And its lowest, so 8d8+8 is 16. */
    private val everythingMin = Dice { times, _, modifier -> times + modifier }

    private fun world(dice: Dice = everythingMax) = GameState(
        party = PartyState(Location(13, 10), Direction.NORTH),
    ).arrivingAt(
        level = 5,
        places = level.monsterInstances,
        maz = level.subLevels[0].maz,
        kinds = kinds,
        dice = dice,
    )

    @Test
    fun `a monster is rolled for out of its own kind's dice as it is placed`() {
        assertEquals(72, world(everythingMax).monsters.map { it.hitPoints.current }.distinct().single())
        assertEquals(16, world(everythingMin).monsters.map { it.hitPoints.current }.distinct().single())
    }

    /** What it can take now and what it could take are the same until it is hurt. */
    @Test
    fun `a monster starts undamaged`() {
        world().monsters.forEach { assertEquals(it.hitPoints.max, it.hitPoints.current) }
    }

    /**
     * The roll is per monster and not per kind, so two of the same creature are
     * not equally hard to kill. Both clerics here are 8d8+8.
     */
    @Test
    fun `two of a kind are rolled for separately`() {
        var nth = 0
        val counting = Dice { _, _, _ -> ++nth }

        val (first, second) = world(counting).monsters
        assertNotEquals(first.hitPoints.current, second.hitPoints.current)
    }

    @Test
    fun `damage short of killing leaves it standing`() {
        val hurt = world().monsterHurt(firstCleric, by = Damage(71))

        assertEquals(1, hurt.monsters.first { it.index == firstCleric }.hitPoints.current)
        assertEquals(72, hurt.monsters.first { it.index == firstCleric }.hitPoints.max)
        assertEquals(2, hurt.monstersOn(theyStand))
    }

    /**
     * A dead one leaves the world rather than lying in it: the square stops
     * counting it, which is the whole of what a script can ask about it.
     */
    @Test
    fun `enough damage kills it and the square stops counting it`() {
        val dead = world().monsterHurt(firstCleric, by = Damage(72))

        assertNull(dead.monsters.firstOrNull { it.index == firstCleric })
        assertEquals(1, dead.monstersOn(theyStand))

        val both = dead.monsterHurt(secondCleric, by = Damage(100))
        assertEquals(0, both.monstersOn(theyStand))
    }

    /**
     * A monster nobody rolled for has no hit points, and must not therefore
     * die to the first blow. The world says so rather than quietly killing it.
     */
    @Test
    fun `a monster nobody rolled for cannot be hurt`() {
        val unrolled = GameState(
            party = PartyState(Location(13, 10), Direction.NORTH),
        ).arrivingAt(level = 5, places = level.monsterInstances)

        assertTrue(unrolled.monsters.none { it.couldBeHurt })
        assertEquals(2, unrolled.monsterHurt(firstCleric, by = Damage(999)).monstersOn(theyStand))
    }

    /**
     * Walking off a level and back does not roll again: a monster left with one
     * hit point is still on one when the party return, and one already killed
     * stays killed.
     */
    @Test
    fun `coming back finds them as hurt as they were left`() {
        val hurt = world().monsterHurt(firstCleric, by = Damage(71)).monsterHurt(secondCleric, by = Damage(72))

        val returned = hurt.leaving(5).arrivingAt(
            level = 5,
            places = level.monsterInstances,
            kinds = kinds,
            dice = everythingMax,
        )

        assertEquals(1, returned.monstersOn(theyStand))
        assertEquals(1, returned.monsters.single().hitPoints.current)
    }

    // --- what the level has been waiting for ---------------------------------

    /**
     * The pair greet the party and offer the three answers their scene is
     * written around — leave, ask, attack.
     */
    @Test
    fun `the clerics speak while they live`() {
        val stage = approached(world())

        assertEquals(listOf(22), stage.questions.map { it.textId.number }.take(1))
        assertEquals(3, stage.questions.first().buttons.size)
    }

    /**
     * And say nothing at all once both are dead. Not a shorter scene, not the
     * same scene without them: the trigger is guarded on somebody standing
     * there, so the script does not run.
     */
    @Test
    fun `the clerics are silent once they are dead`() {
        val dead = world()
            .monsterHurt(firstCleric, by = Damage(72))
            .monsterHurt(secondCleric, by = Damage(72))

        assertEquals(emptyList(), approached(dead).beats)
    }

    /** One of them left alive is enough to hold the scene open. */
    @Test
    fun `one of them left alive still speaks`() {
        val half = world().monsterHurt(firstCleric, by = Damage(72))

        assertTrue(approached(half).beats.isNotEmpty())
    }

    private fun approached(from: GameState): RecordingStage {
        val stage = RecordingStage(answers = listOf(1, 1, 1))
        runBlocking {
            LevelScriptRunner(level.script, level = 5, subLevel = 0, kinds = kinds).onEvent(
                triggers = level.triggers,
                event = ScriptEvent.PARTY_ENTERED,
                state = from.partyMovedTo(theApproach),
                stage = stage,
                at = theApproach,
            )
        }
        return stage
    }
}
