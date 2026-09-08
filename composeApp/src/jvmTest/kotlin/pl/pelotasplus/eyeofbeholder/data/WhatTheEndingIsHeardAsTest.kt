package pl.pelotasplus.eyeofbeholder.data

import kotlinx.coroutines.runBlocking
import org.junit.experimental.categories.Category
import pl.pelotasplus.eyeofbeholder.NeedsGameData
import pl.pelotasplus.eyeofbeholder.data.model.SoundBank
import pl.pelotasplus.eyeofbeholder.data.model.TrackIndex
import pl.pelotasplus.eyeofbeholder.data.model.sequence.FinaleFrames
import pl.pelotasplus.eyeofbeholder.data.model.sequence.SequenceCommand
import pl.pelotasplus.eyeofbeholder.data.model.sequence.TheFinaleScript
import pl.pelotasplus.eyeofbeholder.data.repository.ResourceRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.SoundRepositoryImpl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * The ending's noises, and whether the game has them.
 *
 * The ending is the one scene played on no floor, so it has no floor's bank to
 * take its sounds from and names its own. That is easy to get wrong in a way
 * nothing else notices: a wrong bank is not an error, it is silence.
 */
@Category(NeedsGameData::class)
class WhatTheEndingIsHeardAsTest {

    private val sounds = SoundRepositoryImpl(ResourceRepositoryImpl())

    private fun clip(track: Int) = runBlocking {
        sounds.clip(SoundBank(TheFinaleScript.BANK), TrackIndex(track)).getOrThrow()
    }

    /** The tune it is all played over, which is the first track of its bank. */
    @Test
    fun `the ending has its tune`() {
        assertNotNull(
            clip(TheFinaleScript.THE_TUNE),
            "${TheFinaleScript.BANK} has nothing under track ${TheFinaleScript.THE_TUNE}",
        )
    }

    /**
     * And every noise it makes as it goes — the ones its lists of moves ask
     * for, and the ones the sequence asks for itself.
     *
     * One number in the lists has nothing under it, and that is the bank
     * rather than a mistake: a track that renders to silence is left out
     * instead of being written as a silent file, so a gap here is a gap in the
     * original. It is named so that a second gap appearing would be noticed.
     */
    @Test
    fun `every noise it makes is in the bank`() {
        val asked = buildSet {
            FinaleFrames.MOVES.forEach { moves ->
                moves.filter { it.what == SequenceCommand.Sounds }.forEach { add(it.obj) }
            }
            TheFinaleScript.BEATS
                .filterIsInstance<TheFinaleScript.Beat.Sounds>()
                .forEach { add(it.track) }
        }

        assertEquals(
            listOf(SILENT),
            asked.filter { clip(it) == null }.sorted(),
            "the ending asks for sounds ${TheFinaleScript.BANK} does not have",
        )
    }

    private companion object {
        /** The one the temple falls apart to, which comes out silent. */
        const val SILENT = 15
    }
}
