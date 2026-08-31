package pl.pelotasplus.eyeofbeholder.data

import kotlinx.coroutines.runBlocking
import org.junit.experimental.categories.Category
import pl.pelotasplus.eyeofbeholder.NeedsGameData
import pl.pelotasplus.eyeofbeholder.data.model.DialogueScene
import pl.pelotasplus.eyeofbeholder.data.model.script.Dialog
import pl.pelotasplus.eyeofbeholder.data.repository.CpsRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.DecRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.DialogueTextRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.InfRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.MazRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.PalRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.ResourceRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.VcnRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.VmpRepositoryImpl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A speech never waits on a click that cannot be made.
 *
 * The box holds the words until they are read off, and what reads them off is
 * a button. A speech that names no button is not read off at all — it is left
 * standing and the script goes on, and whatever is said next draws over it.
 *
 * So anything that makes the box wait has to come with something to press.
 * Getting that wrong does not look like a bug in the words: the game simply
 * stops, with the last thing anybody said sitting on screen and no way past
 * it, which is what Khelben's farewell on the ninth floor did.
 *
 * Two things can hold a speech back — a page break written into it, and a box
 * too small for what has been said. This checks the first against every
 * speech in the dungeon; [WhatTheBoxHoldsTest] checks the second.
 */
@Category(NeedsGameData::class)
class NoSpeechWaitsForNothingTest {

    private val resources = ResourceRepositoryImpl()
    private val speeches = DialogueTextRepositoryImpl(resources)

    @Test
    fun `no speech in the dungeon is left waiting to be read by nobody`() {
        val stuck = mutableListOf<String>()
        val buttonless = mutableListOf<String>()

        (1..16).forEach { floor ->
            val inf = runCatching {
                runBlocking {
                    InfRepositoryImpl(
                        resourceRepository = resources,
                        mazRepository = MazRepositoryImpl(resources),
                        vmpRepository = VmpRepositoryImpl(resources),
                        vcnRepository = VcnRepositoryImpl(resources),
                        palRepository = PalRepositoryImpl(resources),
                        cpsRepository = CpsRepositoryImpl(resources),
                        decRepository = DecRepositoryImpl(resources),
                    ).loadInf("LEVEL$floor.INF").getOrThrow()
                }
            }.getOrNull() ?: return@forEach

            inf.script.forEach { line ->
                val token = line.token as? Dialog.DialogText ?: return@forEach

                val label = inf.messages.getOrNull(token.pageBreakLabel.index).orEmpty()
                if (label.isNotBlank()) return@forEach

                val pages = runBlocking { speeches.text(token.textId) }
                    .getOrNull()?.pages ?: return@forEach

                buttonless += "level $floor at ${line.offset.value}"

                // what the box would hold back, given there is nothing to
                // press: it has to be nothing
                val (_, stillToSay) = DialogueScene.whatIsReadFirst(pages, canBeReadOff = false)

                if (stillToSay.isNotEmpty()) {
                    stuck += "level $floor at ${line.offset.value}: " +
                        "${token.textId} holds ${stillToSay.size} pages back with no button"
                }
            }
        }

        assertTrue(
            buttonless.size > 1,
            "no speech without a button was found, so this checked nothing: $buttonless",
        )
        assertEquals(emptyList(), stuck, "a speech would wait to be read off by nobody")
    }
}
