package pl.pelotasplus.eyeofbeholder.data

import kotlinx.coroutines.runBlocking
import org.junit.experimental.categories.Category
import pl.pelotasplus.eyeofbeholder.NeedsGameData
import pl.pelotasplus.eyeofbeholder.data.model.entryPoints
import pl.pelotasplus.eyeofbeholder.data.model.levelFileName
import pl.pelotasplus.eyeofbeholder.data.model.levelNumber
import pl.pelotasplus.eyeofbeholder.data.repository.CpsRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.DecRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.InfRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.MazRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.PalRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.ResourceRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.VcnRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.VmpRepositoryImpl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Category(NeedsGameData::class)
class LevelEntryPointTest {

    private val resources = ResourceRepositoryImpl()

    private val infRepository = InfRepositoryImpl(
        resourceRepository = resources,
        mazRepository = MazRepositoryImpl(resources),
        vmpRepository = VmpRepositoryImpl(resources),
        vcnRepository = VcnRepositoryImpl(resources),
        palRepository = PalRepositoryImpl(resources),
        cpsRepository = CpsRepositoryImpl(resources),
        decRepository = DecRepositoryImpl(resources),
    )

    @Test
    fun `script-only load reads the same script as a full level load`() = runBlocking {
        listOf("LEVEL1.INF", "LEVEL7.INF", "LEVEL16.INF").forEach { name ->
            val full = infRepository.loadInf(name).getOrThrow().script
            val scriptOnly = infRepository.loadScript(name).getOrThrow()

            assertEquals(full, scriptOnly, "$name script differs between the two loads")
        }
    }

    @Test
    fun `every level is reachable from some level's script`() = runBlocking {
        val names = resources.listResources(".INF").getOrThrow()

        val byLevel = names
            .flatMap { name ->
                infRepository.loadScript(name).getOrThrow()
                    .entryPoints(fromLevel = levelNumber(name))
            }
            .groupBy { levelFileName(it.level) }

        names.forEach { name ->
            assertTrue(byLevel[name].orEmpty().isNotEmpty(), "no script leads into $name")
        }
    }
}
