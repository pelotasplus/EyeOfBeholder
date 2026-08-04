package pl.pelotasplus.eyeofbeholder.data

import kotlinx.coroutines.runBlocking
import pl.pelotasplus.eyeofbeholder.data.model.DialogAnswer
import pl.pelotasplus.eyeofbeholder.data.model.DialogueTextId
import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.GameState
import pl.pelotasplus.eyeofbeholder.data.model.LevelScriptRunner
import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.PartyState
import pl.pelotasplus.eyeofbeholder.data.model.ScriptEvent
import pl.pelotasplus.eyeofbeholder.data.model.entryPoints
import pl.pelotasplus.eyeofbeholder.data.model.script.ClearFlag
import pl.pelotasplus.eyeofbeholder.data.model.script.Conditional
import pl.pelotasplus.eyeofbeholder.data.model.script.Eval
import pl.pelotasplus.eyeofbeholder.data.model.script.Message
import pl.pelotasplus.eyeofbeholder.data.model.script.SetFlag
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
        val inf = infRepository.loadInf("LEVEL5.INF").getOrThrow()

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
        val stepped = LevelScriptRunner(inf.script).onEvent(
            triggers = inf.triggers,
            event = ScriptEvent.PARTY_ENTERED,
            state = GameState(
                party = PartyState(position = Location(13, 9), facing = Direction.WEST),
                monsters = inf.monsterInstances,
            ),
        )
        println("=== party ${stepped.state.party}")
        println("=== changed level to ${stepped.changeLevel}")

        println("=== who touches the global flags, across every level")
        (1..16).forEach { number ->
            infRepository.loadScript("LEVEL$number.INF").getOrThrow().forEach { step ->
                val token = step.token
                val what = when {
                    token is SetFlag.GlobalFlag -> "sets ${token.bit.index}"
                    token is ClearFlag.GlobalFlag -> "clears ${token.flag}"
                    token is Eval -> token.tokens
                        .filterIsInstance<Conditional.GetGlobalFlag>()
                        .joinToString { "reads ${it.bit.index}" }
                        .ifEmpty { null }

                    else -> null
                }
                if (what != null) println("  LEVEL$number ${step.offset} $what")
            }
        }

        println("=== what level 1 is doing where it sets global flag 30")
        val one = infRepository.loadInf("LEVEL1.INF").getOrThrow()
        one.triggers.filter { it.script.offset.value in 0..70 }
            .forEach { println("  trigger at ${it.location} -> ${it.script.offset}") }
        one.script.filter { it.offset.value in 30..100 }.forEach { step ->
            val text = (step.token as? Message)?.let { one.message(it.messageId) }
            println("  ${step.offset} ${step.token}${text?.let { " '$it'" } ?: ""}")
        }

        println("=== which flags each way in to the clerics sets")
        listOf(Location(13, 9), Location(13, 11), Location(11, 9)).forEach { at ->
            val walked = LevelScriptRunner(inf.script, level = 5).onEvent(
                triggers = inf.triggers,
                event = ScriptEvent.PARTY_ENTERED,
                state = GameState(
                    party = PartyState(at, Direction.NORTH),
                    monsters = inf.monsterInstances,
                ),
            )
            println("  $at -> ${walked.state.flags.forLevel(5)}")
        }

        println("=== dialogue texts the encounter refers to")
        val dialogueText = pl.pelotasplus.eyeofbeholder.data.repository
            .DialogueTextRepositoryImpl(resources)
        (20..25).forEach { id ->
            println("  $id -> '${dialogueText.text(DialogueTextId(id)).getOrNull()}'")
        }

        println("=== stepping onto the door square 15x9")
        println("=== -> " + LevelScriptRunner(inf.script).onEvent(
            triggers = inf.triggers,
            event = ScriptEvent.PARTY_ENTERED,
            state = GameState(
                party = PartyState(Location(15, 9), Direction.EAST),
                monsters = inf.monsterInstances,
            ),
        ))

        listOf(1, 2, 3).forEach { answer ->
            val stage = RecordingStage(answers = listOf(answer))
            val after = LevelScriptRunner(inf.script, level = 5).onEvent(
                triggers = inf.triggers,
                event = ScriptEvent.PARTY_ENTERED,
                state = GameState(
                    party = PartyState(Location(13, 9), Direction.NORTH),
                    monsters = inf.monsterInstances,
                ),
                stage = stage,
            )
            println("=== answering $answer -> ${after.changeLevel ?: after.state.party}")
            stage.beats.forEach { beat -> println("===   $beat") }
        }
    }
}
