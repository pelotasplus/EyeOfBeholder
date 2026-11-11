package pl.pelotasplus.eyeofbeholder.agentserver

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Which save `checkpoint` gives up when all six slots are taken. */
class OldestSaveTest {

    private val root = Files.createTempDirectory("agent-home").toFile()
    private val saves = File(root, "saves").apply { mkdirs() }
    private val home = AgentHome(File(root, "agent"), saves)

    private fun save(slot: Int, description: String, writtenAt: Long) =
        File(saves, "slot$slot.json").apply {
            writeText("""{"description":"$description"}""")
            setLastModified(writtenAt)
        }

    @Test
    fun `the save written longest ago is the one given up`() {
        save(1, "azure-2-shell", writtenAt = 3_000_000)
        save(2, "start", writtenAt = 1_000_000)
        save(3, "mark", writtenAt = 2_000_000)

        assertEquals("start", home.oldestSave())
    }

    @Test
    fun `no saves gives nothing to give up`() {
        assertNull(home.oldestSave())
    }
}
