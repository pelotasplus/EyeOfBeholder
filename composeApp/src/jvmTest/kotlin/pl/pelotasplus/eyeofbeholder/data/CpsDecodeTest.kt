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

    @Test
    fun `files the game ships unusable are rejected with a reason`() {
        val reasons = runBlocking {
            UNUSABLE.associateWith { cpsRepository.loadCps(it).exceptionOrNull()?.message }
        }

        // 0-byte files in the retail data
        assertTrue(
            reasons.getValue("COIN.CPS")?.contains("too short") == true,
            "COIN.CPS should be rejected as too short, got: ${reasons["COIN.CPS"]}"
        )
        assertTrue(
            reasons.getValue("KHELBAN2.CPS")?.contains("too short") == true,
            "KHELBAN2.CPS should be rejected as too short, got: ${reasons["KHELBAN2.CPS"]}"
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
        /** The two the retail data ships as 0-byte files. */
        val UNUSABLE = listOf("COIN.CPS", "KHELBAN2.CPS")
        val updateGoldens = System.getenv("UPDATE_GOLDENS") != null
    }
}
