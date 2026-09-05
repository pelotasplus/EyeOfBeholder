package pl.pelotasplus.eyeofbeholder.data

import kotlinx.coroutines.runBlocking
import pl.pelotasplus.eyeofbeholder.data.model.Champion
import pl.pelotasplus.eyeofbeholder.data.model.CharacterClass
import pl.pelotasplus.eyeofbeholder.data.model.ChampionFlags
import pl.pelotasplus.eyeofbeholder.data.model.ClassLevel
import pl.pelotasplus.eyeofbeholder.data.model.DialogAnswer
import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.GameState
import pl.pelotasplus.eyeofbeholder.data.model.HitPoints
import pl.pelotasplus.eyeofbeholder.data.model.LevelScriptRunner
import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.PartyState
import pl.pelotasplus.eyeofbeholder.data.model.ScriptEvent
import pl.pelotasplus.eyeofbeholder.data.model.ScriptQuestion
import pl.pelotasplus.eyeofbeholder.data.model.ScriptStage
import pl.pelotasplus.eyeofbeholder.data.model.Trigger
import pl.pelotasplus.eyeofbeholder.data.model.TriggerFlags
import pl.pelotasplus.eyeofbeholder.data.model.XpPoints
import pl.pelotasplus.eyeofbeholder.data.model.progression
import pl.pelotasplus.eyeofbeholder.data.model.script.Conditional
import pl.pelotasplus.eyeofbeholder.data.model.script.Eval
import pl.pelotasplus.eyeofbeholder.data.model.script.Script
import pl.pelotasplus.eyeofbeholder.data.model.script.ScriptOffset
import pl.pelotasplus.eyeofbeholder.data.model.script.SpecialEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Two set pieces the eleventh floor asks for and nothing answered.
 *
 * Alain, bound to his dais, asks the party to touch him: the script speaks,
 * then asks which of them will do it, then thanks whoever did with a level.
 * Both of those are the same opcode with a different number, and neither was
 * written, so the scene ran to its end having asked nobody anything and given
 * nobody anything.
 *
 * A refusal is not silence: the script reads 99 back and has a branch for it.
 */
class TouchingAlainTest {

    private val here = Location(10, 29)

    private fun someone(name: String, cls: CharacterClass, level: Int) = Champion.NOBODY.copy(
        name = name,
        flags = ChampionFlags(IN_THE_PARTY),
        hitPoints = HitPoints(30, 30),
        characterClass = cls,
        levels = listOf(ClassLevel(level, XpPoints(0))),
    )

    private val party = listOf(
        someone("One", CharacterClass.FIGHTER, level = 3),
        someone("Two", CharacterClass.CLERIC, level = 2),
    )

    private fun world(champions: List<Champion> = party) = GameState(
        party = PartyState(here, Direction.SOUTH),
        champions = champions,
    )

    /** Answers every question with the same button. */
    private class Picks(private val button: Int) : ScriptStage {
        val asked = mutableListOf<ScriptQuestion>()

        override fun notImplemented(what: String) = Unit
        override suspend fun show(world: GameState) = Unit
        override suspend fun say(speech: pl.pelotasplus.eyeofbeholder.data.model.ScriptSpeech) = Unit
        override suspend fun hold(ticks: pl.pelotasplus.eyeofbeholder.data.model.Ticks) = Unit
        override suspend fun play(
            track: pl.pelotasplus.eyeofbeholder.data.model.TrackIndex,
            volume: pl.pelotasplus.eyeofbeholder.data.model.Volume,
        ) = Unit
        override suspend fun opensThePortal() = Unit

        override suspend fun ask(question: ScriptQuestion): DialogAnswer {
            asked += question
            return DialogAnswer(button)
        }
    }

    private fun run(
        vararg script: Pair<Int, pl.pelotasplus.eyeofbeholder.data.model.script.ScriptToken>,
        stage: ScriptStage,
        world: GameState = world(),
    ): GameState = runBlocking {
        val instructions = script.map { (at, token) -> Script(ScriptOffset(at), token) }
        LevelScriptRunner(instructions, level = 11).onEvent(
            triggers = listOf(Trigger(here, TriggerFlags(0x08), instructions.first())),
            event = ScriptEvent.PARTY_ENTERED,
            state = world,
            stage = stage,
            at = here,
        ).state
    }

    // --- who is asked ---------------------------------------------------------

    @Test
    fun `it offers everyone who could act, and a way out`() {
        val picks = Picks(button = 1)
        run(0 to SpecialEvent.CharSelectDialogue, stage = picks)

        assertEquals(
            listOf("one", "two", "abort"),
            picks.asked.single().words.map { it.lowercase() },
            "the names offered were not the party's, or nobody could refuse",
        )
    }

    @Test
    fun `the stone and the past-raising are not asked`() {
        val picks = Picks(button = 1)
        run(
            0 to SpecialEvent.CharSelectDialogue,
            stage = picks,
            world = world(
                party + someone("Gone", CharacterClass.FIGHTER, 1)
                    .copy(hitPoints = HitPoints(Champion.BEYOND_RAISING, 30)),
            ),
        )

        assertTrue(
            "Gone" !in picks.asked.single().words,
            "somebody past raising was asked to lay a hand on something",
        )
    }

    /** The last of the choices is the refusal, and it reads back as 99. */
    @Test
    fun `refusing reads back as nobody`() {
        val picks = Picks(button = 3)

        val said = run(
            0 to SpecialEvent.CharSelectDialogue,
            10 to Eval(
                listOf(
                    Conditional.DialogResult,
                    Conditional.ImmediateShort(NOBODY_CHOSE),
                    Conditional.Equals,
                ),
                goto = ScriptOffset(30),
            ),
            20 to SpecialEvent.CharacterLevelGain,
            stage = picks,
        )

        assertEquals(
            party.map { it.levels.first().level },
            said.champions.map { it.levels.first().level },
            "nobody was chosen and somebody was given a level anyway",
        )
    }

    // --- and what the thanks are worth ---------------------------------------

    @Test
    fun `whoever was chosen gains a level`() {
        val given = run(
            0 to SpecialEvent.CharSelectDialogue,
            10 to SpecialEvent.CharacterLevelGain,
            stage = Picks(button = 2),
        )

        assertEquals(
            listOf(3, 3),
            given.champions.map { it.levels.first().level },
            "the second champion was thanked and did not gain the level",
        )
    }

    @Test
    fun `and nobody else does`() {
        val given = run(
            0 to SpecialEvent.CharSelectDialogue,
            10 to SpecialEvent.CharacterLevelGain,
            stage = Picks(button = 1),
        )

        assertEquals(
            listOf(4, 2),
            given.champions.map { it.levels.first().level },
            "the level went to the wrong champion, or to more than one",
        )
    }

    /** Enough experience for the next level and no more than that. */
    @Test
    fun `it is exactly the next level that is bought`() {
        val given = run(
            0 to SpecialEvent.CharSelectDialogue,
            10 to SpecialEvent.CharacterLevelGain,
            stage = Picks(button = 1),
        )

        val grown = given.champions.first()
        val needed = CharacterClass.FIGHTER.progression.neededFor(grown.levels.first().level + 1)

        assertTrue(
            needed == null || grown.levels.first().experience < needed,
            "they were given enough for the level after the one they were promised",
        )
    }

    private companion object {
        const val IN_THE_PARTY = 0x01

        /** What a script reads back when the party named nobody. Transcribed. */
        const val NOBODY_CHOSE = 99
    }
}
