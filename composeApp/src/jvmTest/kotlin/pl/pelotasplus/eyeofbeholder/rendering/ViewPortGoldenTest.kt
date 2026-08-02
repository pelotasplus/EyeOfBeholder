package pl.pelotasplus.eyeofbeholder.rendering

import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import kotlinx.coroutines.runBlocking
import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.GameState
import pl.pelotasplus.eyeofbeholder.data.model.LevelScriptRunner
import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.MessageId
import pl.pelotasplus.eyeofbeholder.data.model.PaletteIndex
import pl.pelotasplus.eyeofbeholder.data.model.PartyState
import pl.pelotasplus.eyeofbeholder.data.RecordingStage
import pl.pelotasplus.eyeofbeholder.data.model.ScriptEvent
import pl.pelotasplus.eyeofbeholder.data.model.ViewPort
import pl.pelotasplus.eyeofbeholder.data.model.toImageBitmap
import pl.pelotasplus.eyeofbeholder.data.model.DialogueScene
import pl.pelotasplus.eyeofbeholder.data.model.DialogueTextId
import pl.pelotasplus.eyeofbeholder.data.model.PlayField
import pl.pelotasplus.eyeofbeholder.data.repository.CpsRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.DialogueTextRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.FontRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.DcrRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.DecRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.InfRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.ItemsRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.MazRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.PalRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.ResourceRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.VcnRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.ViewConeRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.VmpRepositoryImpl
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.fail

/**
 * Golden-image tests for the 3D viewport renderer.
 *
 * Each test renders a known player position using the real game assets and
 * compares the resulting 176×120 frame against a reference PNG checked in
 * under src/jvmTest/goldens/. Any pixel difference fails the test and writes
 * the actual frame plus a diff mask to build/golden-failures/ for inspection.
 *
 * To accept an intentional rendering change (or bootstrap missing goldens):
 *   UPDATE_GOLDENS=1 ./gradlew :composeApp:jvmTest
 */
class ViewPortGoldenTest {

    @Test
    fun `level7 start position`() =
        checkGolden("level7-start", "LEVEL7.INF", x = 29, y = 15, direction = Direction.SOUTH)

    @Test
    fun `level7 silver tower entrance`() =
        checkGolden("level7-silver-tower", "LEVEL7.INF", x = 15, y = 6, direction = Direction.EAST)

    @Test
    fun `level6 temple`() =
        checkGolden("level6-temple", "LEVEL6.INF", x = 27, y = 29, direction = Direction.NORTH)

    @Test
    fun `level1 stairs down`() =
        checkGolden("level1-stairs", "LEVEL1.INF", x = 10, y = 12, direction = Direction.SOUTH)

    @Test
    fun `level1 door with button up close`() =
        checkGolden("level1-door", "LEVEL1.INF", x = 9, y = 13, direction = Direction.WEST)

    @Test
    fun `level1 four guards two rows ahead`() =
        checkGolden("level1-guards", "LEVEL1.INF", x = 10, y = 18, direction = Direction.SOUTH)

    /** The pair that speak on level 5, seen from the square they speak from. */
    @Test
    fun `level5 encounter one square ahead`() =
        checkGolden("level5-encounter", "LEVEL5.INF", x = 13, y = 9, direction = Direction.NORTH)

    /** The same pair on the diagonal square, walking away to the left. */
    @Test
    fun `level5 encounter on the left diagonal`() =
        checkGolden("level5-encounter-diagonal", "LEVEL5.INF", x = 12, y = 9, direction = Direction.EAST)

    /** The pair straight ahead but side-on, one cleric overlapping the other. */
    @Test
    fun `level5 encounter side on`() =
        checkGolden("level5-encounter-side", "LEVEL5.INF", x = 12, y = 8, direction = Direction.EAST)

    /** The same side pose mirrored, with the near wall cutting the pair in half. */
    @Test
    fun `level5 encounter side on mirrored`() =
        checkGolden("level5-encounter-mirrored", "LEVEL5.INF", x = 14, y = 9, direction = Direction.WEST)

    /** The pair three rows back, shrunk twice. */
    @Test
    fun `level5 encounter three rows ahead`() =
        checkGolden("level5-encounter-far", "LEVEL5.INF", x = 13, y = 11, direction = Direction.NORTH)

    /**
     * The priest level 6 conjures behind the party as they walk east, seen
     * after its script spins them round to face it.
     */
    @Test
    fun `level6 priest blocks the way back`() =
        checkGolden(
            "level6-priest",
            renderAfterStepping("LEVEL6.INF", number = 6, x = 10, y = 2, direction = Direction.EAST),
        )

    /**
     * Where the woman by level 4's temple door leaves the party after walking
     * them up the corridor: four squares north of where they were, which the
     * script draws one step at a time.
     */
    @Test
    fun `level4 escorted to the temple door`() =
        checkGolden(
            "level4-escort",
            renderAfterStepping(
                "LEVEL4.INF",
                number = 4,
                x = 12,
                y = 11,
                direction = Direction.NORTH,
                answers = listOf(1, 1),
                frame = LAST_FRAME,
            ),
        )

    @Test
    fun `level7 sword at the party's feet`() =
        checkGolden("level7-sword-at-feet", "LEVEL7.INF", x = 29, y = 16, direction = Direction.SOUTH)

    /**
     * An item on the front-left diagonal square draws partly on top of the wall
     * that should hide it. Frozen while broken so the fix shows up as a diff.
     */
    @Test
    fun `level4 item behind the wall to the left`() =
        checkGolden("level4-item-behind-wall", "LEVEL4.INF", x = 12, y = 5, direction = Direction.SOUTH)

    /** A dagger on a square whose tree trunk stands between it and the party. */
    @Test
    fun `level4 dagger behind the middle tree`() =
        checkGolden("level4-dagger-behind-tree", "LEVEL4.INF", x = 16, y = 12, direction = Direction.SOUTH)

    /** The same dagger one square further away. */
    @Test
    fun `level4 dagger behind the middle tree from further back`() =
        checkGolden("level4-dagger-behind-tree-far", "LEVEL4.INF", x = 16, y = 11, direction = Direction.SOUTH)

    /** The scene the sprite-versus-wall work is being fixed against. */
    @Test
    fun `level4 item against the wall facing west`() =
        checkGolden("level4-item-west", "LEVEL4.INF", x = 18, y = 14, direction = Direction.WEST)

    @Test
    fun `toImageBitmap matches the raw pixel buffer`() {
        val viewPort = renderFrame("LEVEL7.INF", x = 29, y = 15, direction = Direction.SOUTH)
        val fromBuffer = viewPort.toImage()
        val fromBitmap = viewPort.toImageBitmap().toPixelMap()

        var differing = 0
        for (y in 0 until ViewPort.ROWS) {
            for (x in 0 until ViewPort.COLS) {
                val expected = fromBuffer.getRGB(x, y)
                val actual = fromBitmap[x, y].toArgb()
                // compare only visible pixels; both encode transparent as alpha 0
                val same = if ((expected ushr 24) == 0) (actual ushr 24) == 0 else expected == actual
                if (!same) differing++
            }
        }
        if (differing > 0) {
            fail("toImageBitmap differs from the pixel buffer at $differing pixels")
        }
    }

    private fun checkGolden(name: String, level: String, x: Int, y: Int, direction: Direction) =
        checkGolden(name, renderFrame(level, x, y, direction))

    private fun checkGolden(name: String, viewPort: ViewPort) =
        checkGolden(name, viewPort.toImage())

    private fun checkGolden(name: String, actual: BufferedImage) {
        val goldenFile = goldensDir.resolve("$name.png")

        if (updateGoldens) {
            goldenFile.parentFile.mkdirs()
            ImageIO.write(actual, "png", goldenFile)
            println("Updated golden ${goldenFile.absolutePath}")
            return
        }

        if (!goldenFile.exists()) {
            val candidate = failuresDir.resolve("$name-candidate.png")
            candidate.parentFile.mkdirs()
            ImageIO.write(actual, "png", candidate)
            fail(
                "Missing golden ${goldenFile.absolutePath}\n" +
                        "Review ${candidate.absolutePath} and run UPDATE_GOLDENS=1 ./gradlew :composeApp:jvmTest to accept it."
            )
        }

        val golden = ImageIO.read(goldenFile)
        val differing = countDifferingPixels(golden, actual)
        if (differing > 0) {
            failuresDir.mkdirs()
            val actualFile = failuresDir.resolve("$name-actual.png")
            val diffFile = failuresDir.resolve("$name-diff.png")
            ImageIO.write(actual, "png", actualFile)
            ImageIO.write(diffImage(golden, actual), "png", diffFile)
            fail(
                "$name: $differing of ${actual.width * actual.height} pixels differ from golden.\n" +
                        "  actual: ${actualFile.absolutePath}\n" +
                        "  diff:   ${diffFile.absolutePath}\n" +
                        "  If the change is intentional: UPDATE_GOLDENS=1 ./gradlew :composeApp:jvmTest"
            )
        }
    }

    /**
     * The two places a script can put a picture: a speaker framed at the top
     * left with the speech beside and below, and a plate across the whole
     * width, which is drawn instead of the frame rather than inside it.
     */
    @Test
    fun `dialogue with a speaker in the frame`() =
        checkGolden(
            "dialogue-speaker",
            dialogueOver(
                level = "LEVEL6.INF", x = 10, y = 2,
                picture = "SOUT2.CPS", sourceLeft = 160, sourceTop = 0,
                goes = DialogueScene.PictureFrame.SPEAKER,
                textId = 28, buttons = listOf("leave", "attack"),
            ),
        )

    @Test
    fun `dialogue with a picture across the top`() =
        checkGolden(
            "dialogue-across-the-top",
            dialogueOver(
                level = "LEVEL4.INF", x = 15, y = 10,
                picture = "DARKMOON.CPS", sourceLeft = 0, sourceTop = 0,
                goes = DialogueScene.PictureFrame.ACROSS_THE_TOP,
                textId = 18, buttons = listOf("yes", "no"),
            ),
        )

    /** A script writing a line and holding the screen, with nothing to click. */
    @Test
    fun `dialogue with a line and nothing to click`() =
        checkGolden(
            "dialogue-said",
            dialogueOver(
                level = "LEVEL6.INF", x = 10, y = 2,
                picture = "SOUT2.CPS", sourceLeft = 160, sourceTop = 0,
                goes = DialogueScene.PictureFrame.SPEAKER,
                message = 5,
            ),
        )

    /**
     * A line written with no box open goes on the bar beside the camp button,
     * in the ink the script asked for — "going down..." is written in 5.
     */
    @Test
    fun `a message on the bar along the bottom`() =
        checkGolden(
            "message-bar",
            messagesOver(level = "LEVEL6.INF", x = 10, y = 3, messages = listOf(1 to 5)),
        )

    /** Three short lines fill the bar, each keeping the ink it was written in. */
    @Test
    fun `three messages stack up the bar`() =
        checkGolden(
            "message-bar-three",
            messagesOver(
                level = "LEVEL4.INF",
                x = 15,
                y = 11,
                messages = listOf(0 to 5, 1 to 15, 2 to 9),
            ),
        )

    /**
     * The bar is 18 pixels of a 6 pixel font, so a long line fills three of
     * them and whatever will not fit is dropped.
     */
    @Test
    fun `a message long enough to fill the bar`() =
        checkGolden(
            "message-bar-full",
            messagesOver(level = "LEVEL4.INF", x = 15, y = 11, messages = listOf(11 to 9)),
        )

    /** @param messages the level's own message ids, each with the ink to write it in. */
    private fun messagesOver(
        level: String,
        x: Int,
        y: Int,
        messages: List<Pair<Int, Int>>,
    ): BufferedImage =
        runBlocking {
            val resources = ResourceRepositoryImpl()
            val cps = CpsRepositoryImpl(resources)
            val repository = repository()
            val inf = repository.loadLevel(level).getOrThrow()
            val sublevel = inf.subLevels[0]

            val viewPort = repository.renderPosition(
                items = inf.items,
                monsters = inf.monsterInstances,
                sublevel = sublevel,
                playerX = x,
                playerY = y,
                direction = Direction.NORTH,
            ).getOrThrow()

            PlayField(
                background = cps.loadCps("PLAYFLD.CPS").getOrThrow(),
                decorations = cps.loadCps("DECORATE.CPS").getOrThrow(),
                palette = sublevel.palette,
                font = FontRepositoryImpl(resources).loadFont("FONT6.FNT").getOrThrow(),
            ).render(
                viewPort = viewPort,
                direction = Direction.NORTH,
                messages = messages.mapNotNull { (id, ink) ->
                    inf.message(MessageId(id))?.let { PlayField.Message(it, PaletteIndex(ink)) }
                },
            ).toImage()
        }

    private fun dialogueOver(
        level: String,
        x: Int,
        y: Int,
        picture: String,
        sourceLeft: Int,
        sourceTop: Int,
        goes: DialogueScene.PictureFrame,
        textId: Int? = null,
        message: Int? = null,
        buttons: List<String> = emptyList(),
    ): BufferedImage = runBlocking {
        val resources = ResourceRepositoryImpl()
        val cps = CpsRepositoryImpl(resources)
        val repository = repository()
        val inf = repository.loadLevel(level).getOrThrow()
        val sublevel = inf.subLevels[0]

        val viewPort = repository.renderPosition(
            items = inf.items,
            monsters = inf.monsterInstances,
            sublevel = sublevel,
            playerX = x,
            playerY = y,
            direction = Direction.NORTH,
        ).getOrThrow()

        val font = FontRepositoryImpl(resources).loadFont("FONT6.FNT").getOrThrow()

        // a question's words come from the shared text file; a line a script
        // writes into the box is one of the level's own messages
        val text = textId
            ?.let { DialogueTextRepositoryImpl(resources).text(DialogueTextId(it)).getOrThrow().first }
            ?: message?.let { inf.message(MessageId(it)) }
            ?: ""

        PlayField(
            background = cps.loadCps("PLAYFLD.CPS").getOrThrow(),
            decorations = cps.loadCps("DECORATE.CPS").getOrThrow(),
            palette = sublevel.palette,
            font = font,
        ).render(
            viewPort = viewPort,
            direction = Direction.NORTH,
            dialogue = DialogueScene.layout(
                frame = cps.loadCps("BORDER.CPS").getOrThrow()
                    .takeUnless { goes.insteadOfTheFrame },
                portrait = DialogueScene.Picture(
                    cps = cps.loadCps(picture).getOrThrow(),
                    sourceLeft = sourceLeft,
                    sourceTop = sourceTop,
                    goes = goes,
                ),
                text = text,
                buttonLabels = buttons,
                font = font,
            ),
        ).toImage()
    }

    private fun PlayField.toImage(): BufferedImage {
        val image = BufferedImage(PlayField.WIDTH, PlayField.HEIGHT, BufferedImage.TYPE_INT_ARGB)
        getRows().forEachIndexed { y, row ->
            row.forEachIndexed { x, rgb ->
                val argb = if (rgb.transparent) 0
                else (0xFF shl 24) or (rgb.red shl 16) or (rgb.green shl 8) or rgb.blue
                image.setRGB(x, y, argb)
            }
        }
        return image
    }

    private fun repository(): ViewConeRepositoryImpl {
        val resources = ResourceRepositoryImpl()
        val palRepository = PalRepositoryImpl(resources)
        val cpsRepository = CpsRepositoryImpl(resources)
        return ViewConeRepositoryImpl(
            infRepository = InfRepositoryImpl(
                resourceRepository = resources,
                mazRepository = MazRepositoryImpl(resources),
                vmpRepository = VmpRepositoryImpl(resources),
                vcnRepository = VcnRepositoryImpl(resources),
                palRepository = palRepository,
                cpsRepository = cpsRepository,
                decRepository = DecRepositoryImpl(resources),
            ),
            itemsRepository = ItemsRepositoryImpl(resources),
            cpsRepository = cpsRepository,
            dcrRepository = DcrRepositoryImpl(resources),
        )
    }

    /**
     * Renders the frame a square's trigger script puts up rather than the level
     * as loaded, so a scene the party is walked into — a monster conjured, the
     * party spun round to face it — is drawn the way the player meets it.
     *
     * A script says when it wants to be seen, so the frame drawn here is the
     * one it drew first. Everything after that is the conversation, which a
     * golden has no way to click through.
     */
    private fun renderAfterStepping(
        level: String,
        number: Int,
        x: Int,
        y: Int,
        direction: Direction,
        /** The buttons to click, in order, for a script that asks its way along. */
        answers: List<Int> = emptyList(),
        /** Which frame the script drew to render; [LAST_FRAME] takes its final one. */
        frame: Int = 0,
    ): ViewPort = runBlocking {
        val repository = repository()
        val inf = repository.loadLevel(level).getOrThrow()

        val stage = RecordingStage(answers)
        val stepped = LevelScriptRunner(inf.script, level = number).onEvent(
            triggers = inf.triggers,
            event = ScriptEvent.PARTY_ENTERED,
            state = GameState(
                party = PartyState(Location(x, y), direction),
                monsters = inf.monsterInstances,
            ),
            stage = stage,
        )
        val wanted = if (frame == LAST_FRAME) stage.shown.lastIndex else frame
        val world = stage.shown.getOrNull(wanted) ?: stepped.state

        repository.renderPosition(
            items = inf.items,
            monsters = world.monsters,
            sublevel = inf.subLevels[0],
            playerX = world.party.position.x,
            playerY = world.party.position.y,
            direction = world.party.facing,
        ).getOrThrow()
    }

    private fun renderFrame(level: String, x: Int, y: Int, direction: Direction): ViewPort =
        runBlocking {
            val repository = repository()
            val inf = repository.loadLevel(level).getOrThrow()
            repository.renderPosition(
                items = inf.items,
                monsters = inf.monsterInstances,
                sublevel = inf.subLevels[0],
                playerX = x,
                playerY = y,
                direction = direction
            ).getOrThrow()
        }

    private fun ViewPort.toImage(): BufferedImage {
        val image = BufferedImage(ViewPort.COLS, ViewPort.ROWS, BufferedImage.TYPE_INT_ARGB)
        getRows().forEachIndexed { y, row ->
            row.forEachIndexed { x, rgb ->
                val argb = if (rgb.transparent) {
                    0
                } else {
                    (0xFF shl 24) or (rgb.red shl 16) or (rgb.green shl 8) or rgb.blue
                }
                image.setRGB(x, y, argb)
            }
        }
        return image
    }

    private fun countDifferingPixels(golden: BufferedImage, actual: BufferedImage): Int {
        if (golden.width != actual.width || golden.height != actual.height) {
            return golden.width * golden.height
        }
        var count = 0
        for (y in 0 until golden.height) {
            for (x in 0 until golden.width) {
                if (golden.getRGB(x, y) != actual.getRGB(x, y)) count++
            }
        }
        return count
    }

    /** Golden pixels dimmed to grayscale, differing pixels highlighted in red. */
    private fun diffImage(golden: BufferedImage, actual: BufferedImage): BufferedImage {
        val diff = BufferedImage(golden.width, golden.height, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until golden.height) {
            for (x in 0 until golden.width) {
                val g = golden.getRGB(x, y)
                diff.setRGB(
                    x, y,
                    if (x < actual.width && y < actual.height && g == actual.getRGB(x, y)) {
                        val gray = ((g shr 16 and 0xFF) + (g shr 8 and 0xFF) + (g and 0xFF)) / 6
                        (0xFF shl 24) or (gray shl 16) or (gray shl 8) or gray
                    } else {
                        0xFFFF0000.toInt()
                    }
                )
            }
        }
        return diff
    }

    companion object {
        /** Render the last frame a script drew rather than its first. */
        private const val LAST_FRAME = -1

        // Gradle runs jvmTest with the subproject as working directory, but be
        // tolerant of an IDE launching from the repository root.
        private val projectDir: File = run {
            val cwd = File(System.getProperty("user.dir"))
            if (cwd.resolve("src/jvmTest").isDirectory) cwd else cwd.resolve("composeApp")
        }

        private val goldensDir = projectDir.resolve("src/jvmTest/goldens")
        private val failuresDir = projectDir.resolve("build/golden-failures")

        private val updateGoldens =
            System.getenv("UPDATE_GOLDENS") == "1" || System.getProperty("updateGoldens") != null
    }
}
