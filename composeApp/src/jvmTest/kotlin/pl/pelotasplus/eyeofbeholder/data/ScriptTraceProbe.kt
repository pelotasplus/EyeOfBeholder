package pl.pelotasplus.eyeofbeholder.data

import kotlinx.coroutines.runBlocking
import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.LevelScriptRunner
import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.PartyState
import pl.pelotasplus.eyeofbeholder.data.model.ScriptEvent
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

/**
 * Prints the triggers around a square and the trace of stepping onto it, so a
 * script that does nothing on screen can be read instruction by instruction.
 * Change the level and the coordinates to look at another square.
 */
class ScriptTraceProbe {

    @Test
    fun `step onto level5 13x9`() = runBlocking {
        val resources = ResourceRepositoryImpl()
        val palRepository = PalRepositoryImpl(resources)
        val cpsRepository = CpsRepositoryImpl(resources)
        val infRepository = InfRepositoryImpl(
            resourceRepository = resources,
            mazRepository = MazRepositoryImpl(resources),
            vmpRepository = VmpRepositoryImpl(resources),
            vcnRepository = VcnRepositoryImpl(resources),
            palRepository = palRepository,
            cpsRepository = cpsRepository,
            decRepository = DecRepositoryImpl(resources),
        )
        val items = ItemsRepositoryImpl(resources).loadItems().getOrThrow()
        val inf = infRepository.loadInf("LEVEL5.INF", items).getOrThrow()

        println("=== triggers on LEVEL5 around 13x9")
        inf.triggers
            .filter { it.location.y in 7..11 && it.location.x in 11..16 }
            .forEach { println("  $it") }

        val runner = LevelScriptRunner(inf.script)
        println("=== stepping onto 13x9")
        val outcome = runner.onEvent(
            triggers = inf.triggers,
            event = ScriptEvent.PARTY_ENTERED,
            party = PartyState(position = Location(13, 9), facing = Direction.WEST),
        )
        println("=== outcome $outcome")
    }
}
