package pl.pelotasplus.eyeofbeholder.data

import kotlinx.coroutines.runBlocking
import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.GameState
import pl.pelotasplus.eyeofbeholder.data.model.LevelScriptRunner
import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.PartyState
import pl.pelotasplus.eyeofbeholder.data.model.ScriptEvent
import pl.pelotasplus.eyeofbeholder.data.model.TrackIndex
import pl.pelotasplus.eyeofbeholder.data.model.Trigger
import pl.pelotasplus.eyeofbeholder.data.model.TriggerFlags
import pl.pelotasplus.eyeofbeholder.data.model.Volume
import pl.pelotasplus.eyeofbeholder.data.model.script.End
import pl.pelotasplus.eyeofbeholder.data.model.script.Script
import pl.pelotasplus.eyeofbeholder.data.model.script.ScriptOffset
import pl.pelotasplus.eyeofbeholder.data.model.script.ScriptToken
import pl.pelotasplus.eyeofbeholder.data.model.script.Sound
import pl.pelotasplus.eyeofbeholder.data.model.script.Teleport
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A script names the square its sound comes from, and that square is what
 * decides how loud the party hear it — or whether they hear it at all.
 *
 * The rule is the engine's rather than ours: the distance is the whole of the
 * larger gap plus half the smaller, a sixteenth of full volume comes off for
 * each square of it, and fifteen squares is where it runs out. So a floor's
 * far end can grind a wall open without the party being told about it.
 *
 * [HowFarSoundCarriesTest] pins the volume curve itself; this pins that a
 * script's sound is put through it, and from where.
 */
class HowFarAScriptsSoundCarriesTest {

    private val partyStandOn = Location(7, 16)

    @Test
    fun `a sound on the party's own square is as loud as a sound gets`() {
        assertEquals(listOf(Volume.asFarOffAs(0)), heardFrom(Sound(17, partyStandOn)))
    }

    @Test
    fun `and one square along is a sixteenth quieter`() {
        assertEquals(listOf(Volume.asFarOffAs(1)), heardFrom(Sound(17, Location(8, 16))))
    }

    /**
     * Four squares along and four down is six away, not eight: the shorter way
     * is halved. Counting both in full would put this past the diagonal it is
     * on and quieten it by two squares too many.
     */
    @Test
    fun `a sound on the diagonal counts the shorter way as half`() {
        assertEquals(listOf(Volume.asFarOffAs(6)), heardFrom(Sound(17, Location(11, 20))))
    }

    /**
     * The case that started this: a clock on level 1 grinds a wall open at
     * 22x18 while the party stand at 7x16. Fifteen across and two down is
     * sixteen away, and nothing is heard.
     */
    @Test
    fun `a wall opening at the far end of the floor is not heard`() {
        assertEquals(listOf(Volume.SILENT), heardFrom(Sound(17, Location(22, 18))))
    }

    /**
     * Fifteen is already silence rather than the last audible step, so the
     * last square anything reaches from is fourteen away.
     */
    @Test
    fun `fourteen squares off is the last that is heard at all`() {
        assertEquals(listOf(Volume.asFarOffAs(14)), heardFrom(Sound(17, Location(21, 16))))
        assertEquals(listOf(Volume.SILENT), heardFrom(Sound(17, Location(22, 16))))
    }

    /**
     * A script that names no square means a sound the party carry with them —
     * a door they are working, a voice in their heads — rather than one coming
     * from a place, and that is heard in full wherever they stand.
     */
    @Test
    fun `a sound with no square at all is heard in full`() {
        assertEquals(listOf(Volume.FULL), heardFrom(Sound(17, Location(0, 0))))
    }

    /**
     * The party's square is read when the sound is made, not when the script
     * started. A script that walks them across the floor first is heard from
     * where it left them — which is the difference between a slam behind them
     * and one they are standing in.
     */
    @Test
    fun `a script that moves the party first is heard from where it put them`() {
        val far = Sound(17, Location(22, 18))

        assertEquals(
            listOf(Volume.SILENT, Volume.asFarOffAs(1)),
            heardFrom(
                far,
                Teleport.MoveParty(source = Location(0, 0), destination = Location(21, 18)),
                far,
            ),
        )
    }

    @Test
    fun `every sound the script makes is still the sound it asked for`() {
        assertEquals(
            listOf(TrackIndex(17), TrackIndex(3)),
            played(Sound(17, partyStandOn), Sound(3, Location(8, 16))),
        )
    }

    /** How loud each sound of [script] is, with the party on [partyStandOn]. */
    private fun heardFrom(vararg script: ScriptToken): List<Volume> =
        run(*script).map { it.volume }

    private fun played(vararg script: ScriptToken): List<TrackIndex> =
        run(*script).map { it.track }

    private fun run(vararg script: ScriptToken): List<RecordingStage.Beat.Played> = runBlocking {
        val instructions = (script.toList() + End)
            .mapIndexed { offset, token -> Script(ScriptOffset(offset), token) }
        val stage = RecordingStage()

        LevelScriptRunner(instructions).onEvent(
            triggers = listOf(Trigger(partyStandOn, TriggerFlags(0x08), instructions.first())),
            event = ScriptEvent.PARTY_ENTERED,
            state = GameState(PartyState(position = partyStandOn, facing = Direction.NORTH)),
            stage = stage,
        )

        stage.beats.filterIsInstance<RecordingStage.Beat.Played>()
    }
}
