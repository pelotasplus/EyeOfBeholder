package pl.pelotasplus.eyeofbeholder.data

import kotlinx.coroutines.runBlocking
import org.junit.experimental.categories.Category
import pl.pelotasplus.eyeofbeholder.NeedsGameData
import pl.pelotasplus.eyeofbeholder.data.model.DialogueBox
import pl.pelotasplus.eyeofbeholder.data.model.DialogueScene
import pl.pelotasplus.eyeofbeholder.data.model.DialogueTextId
import pl.pelotasplus.eyeofbeholder.data.model.Font
import pl.pelotasplus.eyeofbeholder.data.model.Inf
import pl.pelotasplus.eyeofbeholder.data.model.script.Dialog
import pl.pelotasplus.eyeofbeholder.data.model.script.Message
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
 * Khelben's farewell on the ninth floor, screen by screen.
 *
 * This is the scene that showed its end, then its beginning, then its end
 * again, and it is written in the one shape that catches that: the box is
 * drawn once and three long speeches go into it with nothing but blank
 * speeches between them, so every mistake about what the box remembers shows
 * up here as words in the wrong order.
 *
 * ```
 * 3343  draw the dialogue box        the last time it is emptied
 * 3345  speak text 36
 * 3373  speak text 100
 * 3385  speak text 20                blank; it only raises the button
 * 3391  speak text 101
 * 3408  speak text 20
 * 3414  close the dialogue
 * ```
 *
 * The screens are not written down here — the wrap depends on the font and
 * would be a photograph of whatever the code does. What is written down is
 * what has to be true of them whatever they come out as: every line of the
 * conversation is seen, each is seen once, and they are seen in the order
 * they were said. That is the whole of what went wrong.
 */
@Category(NeedsGameData::class)
class KhelbensFarewellTest {

    private val resources = ResourceRepositoryImpl()
    private val speeches = DialogueTextRepositoryImpl(resources)

    private val level: Inf = runBlocking {
        InfRepositoryImpl(
            resourceRepository = resources,
            mazRepository = MazRepositoryImpl(resources),
            vmpRepository = VmpRepositoryImpl(resources),
            vcnRepository = VcnRepositoryImpl(resources),
            palRepository = PalRepositoryImpl(resources),
            cpsRepository = CpsRepositoryImpl(resources),
            decRepository = DecRepositoryImpl(resources),
        ).loadInf("LEVEL9.INF").getOrThrow()
    }

    private val font = Font(width = 6, height = 8, glyphs = emptyList())
    private val strip = DialogueScene.ReadOff.TheStripBelow

    /**
     * The scene played out, in screens.
     *
     * A speech names the words on its button; where it names none there is
     * nothing to press, and the box does not stop. Turning a page is a
     * screen of its own, which is what the reader presses MORE for.
     */
    private fun screensFrom(first: Int, last: Int): List<String> {
        val box = DialogueBox(font, strip)
        val screens = mutableListOf<String>()

        level.script
            .filter { it.offset.value in first..last }
            .forEach { line ->
                when (val token = line.token) {
                    Dialog.DrawDialogBox -> box.drawnAgain()

                    is Message -> {
                        val words = level.messages.getOrNull(token.messageId.index).orEmpty()
                        screens += box.said(words, canBeReadOff = false).onScreen
                    }

                    is Dialog.DialogText -> {
                        val words = runBlocking { speeches.text(token.textId) }
                            .getOrNull()?.pages?.joinToString("\n").orEmpty()
                        val button = level.messages
                            .getOrNull(token.pageBreakLabel.index).orEmpty()

                        var shown = box.said(words, canBeReadOff = button.isNotBlank())
                        screens += shown.onScreen

                        // and the reader presses through whatever is behind it
                        while (shown.stillToRead.isNotEmpty()) {
                            val page = shown.stillToRead.first()
                            box.turnedTo(page)
                            screens += page
                            shown = DialogueBox.Shown(page, shown.stillToRead.drop(1))
                        }
                    }

                    else -> Unit
                }
            }

        return screens
    }

    /** The whole scene from the last time the box is emptied to the close. */
    private val screens get() = screensFrom(first = 3343, last = 3414)

    private fun linesOf(screen: String) = font.wrap(screen, strip.textWidth)

    @Test
    fun `the scene is played out over more than one screen`() {
        assertTrue(screens.size > 1, "the whole farewell came out on one screen: ${screens.size}")
    }

    /**
     * The one that failed. A line once read is never shown again as
     * something new — the box moves forward and does not go back.
     */
    @Test
    fun `no screen goes back over what has been read`() {
        // where each screen opens within the conversation as a whole
        val whole = wholeConversation()

        // a blank speech says nothing and leaves the screen as it was, which
        // is not the box going anywhere
        val moved = screens.filterIndexed { at, screen -> at == 0 || screen != screens[at - 1] }
        val opensAt = moved.map { whole.indexOf(linesOf(it).first()) }

        assertTrue(opensAt.none { it < 0 }, "a screen opened on words nobody said: $opensAt")
        assertEquals(
            opensAt.distinct(),
            opensAt,
            "two screens opened at the same place, so the box went back: $opensAt",
        )
        assertEquals(
            opensAt.sorted(),
            opensAt,
            "a screen went back to words already read: $opensAt",
        )
    }

    /** Every line of the conversation, in the order the script says it. */
    private fun wholeConversation(): List<String> = runBlocking {
        listOf(36, 100, 101).flatMap { number ->
            speeches.text(DialogueTextId(number)).getOrNull()?.pages.orEmpty()
        }
    }.flatMap { font.wrap(it, strip.textWidth) }

    @Test
    fun `every screen is a boxful or less`() {
        val tooDeep = screens.filter { linesOf(it).size > strip.linesThatFit(font) }

        assertEquals(emptyList(), tooDeep, "a screen was written past the bottom of the box")
    }

    /**
     * Nothing said is lost between the screens. Every line of every speech
     * the script runs is on one of them.
     */
    @Test
    fun `nothing said goes unseen`() {
        val shown = screens.flatMap { linesOf(it) }.toSet()

        val said = runBlocking {
            listOf(36, 100, 101).flatMap { number ->
                speeches.text(DialogueTextId(number)).getOrNull()?.pages.orEmpty()
            }
        }.flatMap { font.wrap(it, strip.textWidth) }

        assertEquals(
            emptyList(),
            said.filterNot { it in shown },
            "words the script said never reached a screen",
        )
    }

    /** And the order they are seen in is the order they were said in. */
    @Test
    fun `they are seen in the order they were said`() {
        val shown = screens.flatMap { linesOf(it) }

        val opens = runBlocking {
            listOf(36, 100, 101).map { number ->
                speeches.text(DialogueTextId(number)).getOrNull()?.pages?.first().orEmpty()
            }
        }.map { font.wrap(it, strip.textWidth).first() }

        val whereEachStarts = opens.map { shown.indexOf(it) }

        assertEquals(
            whereEachStarts.sorted(),
            whereEachStarts,
            "the speeches reached the screen out of order: $whereEachStarts",
        )
        assertTrue(whereEachStarts.none { it < 0 }, "a speech never opened on any screen")
    }

    /** Drawing the box again is the one thing that wipes it. */
    @Test
    fun `drawing the box again starts a fresh screen`() {
        val box = DialogueBox(font, strip)

        box.said("something already said", canBeReadOff = false)
        box.drawnAgain()

        assertEquals(
            "after",
            box.said("after", canBeReadOff = false).onScreen,
            "the box kept what was said before it was drawn again",
        )
    }
}
