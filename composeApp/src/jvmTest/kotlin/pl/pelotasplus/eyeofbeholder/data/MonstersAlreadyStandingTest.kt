package pl.pelotasplus.eyeofbeholder.data

import kotlinx.coroutines.runBlocking
import org.junit.experimental.categories.Category
import pl.pelotasplus.eyeofbeholder.NeedsGameData
import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.ScriptEvent
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

/**
 * That a monster's arrival cannot only mean one that walked.
 *
 * A floor is peopled before anybody sees it, and the twelfth begins with one
 * of its monsters already standing on a square that answers a monster's
 * weight. Raising the event only when something takes a step would leave that
 * square never told, and it opens a way — so the floor would be laid out
 * differently from the moment it loads.
 */
@Category(NeedsGameData::class)
class MonstersAlreadyStandingTest {

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
    fun `a floor begins with a monster standing on a square that answers one`() = runBlocking {
        val standingSomewhereThatAnswers = (1..16).flatMap { number ->
            val inf = infRepository.loadInf("LEVEL$number.INF").getOrNull()
                ?: return@flatMap emptyList()

            val answering = inf.triggers
                .filter { it.flags.reactsTo(ScriptEvent.A_MONSTER_ARRIVED) }
                .map { it.location }

            inf.monsterInstances
                .map { it.location }
                .filter { it in answering }
                .map { number to it }
        }

        assertEquals(listOf(12 to Location(7, 11)), standingSomewhereThatAnswers)
    }
}
