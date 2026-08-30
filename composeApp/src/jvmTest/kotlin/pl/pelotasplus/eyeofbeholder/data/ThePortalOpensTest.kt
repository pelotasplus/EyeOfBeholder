package pl.pelotasplus.eyeofbeholder.data

import kotlinx.coroutines.runBlocking
import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.GameState
import pl.pelotasplus.eyeofbeholder.data.model.Inf
import pl.pelotasplus.eyeofbeholder.data.model.Item
import pl.pelotasplus.eyeofbeholder.data.model.ItemIndex
import pl.pelotasplus.eyeofbeholder.data.model.LevelScriptRunner
import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.PartyState
import pl.pelotasplus.eyeofbeholder.data.model.ScriptEvent
import pl.pelotasplus.eyeofbeholder.data.repository.CpsRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.DecRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.InfRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.ItemsRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.MazRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.PalRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.ResourceRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.VcnRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.VmpRepositoryImpl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The archway on level 3, which a stone gem opens and which then puts the
 * party down on another floor.
 *
 * The party stand at 17x23 facing west and take the gem to the arch, so the
 * script that answers is the one on the square in front of them.
 */
class ThePortalOpensTest {

    private val resources = ResourceRepositoryImpl()

    private val level: Inf by lazy {
        runBlocking {
            InfRepositoryImpl(
                resourceRepository = resources,
                mazRepository = MazRepositoryImpl(resources),
                vmpRepository = VmpRepositoryImpl(resources),
                vcnRepository = VcnRepositoryImpl(resources),
                palRepository = PalRepositoryImpl(resources),
                cpsRepository = CpsRepositoryImpl(resources),
                decRepository = DecRepositoryImpl(resources),
            ).loadInf("LEVEL3.INF").getOrThrow()
        }
    }

    private val dungeonItems: List<Item> by lazy {
        runBlocking { ItemsRepositoryImpl(resources).loadItems().getOrThrow().items }
    }

    /** The square the arch is on, which is the one the party face. */
    private val theArch = Location(16, 23)

    /**
     * The arch is clicked rather than had an item used on it, and reads what
     * is in the hand doing the clicking. Its trigger says as much: it answers
     * a wall being clicked and not a thing being used on one.
     */
    private fun takingToIt(held: ItemIndex) = RecordingStage().also { stage ->
        runBlocking {
            LevelScriptRunner(level.script).onEvent(
                triggers = level.triggers,
                event = ScriptEvent.WALL_CLICKED,
                state = GameState(
                    party = PartyState(Location(17, 23), Direction.WEST),
                    items = dungeonItems,
                    inHand = held,
                ),
                stage = stage,
                at = theArch,
            )
        }
    }

    /** Index nought is an empty hand, so a thing has to be found past it. */
    private fun somethingThatIs(gem: Boolean): ItemIndex {
        val at = dungeonItems
            .drop(1)
            .indexOfFirst { (it.type.value == STONE_GEM) == gem }

        assertTrue(at >= 0, "the file holds nothing to take to the arch")
        return ItemIndex(at + 1)
    }

    @Test
    fun `the arch opens for a stone gem`() {
        val stage = takingToIt(somethingThatIs(gem = true))

        assertTrue(
            RecordingStage.Beat.OpenedThePortal in stage.beats,
            "the arch did not open; beats were ${stage.beats}",
        )
    }

    /** Anything else taken to it is a wall, and the script has nothing to say. */
    @Test
    fun `it does not open for anything else`() {
        val stage = takingToIt(somethingThatIs(gem = false))

        assertEquals(
            emptyList(),
            stage.beats.filter { it == RecordingStage.Beat.OpenedThePortal },
            "the arch opened for something that is not a stone gem",
        )
    }

    private companion object {
        /** The kind of thing the arch answers to. */
        const val STONE_GEM = 37
    }
}
