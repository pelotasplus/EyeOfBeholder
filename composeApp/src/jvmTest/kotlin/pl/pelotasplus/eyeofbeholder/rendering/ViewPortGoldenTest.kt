package pl.pelotasplus.eyeofbeholder.rendering

import kotlinx.coroutines.runBlocking
import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.ViewPort
import pl.pelotasplus.eyeofbeholder.data.repository.CpsRepositoryImpl
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

    private fun checkGolden(name: String, level: String, x: Int, y: Int, direction: Direction) {
        val actual = renderFrame(level, x, y, direction).toImage()
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
                "$name: $differing of ${ViewPort.COLS * ViewPort.ROWS} pixels differ from golden.\n" +
                        "  actual: ${actualFile.absolutePath}\n" +
                        "  diff:   ${diffFile.absolutePath}\n" +
                        "  If the change is intentional: UPDATE_GOLDENS=1 ./gradlew :composeApp:jvmTest"
            )
        }
    }

    private fun renderFrame(level: String, x: Int, y: Int, direction: Direction): ViewPort =
        runBlocking {
            val resources = ResourceRepositoryImpl()
            val palRepository = PalRepositoryImpl(resources)
            val cpsRepository = CpsRepositoryImpl(resources)
            val repository = ViewConeRepositoryImpl(
                infRepository = InfRepositoryImpl(
                    resourceRepository = resources,
                    mazRepository = MazRepositoryImpl(resources),
                    vmpRepository = VmpRepositoryImpl(resources),
                    vcnRepository = VcnRepositoryImpl(resources, palRepository),
                    palRepository = palRepository,
                    cpsRepository = cpsRepository,
                    decRepository = DecRepositoryImpl(resources),
                ),
                itemsRepository = ItemsRepositoryImpl(resources),
                cpsRepository = cpsRepository,
            )

            val inf = repository.loadLevel(level).getOrThrow()
            repository.renderPosition(
                items = inf.items,
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
