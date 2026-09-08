package pl.pelotasplus.eyeofbeholder.data

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import pl.pelotasplus.eyeofbeholder.data.model.Debugging
import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.PcmClip
import pl.pelotasplus.eyeofbeholder.data.model.Volume
import pl.pelotasplus.eyeofbeholder.data.repository.AudioSink
import pl.pelotasplus.eyeofbeholder.data.repository.CpsRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.DcrRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.DecRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.DialogueTextRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.FontRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.InfRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.ItemTypesRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.ItemsRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.MazRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.NotPlaying
import pl.pelotasplus.eyeofbeholder.data.repository.OriginalSaveRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.PalRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.PlayingSound
import pl.pelotasplus.eyeofbeholder.data.repository.ResourceRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.SaveSlot
import pl.pelotasplus.eyeofbeholder.data.repository.SaveStore
import pl.pelotasplus.eyeofbeholder.data.repository.SavedGameRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.SoundRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.VcnRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.ViewConeRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.VmpRepositoryImpl
import pl.pelotasplus.eyeofbeholder.features.view_cone_debug.ViewConeDebugViewModel

/**
 * A play field to test against: a real view model, on real game data, with
 * nothing coming out of the speakers and nothing written to anybody's saves.
 *
 * The point of testing at this level rather than lower down is what only
 * exists here. A script is set off by the world model but run by the view
 * model, and the wiring between the two has its own faults: an event can be
 * raised and its script never run, and nothing below this can see that.
 */
class ThePlayField {

    /** Every save the field wrote, which is nobody's but this test's. */
    class SavesInMemory : SaveStore {
        private val slots = mutableMapOf<SaveSlot, String>()

        override suspend fun written(): Set<SaveSlot> = slots.keys.toSet()
        override suspend fun read(slot: SaveSlot): String? = slots[slot]
        override suspend fun write(slot: SaveSlot, contents: String) {
            slots[slot] = contents
        }

        override suspend fun erase(slot: SaveSlot) {
            slots.remove(slot)
        }
    }

    /** A speaker that is not there: every sound is asked for and heard by none. */
    class NothingHeard : AudioSink {
        override fun play(clip: PcmClip, volume: Volume, loop: Boolean): PlayingSound =
            NotPlaying

        override fun stopAll() = Unit
    }

    private val resources = ResourceRepositoryImpl()
    private val pal = PalRepositoryImpl(resources)
    private val cps = CpsRepositoryImpl(resources)
    private val maz = MazRepositoryImpl(resources)
    private val vmp = VmpRepositoryImpl(resources)
    private val vcn = VcnRepositoryImpl(resources)
    private val dec = DecRepositoryImpl(resources)
    private val dcr = DcrRepositoryImpl(resources)
    private val inf = InfRepositoryImpl(resources, maz, vmp, vcn, pal, cps, dec)

    val saves = SavesInMemory()

    val viewModel = ViewConeDebugViewModel(
        viewConeRepository = ViewConeRepositoryImpl(inf, cps, dcr),
        cpsRepository = cps,
        dialogueTextRepository = DialogueTextRepositoryImpl(resources),
        fontRepository = FontRepositoryImpl(resources),
        palRepository = pal,
        originalSaveRepository = OriginalSaveRepositoryImpl(resources),
        savedGames = SavedGameRepositoryImpl(saves),
        itemTypesRepository = ItemTypesRepositoryImpl(resources),
        itemsRepository = ItemsRepositoryImpl(resources),
        soundRepository = SoundRepositoryImpl(resources),
        audioSink = NothingHeard(),
        debugging = Debugging(),
    )

    /** Opens a floor with the party standing on [at], and waits for it. */
    suspend fun opened(level: String, at: Location, facing: Direction) {
        viewModel.onEvent(
            ViewConeDebugViewModel.Event.Initialize(
                level = level,
                startX = at.x,
                startY = at.y,
                startDirection = facing,
            ),
        )
        until("$level to open") { viewModel.state.value.inf != null }
    }

    /**
     * Waits for [wanted] to come true, since a view model answers in its own
     * time: the scripts it runs are coroutines, and a test that read the state
     * straight after asking for something would read it before anything had
     * happened.
     *
     * It fails by saying what it was waiting for. A test that simply hung here
     * would say nothing at all about which beat never came.
     */
    suspend fun until(what: String, wanted: () -> Boolean) {
        try {
            withTimeout(A_WHILE) {
                while (!wanted()) delay(A_MOMENT)
            }
        } catch (e: TimeoutCancellationException) {
            throw AssertionError("waited for $what and it never came", e)
        }
    }

    private companion object {
        const val A_WHILE = 20_000L
        const val A_MOMENT = 10L
    }
}
