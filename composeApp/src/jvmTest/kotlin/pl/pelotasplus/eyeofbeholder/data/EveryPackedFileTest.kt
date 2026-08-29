package pl.pelotasplus.eyeofbeholder.data

import kotlinx.coroutines.runBlocking
import org.junit.experimental.categories.Category
import pl.pelotasplus.eyeofbeholder.NeedsGameData
import pl.pelotasplus.eyeofbeholder.data.repository.ResourceRepositoryImpl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Every packed file the game ships, unpacked.
 *
 * A file says in its header how it is packed and how big it comes out, and
 * both are the file's word rather than ours — so unpacking one and getting the
 * size it promised is a test the data itself decides. It is also the only
 * thing that notices a whole kind of packing being missing: the skeletal
 * warrior's sheet is run-length packed where everything else is LCW, and
 * nothing said so until the monster failed to load on level 2.
 */
@Category(NeedsGameData::class)
class EveryPackedFileTest {

    private val resources = ResourceRepositoryImpl()

    /** What a compressed file promises about itself, read straight off it. */
    private data class Header(val packing: Int, val unpackedSize: Int)

    private fun headerOf(path: String): Header? = runBlocking {
        val bytes = resources.readResource(path)
        if (bytes.size < HEADER_SIZE) return@runBlocking null

        val reader = ByteReader(bytes)
        reader.readU16LE()
        Header(packing = reader.readU16LE(), unpackedSize = reader.readU32LE())
    }

    /** The manifest keeps bare names; everything is read from under `files/`. */
    private fun packedFiles() = runBlocking {
        PACKED.flatMap { resources.listResources(it).getOrThrow() }.map { "files/$it" }
    }

    @Test
    fun `every packed file unpacks to the size it promises`() = runBlocking {
        val wrong = packedFiles().mapNotNull { path ->
            val header = headerOf(path) ?: return@mapNotNull null
            val unpacked = runCatching { resources.decompressResource(path).bytes }

            when {
                unpacked.isFailure -> "$path: ${unpacked.exceptionOrNull()?.message}"
                unpacked.getOrThrow().size != header.unpackedSize ->
                    "$path: ${unpacked.getOrThrow().size} bytes, promised ${header.unpackedSize}"

                else -> null
            }
        }

        assertEquals(emptyList(), wrong)
    }

    /**
     * The one that was failing, named so the reason is on the record: it is
     * packed the other way, and everything else in the game is LCW.
     */
    @Test
    fun `the skeletal warrior's sheet is run-length packed`() = runBlocking {
        val header = headerOf("files/SKELWAR.CPS")

        assertEquals(RUN_LENGTH, header?.packing)

        val unpacked = resources.decompressResource("files/SKELWAR.CPS").bytes
        assertEquals(header?.unpackedSize, unpacked.size)
        assertTrue(unpacked.any { it.toInt() != 0 }, "it unpacked to nothing at all")
    }

    private companion object {
        const val HEADER_SIZE = 10
        const val RUN_LENGTH = 3

        /** The kinds of file that carry the header and are packed. */
        val PACKED = listOf(".CPS", ".VCN", ".INF")
    }
}
