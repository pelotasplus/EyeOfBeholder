package pl.pelotasplus.eyeofbeholder.data

import kotlinx.coroutines.runBlocking
import org.junit.experimental.categories.Category
import pl.pelotasplus.eyeofbeholder.NeedsGameData
import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.GameState
import pl.pelotasplus.eyeofbeholder.data.model.LevelScriptRunner
import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.PartyState
import pl.pelotasplus.eyeofbeholder.data.model.ScriptEvent
import pl.pelotasplus.eyeofbeholder.data.model.Trigger
import pl.pelotasplus.eyeofbeholder.data.model.TriggerFlags
import pl.pelotasplus.eyeofbeholder.data.model.script.Encounter
import pl.pelotasplus.eyeofbeholder.data.model.script.End
import pl.pelotasplus.eyeofbeholder.data.model.script.Script
import pl.pelotasplus.eyeofbeholder.data.model.script.ScriptOffset
import pl.pelotasplus.eyeofbeholder.data.model.script.ScriptToken
import pl.pelotasplus.eyeofbeholder.data.model.script.Wait
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
 * What a set piece does to the screen and to the party before it begins.
 *
 * A set piece is played over the whole picture — the archway a stone gem
 * opens stands across the view — so whatever the player had raised over the
 * play field comes down first: a champion's page, the camp menu, a rest in
 * progress. And the party are the script's until it is done, so they cannot
 * be walked out from under it while it plays.
 *
 * The ordinary run of instructions does none of this. A script that writes a
 * line or opens a door leaves the screen as it found it, and a page can be
 * read through the whole of one.
 */
@Category(NeedsGameData::class)
class ASetPieceTakingTheScreenTest {

    private fun ran(vararg tokens: ScriptToken): RecordingStage {
        val script = tokens.mapIndexed { at, token -> Script(ScriptOffset(at), token) } +
            Script(ScriptOffset(tokens.size), End)
        val stage = RecordingStage()

        runBlocking {
            LevelScriptRunner(script, level = LEVEL).onEvent(
                triggers = listOf(Trigger(SOMEWHERE, TriggerFlags(0x08), script.first())),
                event = ScriptEvent.PARTY_ENTERED,
                state = GameState(party = PartyState(SOMEWHERE, Direction.NORTH)),
                stage = stage,
                at = SOMEWHERE,
            )
        }
        return stage
    }

    /**
     * The screen is cleared before the archway is drawn on it, not after —
     * the two are one picture, and a page still up when it starts is a page
     * the arch is painted over.
     */
    @Test
    fun `the screen is taken before the set piece plays`() {
        assertEquals(
            listOf(RecordingStage.Beat.TookTheScreen, RecordingStage.Beat.OpenedThePortal),
            ran(Encounter.PortalSequence).beats,
        )
    }

    /** And the party with it, before a frame of it has been drawn. */
    @Test
    fun `and the party are taken before it too`() {
        val stage = ran(Encounter.PortalSequence)

        assertEquals(1, stage.tookThePartyAfter, "the party were free while it played")
    }

    /**
     * Every set piece, not only the one that is drawn. One nothing here plays
     * yet still clears the screen, because what makes it a set piece is the
     * instruction rather than what comes of it.
     */
    @Test
    fun `a set piece nothing plays takes the screen all the same`() {
        val stage = ran(Encounter.DeathSequence)

        assertEquals(listOf<RecordingStage.Beat>(RecordingStage.Beat.TookTheScreen), stage.beats)
        assertTrue(stage.tookTheParty)
    }

    /** An ordinary instruction takes neither. */
    @Test
    fun `waiting takes neither the screen nor the party`() {
        val stage = ran(Wait(delay = 2))

        assertFalse(
            stage.beats.contains(RecordingStage.Beat.TookTheScreen),
            "a wait cleared the screen",
        )
        assertFalse(stage.tookTheParty, "a wait took the party")
    }

    /**
     * And the archway is a real thing on a real floor: the fourteenth's niche
     * at 14x15, which answers a stone gem held to it.
     */
    @Test
    fun `the fourteenth floor is where the archway is opened`() {
        val level = runBlocking {
            val resources = ResourceRepositoryImpl()
            InfRepositoryImpl(
                resourceRepository = resources,
                mazRepository = MazRepositoryImpl(resources),
                vmpRepository = VmpRepositoryImpl(resources),
                vcnRepository = VcnRepositoryImpl(resources),
                palRepository = PalRepositoryImpl(resources),
                cpsRepository = CpsRepositoryImpl(resources),
                decRepository = DecRepositoryImpl(resources),
            ).loadInf("LEVEL14.INF").getOrThrow()
        }

        assertTrue(
            level.script.any { it.token == Encounter.PortalSequence },
            "the floor opens no archway",
        )
        assertTrue(
            level.triggers.any { it.location == THE_NICHE },
            "nothing on the floor answers at $THE_NICHE",
        )
    }

    private companion object {
        const val LEVEL = 14

        val SOMEWHERE = Location(14, 14)
        val THE_NICHE = Location(14, 15)
    }
}
