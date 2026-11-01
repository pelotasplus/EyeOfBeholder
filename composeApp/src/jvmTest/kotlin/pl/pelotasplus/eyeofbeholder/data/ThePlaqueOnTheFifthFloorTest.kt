package pl.pelotasplus.eyeofbeholder.data

import kotlinx.coroutines.runBlocking
import org.junit.experimental.categories.Category
import pl.pelotasplus.eyeofbeholder.NeedsGameData
import pl.pelotasplus.eyeofbeholder.data.model.Champion
import pl.pelotasplus.eyeofbeholder.data.model.ChampionFlags
import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.GameState
import pl.pelotasplus.eyeofbeholder.data.model.HitPoints
import pl.pelotasplus.eyeofbeholder.data.model.Inf
import pl.pelotasplus.eyeofbeholder.data.model.LevelScriptRunner
import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.MessageId
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The level's own script, whose words say what to expect: "i shall only grant
 * three", three flags to count them, and message 29 once all three are set.
 */
@Category(NeedsGameData::class)
class ThePlaqueOnTheFifthFloorTest {

    private val inf: Inf = runBlocking {
        val r = ResourceRepositoryImpl()
        InfRepositoryImpl(
            r, MazRepositoryImpl(r), VmpRepositoryImpl(r), VcnRepositoryImpl(r),
            PalRepositoryImpl(r), CpsRepositoryImpl(r), DecRepositoryImpl(r),
        ).loadInf("LEVEL5.INF").getOrThrow()
    }

    private val plaque = Location(1, 2)

    private val theDead = List(4) {
        Champion.NOBODY.copy(
            name = "Dead $it",
            flags = ChampionFlags(IN_THE_PARTY),
            hitPoints = HitPoints(Champion.BEYOND_RAISING, 30),
        )
    }

    private fun clicked(state: GameState, stage: Picks): GameState = runBlocking {
        LevelScriptRunner(inf.script, level = 5).onEvent(
            triggers = inf.triggers,
            event = ScriptEvent.WALL_CLICKED,
            state = state,
            stage = stage,
            at = plaque,
        ).state
    }

    private val start = GameState(party = PartyState(Location(2, 2), Direction.WEST), champions = theDead)

    @Test
    fun `it grants three and then says its powers are exhausted`() {
        var world = start
        repeat(3) { use ->
            val picks = Picks(1)
            world = clicked(world, picks)
            assertEquals(1, picks.asked.size, "use ${use + 1} did not ask")
            assertEquals(use + 1, world.champions.count { it.hitPoints.current == Champion.RAISED_WITH })
        }

        val fourth = Picks(1)
        val after = clicked(world, fourth)

        assertTrue(fourth.asked.isEmpty(), "a fourth raising was offered")
        assertTrue(EXHAUSTED in fourth.said, "it did not say it was spent")
        assertEquals(1, after.champions.count { it.deadForGood })
    }

    @Test
    fun `backing out does not spend a use`() {
        val picks = Picks(theDead.size + 1)
        val after = clicked(start, picks)

        assertEquals(1, picks.asked.size)
        assertFalse(after.isLevelFlagSet(5, FIRST_USE), "the way out spent a raising")
        assertEquals(theDead.size, after.champions.count { it.deadForGood })
    }

    private companion object {
        const val IN_THE_PARTY = 0x01
        val EXHAUSTED = MessageId(29)
        val FIRST_USE = pl.pelotasplus.eyeofbeholder.data.model.FlagBit(9)
    }
}
