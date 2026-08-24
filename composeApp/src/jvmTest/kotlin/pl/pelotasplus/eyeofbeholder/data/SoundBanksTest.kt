package pl.pelotasplus.eyeofbeholder.data

import kotlinx.coroutines.runBlocking
import pl.pelotasplus.eyeofbeholder.data.model.SoundBank
import pl.pelotasplus.eyeofbeholder.data.model.TrackIndex
import pl.pelotasplus.eyeofbeholder.data.repository.CpsRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.DcrRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.DecRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.InfRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.ItemsRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.MazRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.PalRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.ResourceRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.SoundRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.VcnRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.VmpRepositoryImpl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * That the rendered clips answer what the game will actually ask them.
 *
 * Nothing here is measured off the renderer. Which banks must exist comes from
 * the levels, which name their own; which tracks must be audible comes from
 * the game, which plays those numbers at named moments — a door
 * being forced, a spell going off, a champion going down. A bank missing a
 * track the game plays is a silence a player would notice and a test would
 * not, unless it is this one.
 */
class SoundBanksTest {

    private val resources = ResourceRepositoryImpl()
    private val sounds = SoundRepositoryImpl(resources)

    private val infRepository = InfRepositoryImpl(
        resourceRepository = resources,
        mazRepository = MazRepositoryImpl(resources),
        vmpRepository = VmpRepositoryImpl(resources),
        vcnRepository = VcnRepositoryImpl(resources),
        palRepository = PalRepositoryImpl(resources),
        cpsRepository = CpsRepositoryImpl(resources),
        decRepository = DecRepositoryImpl(resources),
    )

    /**
     * Effects played from code that can run on any level at all —
     * a blow landing, a door, a spell, a body hitting the floor — and so
     * effects every level's bank has to be able to answer.
     *
     * Track 2 is left out although the game plays it. It is asked for at one
     * place only — coming back from a dialogue to the play field — and it
     * carries no sound in any of the nine level banks, while the intro bank's
     * track 2, which is a different program under the same number, does. A
     * track played on returning from somewhere and heard nowhere is a control
     * track rather than an effect, the way track 0 is; that is a reading of
     * the evidence rather than something the banks state, so it is written
     * here instead of being asserted either way.
     */
    private val playedAnywhere = listOf(
        6, 9, 10, 11, 19, 21, 22, 24, 26, 29, 31, 32, 33,
        79, 86, 90, 95, 98, 100, 104,
    ).map(::TrackIndex)

    /**
     * What the people the party meets sound like, played only when a dialogue
     * with one of them begins. These belong to the levels those people are
     * on: the forest bank leaves 57 undefined outright, because nobody who
     * speaks with that voice is out there.
     */
    private val playedWhereSomeoneSpeaks = listOf(53, 55, 57, 91).map(::TrackIndex)

    @Test
    fun `every bank a level names has clips`() = runBlocking {
        val named = levelBanks()

        assertTrue(named.isNotEmpty(), "the levels should name some banks")

        named.forEach { bank ->
            val clip = sounds.clip(bank, TrackIndex(11)).getOrThrow()
            assertNotNull(clip, "$bank should have track 11, which is an item dropping")
        }
    }

    @Test
    fun `every bank answers the effects any level can call for`() = runBlocking {
        levelBanks().forEach { bank ->
            val missing = playedAnywhere.filter { track ->
                sounds.clip(bank, track).getOrThrow() == null
            }

            assertEquals(emptyList(), missing, "$bank is silent for effects any level plays")
        }
    }

    @Test
    fun `the voices of the people the party meets are somewhere`() = runBlocking {
        val banks = levelBanks()

        playedWhereSomeoneSpeaks.forEach { track ->
            val heardIn = banks.filter { sounds.clip(it, track).getOrThrow() != null }

            assertTrue(heardIn.isNotEmpty(), "no bank has track $track for anyone to speak with")
        }
    }

    @Test
    fun `track 0 is the driver's own silence, not a sound`() = runBlocking {
        assertNull(sounds.clip(SoundBank("AZURE"), TrackIndex(0)).getOrThrow())
    }

    @Test
    fun `a clip is one channel at the rate it was rendered`() = runBlocking {
        val clip = assertNotNull(sounds.clip(SoundBank("AZURE"), TrackIndex(11)).getOrThrow())

        assertEquals(22050, clip.sampleRate)
        assertTrue(clip.samples.isNotEmpty(), "a clip that resolves should have samples")
    }

    /**
     * A door is a door wherever it is heard, so the same track out of two
     * banks is the same recording — which is why there are 181 clips behind
     * 745 numbered tracks, and why this may not quietly stop being true.
     */
    @Test
    fun `banks share the effects they have in common`() = runBlocking {
        val azure = assertNotNull(sounds.clip(SoundBank("AZURE"), TrackIndex(11)).getOrThrow())
        val catacomb = assertNotNull(sounds.clip(SoundBank("CATACOMB"), TrackIndex(11)).getOrThrow())

        assertTrue(
            azure.samples contentEquals catacomb.samples,
            "the same track of two banks should be the same sound",
        )
    }

    private suspend fun levelBanks(): List<SoundBank> =
        (1..16).flatMap { level ->
            infRepository.loadInf("LEVEL$level.INF")
                .getOrNull()
                ?.subLevels
                ?.map { SoundBank(it.sound) }
                .orEmpty()
        }.distinct()
}
