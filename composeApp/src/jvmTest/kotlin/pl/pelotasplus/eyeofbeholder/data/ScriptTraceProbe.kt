package pl.pelotasplus.eyeofbeholder.data

import kotlinx.coroutines.runBlocking
import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.GameState
import pl.pelotasplus.eyeofbeholder.data.model.entryPoints
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

        println("=== LEVEL5 has ${inf.subLevels.size} sublevels: ${inf.subLevels.map { it.index }}")
        println("=== how other levels enter LEVEL5")
        listOf(1, 2, 3, 4, 5, 6, 7).forEach { from ->
            infRepository.loadScript("LEVEL$from.INF").getOrThrow()
                .entryPoints(fromLevel = from)
                .filter { it.level == 5 }
                .forEach { println("  from LEVEL$from -> sub=${it.subLevel} ${it.location}") }
        }

        println("=== all ${inf.monsterInstances.size} monster instances")
        inf.monsterInstances.forEach {
            println("  unit=${it.unit} ${it.x}x${it.y} type=${it.type} mode=${it.mode}")
        }

        println("=== stepping onto 13x9")
        val outcome = LevelScriptRunner(inf.script).onEvent(
            triggers = inf.triggers,
            event = ScriptEvent.PARTY_ENTERED,
            state = GameState(
                party = PartyState(position = Location(13, 9), facing = Direction.WEST),
                monsters = inf.monsterInstances,
            ),
        )
        println("=== outcome $outcome")
    }
}
