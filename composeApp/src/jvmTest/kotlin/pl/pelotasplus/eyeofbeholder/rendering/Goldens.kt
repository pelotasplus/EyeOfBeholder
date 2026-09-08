package pl.pelotasplus.eyeofbeholder.rendering

import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.fail

/**
 * Comparing a rendered frame against the one frozen beside it.
 *
 * Shared rather than owned by one test because there is more than one surface
 * worth freezing: the panel the dungeon is played on, and the whole screen a
 * scene takes over. Both want the same treatment — a byte comparison, and the
 * frame and a red mask written out where it fails.
 */
object Goldens {

    fun check(name: String, actual: BufferedImage) {
        val goldenFile = goldensDir.resolve("$name.png")

        if (updating) {
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
                    "Review ${candidate.absolutePath} and run " +
                    "UPDATE_GOLDENS=1 ./gradlew :composeApp:jvmTest to accept it."
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

    fun countDifferingPixels(golden: BufferedImage, actual: BufferedImage): Int {
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
    fun diffImage(golden: BufferedImage, actual: BufferedImage): BufferedImage {
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

    // tolerant of an IDE launching from the repository root.
    private val projectDir: File = run {
        val cwd = File(System.getProperty("user.dir"))
        if (cwd.resolve("src/jvmTest").isDirectory) cwd else cwd.resolve("composeApp")
    }

    val goldensDir: File = projectDir.resolve("src/jvmTest/goldens")
    val failuresDir: File = projectDir.resolve("build/golden-failures")

    val updating =
        System.getenv("UPDATE_GOLDENS") == "1" || System.getProperty("updateGoldens") != null
}
