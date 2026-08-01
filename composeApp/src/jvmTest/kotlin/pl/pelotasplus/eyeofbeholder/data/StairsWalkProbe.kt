package pl.pelotasplus.eyeofbeholder.data

import kotlinx.coroutines.runBlocking
import pl.pelotasplus.eyeofbeholder.data.model.DialogAnswer
import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.GameState
import pl.pelotasplus.eyeofbeholder.data.model.Inf
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

class StairsWalkProbe {

    @Test
    fun `walk up the level 5 stairs and see what level 6 does`() {
        val five = load("LEVEL5.INF")
        val six = load("LEVEL6.INF")

        val fiveRunner = LevelScriptRunner(five.script)
        println("WALK level5 (10,6) NORTH -> " + fiveRunner.onEvent(
            five.triggers, ScriptEvent.PARTY_ENTERED,
            GameState(PartyState(Location(10, 6), Direction.NORTH)),
        ))

        // arriving on level 6 at (10,3); nothing runs today, but what would?
        val sixRunner = LevelScriptRunner(six.script)
        println("WALK level6 arrival (10,3) NORTH -> " + sixRunner.onEvent(
            six.triggers, ScriptEvent.PARTY_ENTERED,
            GameState(PartyState(Location(10, 3), Direction.NORTH)),
        ))

        // the staircase square behind the arrival, each way round
        Direction.entries.forEach { facing ->
            println("WALK level6 (10,4) $facing -> " + LevelScriptRunner(six.script).onEvent(
                six.triggers, ScriptEvent.PARTY_ENTERED,
                GameState(PartyState(Location(10, 4), facing)),
            ))
        }

        // and the square ahead of the arrival
        println("WALK level6 (10,2) NORTH -> " + LevelScriptRunner(six.script).onEvent(
            six.triggers, ScriptEvent.PARTY_ENTERED,
            GameState(PartyState(Location(10, 2), Direction.NORTH)),
        ))

        val four = load("LEVEL4.INF")
        val fourRunner = LevelScriptRunner(four.script)
        val ask = fourRunner.onEvent(
            four.triggers, ScriptEvent.PARTY_ENTERED,
            GameState(PartyState(Location(15, 10), Direction.NORTH)),
        )
        println("ASK level4 (15,10) -> $ask")
        if (ask is pl.pelotasplus.eyeofbeholder.data.model.ScriptOutcome.AskThePlayer) {
            println("ASK   buttons: ${four.messages.getOrNull(ask.dialog.button1)} / ${four.messages.getOrNull(ask.dialog.button2)}")
            listOf(DialogAnswer(1), DialogAnswer(2)).forEach { answer ->
                println("ASK   answer=$answer -> " + LevelScriptRunner(four.script).answer(
                    ask.resumeAt, GameState(PartyState(Location(15, 10), Direction.NORTH)), answer,
                ))
            }
        }
    }

    private fun load(name: String): Inf = runBlocking {
        val r = ResourceRepositoryImpl()
        val pal = PalRepositoryImpl(r)
        val items = ItemsRepositoryImpl(r).loadItems().getOrThrow()
        InfRepositoryImpl(
            r, MazRepositoryImpl(r), VmpRepositoryImpl(r), VcnRepositoryImpl(r),
            pal, CpsRepositoryImpl(r), DecRepositoryImpl(r),
        ).loadInf(name, items).getOrThrow()
    }
}
