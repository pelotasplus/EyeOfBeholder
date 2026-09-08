package pl.pelotasplus.eyeofbeholder.data

import kotlinx.coroutines.runBlocking
import org.junit.experimental.categories.Category
import pl.pelotasplus.eyeofbeholder.NeedsGameData
import pl.pelotasplus.eyeofbeholder.data.model.PaletteIndex
import pl.pelotasplus.eyeofbeholder.data.repository.CpsRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.ResourceRepositoryImpl
import java.io.File
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Decode coverage for every .CPS in the game data.
 *
 * The golden-image tests only exercise the handful of assets a rendered
 * viewport happens to touch, which is how three LCW decoder bugs survived: a
 * missing destination-full check, unclamped counts, and a phantom relative
 * addressing mode. Those only showed on files no golden scene renders
 * (DRANX.CPS, DOORWAY1.CPS, KHELBAN1.CPS).
 *
 * So this pins the decode result of all of them: one line per file holding a
 * checksum of the decoded pixels, in
 * `src/jvmTest/goldens/cps-decode.txt`. Any change to LCWHelper or the CPS
 * header parsing that alters a single pixel of any image fails here.
 *
 * To accept an intentional change (or bootstrap the file):
 *   UPDATE_GOLDENS=1 ./gradlew :composeApp:jvmTest
 */
@Category(NeedsGameData::class)
class CpsDecodeTest {

    private val resources = ResourceRepositoryImpl()
    private val cpsRepository = CpsRepositoryImpl(resources)

    @Test
    fun `every CPS decodes to a stable result`() {
        val actual = runBlocking {
            resources.listResources(".CPS").getOrThrow().sorted().map { name ->
                "$name ${describeDecode(name)}"
            }
        }

        val goldenFile = File("src/jvmTest/goldens/cps-decode.txt")

        if (updateGoldens) {
            goldenFile.parentFile.mkdirs()
            goldenFile.writeText(actual.joinToString("\n", postfix = "\n"))
            println("Updated ${goldenFile.absolutePath} with ${actual.size} entries")
            return
        }

        if (!goldenFile.exists()) {
            fail(
                "Missing ${goldenFile.absolutePath}\n" +
                        "Run UPDATE_GOLDENS=1 ./gradlew :composeApp:jvmTest to create it."
            )
        }

        val expected = goldenFile.readLines().filter { it.isNotBlank() }
        val differences = buildList {
            (expected.map { it.substringBefore(' ') } + actual.map { it.substringBefore(' ') })
                .distinct()
                .sorted()
                .forEach { name ->
                    val was = expected.firstOrNull { it.startsWith("$name ") }
                    val now = actual.firstOrNull { it.startsWith("$name ") }
                    if (was != now) add("  $name\n    expected: $was\n    actual:   $now")
                }
        }

        if (differences.isNotEmpty()) {
            fail(
                "${differences.size} CPS file(s) decode differently than recorded:\n" +
                        differences.joinToString("\n") + "\n" +
                        "If the change is intentional: UPDATE_GOLDENS=1 ./gradlew :composeApp:jvmTest"
            )
        }
    }

    /**
     * The decoder must never throw on real game data. Files the game itself
     * ships broken or in an unsupported format are rejected with an
     * explanation rather than crashing mid-decode.
     */
    @Test
    fun `no CPS crashes the decoder`() {
        val crashes = runBlocking {
            resources.listResources(".CPS").getOrThrow().mapNotNull { name ->
                val failure = cpsRepository.loadCps(name).exceptionOrNull()
                when {
                    failure == null -> null
                    failure is IllegalStateException && failure.message != null -> null
                    else -> "$name -> $failure"
                }
            }
        }

        assertTrue(
            crashes.isEmpty(),
            "these CPS files failed with something other than a described IllegalStateException:\n" +
                    crashes.joinToString("\n")
        )
    }

    /**
     * Every sheet the game ships loads. Not a decoder test — a data test, and
     * the one that says whether this copy of the game is whole.
     *
     * Nine files in an earlier copy were not: two at zero bytes and seven
     * holding a truncated stream with the front of a zip archive written over
     * the rest. That went unnoticed for months because nothing asked this, and
     * the two empty ones had been written down here as something the retail
     * data does. It does not.
     */
    @Test
    fun `every CPS the game ships can be read`() {
        val unreadable = runBlocking {
            resources.listResources(".CPS").getOrThrow().sorted().mapNotNull { name ->
                cpsRepository.loadCps(name).exceptionOrNull()?.let { "$name -> ${it.message}" }
            }
        }

        assertTrue(
            unreadable.isEmpty(),
            "the game data is damaged; these sheets cannot be read:\n" +
                unreadable.joinToString("\n"),
        )
    }

    private suspend fun describeDecode(name: String): String =
        cpsRepository.loadCps(name).fold(
            onSuccess = { cps ->
                val painted = cps.pixels.indexOfLast { !it.isTransparent } + 1
                "ok sha256=${sha256(cps.pixels)} painted=$painted/${cps.pixels.size} " +
                        "palette=${if (cps.palette != null) "embedded" else "external"}"
            },
            onFailure = { "rejected: ${it.message}" }
        )

    private fun sha256(pixels: List<PaletteIndex>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(ByteArray(pixels.size) { pixels[it].value.toByte() })
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private companion object {
        val updateGoldens = System.getenv("UPDATE_GOLDENS") != null
    }
}
