package pl.pelotasplus.eyeofbeholder.data

import kotlinx.coroutines.runBlocking
import org.junit.experimental.categories.Category
import pl.pelotasplus.eyeofbeholder.NeedsGameData
import pl.pelotasplus.eyeofbeholder.data.model.Dice
import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.GameState
import pl.pelotasplus.eyeofbeholder.data.model.LevelScriptRunner
import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.MessageId
import pl.pelotasplus.eyeofbeholder.data.model.PartyState
import pl.pelotasplus.eyeofbeholder.data.model.ScriptEvent
import pl.pelotasplus.eyeofbeholder.data.model.Trigger
import pl.pelotasplus.eyeofbeholder.data.model.TriggerFlags
import pl.pelotasplus.eyeofbeholder.data.model.script.ScriptOffset
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

/**
 * What a script leaves to luck.
 *
 * Level 1's beds are the plainest of them. The script throws one four-sided
 * die and says one of two things:
 *
 * ```
 *  2  if 3 > roll, carry on, else go to 22
 * 14  message 0   "the bed is hard and uncomfortable."
 * 19  go to 27
 * 22  message 1   "this bed looks like someone was recently sleeping in it."
 * 27  return
 * ```
 *
 * Which is to say a throw of 1 or 2 gets the first line and 3 or 4 the
 * second — the operand written last is the left-hand side, so `roll 3 more`
 * asks whether 3 is more than the roll. Every level rolls for something:
 * whether a wall search turns anything up, whether a fighter forces a door.
 */
@Category(NeedsGameData::class)
class DiceInConditionsTest {

    private val level = runBlocking {
        val resources = ResourceRepositoryImpl()
        InfRepositoryImpl(
            resourceRepository = resources,
            mazRepository = MazRepositoryImpl(resources),
            vmpRepository = VmpRepositoryImpl(resources),
            vcnRepository = VcnRepositoryImpl(resources),
            palRepository = PalRepositoryImpl(resources),
            cpsRepository = CpsRepositoryImpl(resources),
            decRepository = DecRepositoryImpl(resources),
        ).loadInf("LEVEL1.INF").getOrThrow()
    }

    private val here = Location(5, 5)

    /** The bed, run as though the party had clicked it where they stand. */
    private fun sleptIn(roll: Int): List<MessageId> {
        val bed = level.script.single { it.offset == ScriptOffset(2) }
        val stage = RecordingStage()

        runBlocking {
            LevelScriptRunner(
                script = level.script,
                level = 1,
                dice = { _, _, _ -> roll },
            ).onEvent(
                triggers = listOf(Trigger(here, TriggerFlags(0x08), bed)),
                event = ScriptEvent.PARTY_ENTERED,
                state = GameState(party = PartyState(here, Direction.NORTH)),
                stage = stage,
            )
        }

        return stage.beats
            .filterIsInstance<RecordingStage.Beat.Said>()
            .flatMap { it.speech.said }
    }

    @Test
    fun `a low throw finds the bed hard`() {
        assertEquals(listOf(MessageId(0)), sleptIn(roll = 1))
        assertEquals(listOf(MessageId(0)), sleptIn(roll = 2))
    }

    @Test
    fun `a high throw finds it slept in`() {
        assertEquals(listOf(MessageId(1)), sleptIn(roll = 3))
        assertEquals(listOf(MessageId(1)), sleptIn(roll = 4))
    }

    /**
     * A throw is a throw of what the script asked for. Reading the record
     * wrongly is not something a level would show — every base in the game is
     * 0 or 1 — so what is asked for is checked here rather than left to a
     * level to give away.
     */
    @Test
    fun `the script's own dice are thrown`() {
        var asked: Triple<Int, Int, Int>? = null

        runBlocking {
            LevelScriptRunner(
                script = level.script,
                level = 1,
                dice = Dice { times, pips, modifier ->
                    asked = Triple(times, pips, modifier)
                    1
                },
            ).onEvent(
                triggers = listOf(
                    Trigger(here, TriggerFlags(0x08), level.script.single { it.offset == ScriptOffset(2) }),
                ),
                event = ScriptEvent.PARTY_ENTERED,
                state = GameState(party = PartyState(here, Direction.NORTH)),
            )
        }

        assertEquals(Triple(1, 4, 0), asked, "one four-sided die, nothing added")
    }
}
