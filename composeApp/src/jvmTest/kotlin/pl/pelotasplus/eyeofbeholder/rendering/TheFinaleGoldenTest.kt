package pl.pelotasplus.eyeofbeholder.rendering

import kotlinx.coroutines.runBlocking
import org.junit.experimental.categories.Category
import pl.pelotasplus.eyeofbeholder.NeedsGameData
import pl.pelotasplus.eyeofbeholder.data.model.Cps
import pl.pelotasplus.eyeofbeholder.data.model.Font
import pl.pelotasplus.eyeofbeholder.data.model.PaletteIndex
import pl.pelotasplus.eyeofbeholder.data.model.sequence.FinaleFrames
import pl.pelotasplus.eyeofbeholder.data.model.sequence.SequenceScreen
import pl.pelotasplus.eyeofbeholder.data.model.sequence.TheFinale
import pl.pelotasplus.eyeofbeholder.data.model.sequence.TheFinaleScript
import pl.pelotasplus.eyeofbeholder.data.model.sequence.TheFinaleScript.Beat
import pl.pelotasplus.eyeofbeholder.data.repository.CpsRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.FontRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.PalRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.ResourceRepositoryImpl
import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The whole ending, one frozen frame per step, in the order it is played.
 *
 * A cut scene cannot be reviewed a piece at a time: a figure that walks in at
 * the wrong height, a mouth pasted where an eye should be, a room that
 * disappears when the sheet under it is swapped — none of those is visible in
 * the code, and none of them is visible in one frame either. They are visible
 * in the run of frames, which is what this writes out.
 *
 * Every beat of [TheFinaleScript] that changes the screen leaves a
 * `finale-NNN.png` behind, numbered in play order so that paging through the
 * directory is watching the scene.
 */
@Category(NeedsGameData::class)
class TheFinaleGoldenTest {

    private val resources = ResourceRepositoryImpl()
    private val cps = CpsRepositoryImpl(resources)
    private val pal = PalRepositoryImpl(resources)

    private val font: Font = runBlocking {
        FontRepositoryImpl(resources).loadFont("FONT8.FNT").getOrThrow()
    }

    private fun sheet(which: Int): Cps = runBlocking {
        cps.loadCps(TheFinale.PICTURES[which]).getOrThrow()
    }

    @Test
    fun `every step of the ending`() {
        val screen = SequenceScreen()
        runBlocking { screen.light(pal.loadPal(TheFinale.COLOURS.first()).getOrThrow()) }

        var step = 0
        var last: BufferedImage? = null

        // A frame that looks exactly like the one before it is not a step. The
        // scene runs a list of moves several times over where the original
        // wants a beat held, and each run ends on the same picture; writing
        // those out would bury the frames that do move.
        fun freeze() {
            val now = screen.toImage()
            if (last?.let { Goldens.countDifferingPixels(it, now) } == 0) return
            last = now
            Goldens.check("finale-${(++step).toString().padStart(3, '0')}", now)
        }

        TheFinaleScript.BEATS.forEach { beat ->
            when (beat) {
                is Beat.Opens -> {
                    screen.load(sheet(beat.scene))
                    FinaleFrames.SHAPES[beat.scene]?.let { screen.cut(it) }
                }

                is Beat.OpensAlongside -> screen.loadAlongside(sheet(beat.scene))

                Beat.KeepsTheView -> screen.keepWhatIsShowing()

                Beat.Shows -> {
                    screen.show()
                    freeze()
                }

                // A frame is taken at every instruction that draws, which is
                // what the animation is: the figure walking in is six of them
                // and the mouth is three.
                is Beat.Moves ->
                    FinaleFrames.MOVES[beat.list].forEach { screen.perform(it) { freeze() } }

                is Beat.Says -> {
                    screen.clearTheStrip()
                    TheFinale.WORDS[beat.line]
                        .split(TheFinale.A_LINE_BREAK)
                        .forEachIndexed { row, line ->
                            screen.write(line, font, PaletteIndex(beat.colour), row)
                        }
                    freeze()
                }

                Beat.Hushes -> {
                    screen.clearTheStrip()
                    freeze()
                }

                // Time passing and noise draw nothing, so there is nothing to
                // freeze. What they do is tested where they are played.
                is Beat.Waits, is Beat.Sounds, Beat.Strikes -> Unit
            }
        }

        assertTrue(step > 100, "the ending came out $step steps long, which is too short to be it")
    }

    private fun SequenceScreen.toImage(): BufferedImage {
        val image =
            BufferedImage(SequenceScreen.WIDTH, SequenceScreen.HEIGHT, BufferedImage.TYPE_INT_ARGB)
        getRows().forEachIndexed { y, row ->
            row.forEachIndexed { x, rgb ->
                image.setRGB(x, y, (0xFF shl 24) or (rgb.red shl 16) or (rgb.green shl 8) or rgb.blue)
            }
        }
        return image
    }
}
