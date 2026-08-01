package pl.pelotasplus.eyeofbeholder.data

import kotlinx.coroutines.runBlocking
import pl.pelotasplus.eyeofbeholder.data.model.Inf
import pl.pelotasplus.eyeofbeholder.data.model.LevelScriptRunner
import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.ScriptEvent
import pl.pelotasplus.eyeofbeholder.data.model.ScriptOutcome
import pl.pelotasplus.eyeofbeholder.data.model.script.NewLevelOrMonster
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
 * The Block C trigger map and the script interpreter that moves the party
 * between levels.
 */
class LevelTransitionTest {

    @Test
    fun `every level parses its trigger map`() {
        val names = runBlocking { resources.listResources(".INF").getOrThrow() }
        val counts = names.associateWith { runBlocking { load(it).triggers.size } }

        assertTrue(
            counts.values.any { it > 0 },
            "no level declared any trigger, Block C is probably being misparsed"
        )
        println("triggers per level: ${counts.entries.sortedBy { it.key }.joinToString()}")
    }

    @Test
    fun `stepping on the level 4 stairs changes level`() {
        val level = load("LEVEL4.INF")
        val runner = LevelScriptRunner(level.script)

        val outcome = runner.onEvent(
            triggers = level.triggers,
            at = Location(15, 10),
            event = ScriptEvent.PARTY_ENTERED,
        )

        assertTrue(
            outcome is ScriptOutcome.ChangeLevel,
            "expected entering (15,10) to change level, got $outcome"
        )
        assertEquals(5, outcome.level)
    }

    @Test
    fun `a trigger that does not react to entering stays silent`() {
        val level = load("LEVEL4.INF")
        val runner = LevelScriptRunner(level.script)

        // (17,4) has flags 0x0: it reacts to a wall click, not to the party
        val outcome = runner.onEvent(
            triggers = level.triggers,
            at = Location(17, 4),
            event = ScriptEvent.PARTY_ENTERED,
        )

        assertEquals(ScriptOutcome.Nothing, outcome)
    }

    @Test
    fun `the level 4 change targets match the level data`() {
        val level = load("LEVEL4.INF")
        val targets = level.script
            .map { it.token }
            .filterIsInstance<NewLevelOrMonster.ChangeLevel>()
            .map { it.level }

        assertEquals(listOf(5, 1), targets, "level 4 should lead to levels 5 and 1")
    }

    private val resources = ResourceRepositoryImpl()

    private fun load(name: String): Inf = runBlocking {
        val pal = PalRepositoryImpl(resources)
        val items = ItemsRepositoryImpl(resources).loadItems().getOrThrow()
        InfRepositoryImpl(
            resourceRepository = resources,
            mazRepository = MazRepositoryImpl(resources),
            vmpRepository = VmpRepositoryImpl(resources),
            vcnRepository = VcnRepositoryImpl(resources),
            palRepository = pal,
            cpsRepository = CpsRepositoryImpl(resources),
            decRepository = DecRepositoryImpl(resources),
        ).loadInf(name, items).getOrThrow()
    }
}
