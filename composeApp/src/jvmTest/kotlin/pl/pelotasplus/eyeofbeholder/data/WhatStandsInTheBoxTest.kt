package pl.pelotasplus.eyeofbeholder.data

import kotlinx.coroutines.runBlocking
import pl.pelotasplus.eyeofbeholder.data.model.DialogAnswer
import pl.pelotasplus.eyeofbeholder.data.model.DialogueTextId
import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.GameState
import pl.pelotasplus.eyeofbeholder.data.model.LevelScriptRunner
import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.MessageId
import pl.pelotasplus.eyeofbeholder.data.model.PartyState
import pl.pelotasplus.eyeofbeholder.data.model.ScriptEvent
import pl.pelotasplus.eyeofbeholder.data.model.ScriptQuestion
import pl.pelotasplus.eyeofbeholder.data.model.ScriptSpeech
import pl.pelotasplus.eyeofbeholder.data.model.ScriptStage
import pl.pelotasplus.eyeofbeholder.data.model.Ticks
import pl.pelotasplus.eyeofbeholder.data.model.TrackIndex
import pl.pelotasplus.eyeofbeholder.data.model.Volume
import pl.pelotasplus.eyeofbeholder.data.model.Trigger
import pl.pelotasplus.eyeofbeholder.data.model.TriggerFlags
import pl.pelotasplus.eyeofbeholder.data.model.script.Dialog
import pl.pelotasplus.eyeofbeholder.data.model.script.Script
import pl.pelotasplus.eyeofbeholder.data.model.script.ScriptOffset
import pl.pelotasplus.eyeofbeholder.data.model.script.ScriptToken
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Which of the two ways a script writes into the dialogue box takes what was
 * already there with it.
 *
 * The engine has one call that prints a speech where the last one left off,
 * and another that puts a question and draws the box again over the answer.
 * Both arrive here as a [ScriptQuestion], so the box would be wiped by either
 * unless the two are told apart.
 *
 * The magic mouth is what this costs when it is wrong. It draws the box,
 * speaks its riddle, animates for a few seconds, then speaks a string that is
 * a single space — nothing to say, there only to raise the button again. Wipe
 * the box for that and the riddle goes with it, and the puzzle is unreadable.
 */
class WhatStandsInTheBoxTest {

    private val here = Location(1, 1)
    private val riddle = DialogueTextId(55)
    private val aBlankLine = DialogueTextId(20)

    @Test
    fun `a speech leaves the box standing`() {
        val asked = questionsFrom(
            0 to Dialog.DrawDialogBox,
            1 to Dialog.DialogText(riddle, MessageId(10)),
            2 to Dialog.DialogText(aBlankLine, MessageId(11)),
        )

        assertEquals(listOf(riddle, aBlankLine), asked.map { it.textId })
        assertTrue(
            asked.none { it.boxDrawnAgainAfter },
            "a speech asked for the box to be drawn again, which takes the last one with it",
        )
    }

    @Test
    fun `a question takes the box with it`() {
        val asked = questionsFrom(
            0 to Dialog.RunDialog(riddle, MessageId(1), MessageId(2), MessageId(3)),
        )

        assertTrue(
            asked.single().boxDrawnAgainAfter,
            "an answered question left its own words standing under the reply",
        )
    }

    /**
     * The two together, which is the order the mouth runs them in: the box is
     * drawn once, and nothing after it asks for that again.
     */
    @Test
    fun `only drawing the box empties it`() {
        val stage = Remembers()

        runBlocking {
            runner(
                0 to Dialog.DrawDialogBox,
                1 to Dialog.DialogText(riddle, MessageId(10)),
                2 to Dialog.DialogText(aBlankLine, MessageId(11)),
            ).onEvent(triggers, ScriptEvent.PARTY_ENTERED, world, stage)
        }

        assertEquals(
            1,
            stage.spoke.count { it.boxJustDrawn },
            "the box was emptied a different number of times than it was drawn",
        )
        assertFalse(
            stage.asked.any { it.boxDrawnAgainAfter },
            "something after the box was drawn asked for it to be drawn again",
        )
    }

    private fun questionsFrom(vararg script: Pair<Int, ScriptToken>): List<ScriptQuestion> {
        val stage = Remembers()
        runBlocking { runner(*script).onEvent(triggers, ScriptEvent.PARTY_ENTERED, world, stage) }
        return stage.asked
    }

    private fun runner(vararg script: Pair<Int, ScriptToken>) = LevelScriptRunner(
        script.map { (offset, token) -> Script(ScriptOffset(offset), token) },
    )

    private val triggers
        get() = listOf(Trigger(here, TriggerFlags(0x08), Script(ScriptOffset(0), Dialog.DrawDialogBox)))

    private val world = GameState(party = PartyState(position = here, facing = Direction.NORTH))

    private class Remembers : ScriptStage {
        val asked = mutableListOf<ScriptQuestion>()
        val spoke = mutableListOf<ScriptSpeech>()

        override fun notImplemented(what: String) = Unit
        override suspend fun show(world: GameState) = Unit
        override suspend fun hold(ticks: Ticks) = Unit
        override suspend fun play(track: TrackIndex, volume: Volume) = Unit
        override suspend fun opensThePortal() = Unit

        override suspend fun say(speech: ScriptSpeech) {
            spoke += speech
        }

        override suspend fun ask(question: ScriptQuestion): DialogAnswer {
            asked += question
            return DialogAnswer(1)
        }
    }
}
