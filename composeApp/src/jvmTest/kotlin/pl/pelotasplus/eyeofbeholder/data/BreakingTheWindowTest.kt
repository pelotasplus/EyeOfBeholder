package pl.pelotasplus.eyeofbeholder.data

import kotlinx.coroutines.runBlocking
import org.junit.experimental.categories.Category
import pl.pelotasplus.eyeofbeholder.NeedsGameData
import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.GameState
import pl.pelotasplus.eyeofbeholder.data.model.Inf
import pl.pelotasplus.eyeofbeholder.data.model.ItemIndex
import pl.pelotasplus.eyeofbeholder.data.model.ItemTypes
import pl.pelotasplus.eyeofbeholder.data.model.LevelScriptRunner
import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.Maz
import pl.pelotasplus.eyeofbeholder.data.model.PartyState
import pl.pelotasplus.eyeofbeholder.data.model.ScriptEvent
import pl.pelotasplus.eyeofbeholder.data.model.WallSide
import pl.pelotasplus.eyeofbeholder.data.repository.CpsRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.DecRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.InfRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.ItemTypesRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.MazRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.OriginalSaveRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.PalRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.ResourceRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.VcnRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.VmpRepositoryImpl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The window in the temple, and what taking a weapon to it costs.
 *
 * The party stand on 14x11 looking east. That square has no walls of its own;
 * the face they are looking at belongs to 15x11, and it is decoration 62 — a
 * window. Its script offers two different answers depending on how it is
 * treated, and one of them is the clerics turning on the party.
 */
@Category(NeedsGameData::class)
class BreakingTheWindowTest {

    private val resources = ResourceRepositoryImpl()

    private val level: Inf = runBlocking {
        InfRepositoryImpl(
            resourceRepository = resources,
            mazRepository = MazRepositoryImpl(resources),
            vmpRepository = VmpRepositoryImpl(resources),
            vcnRepository = VcnRepositoryImpl(resources),
            palRepository = PalRepositoryImpl(resources),
            cpsRepository = CpsRepositoryImpl(resources),
            decRepository = DecRepositoryImpl(resources),
        ).loadInf("LEVEL5.INF").getOrThrow()
    }

    private val types: ItemTypes = runBlocking {
        ItemTypesRepositoryImpl(resources).loadItemTypes().getOrThrow()
    }

    private val saved = runBlocking {
        OriginalSaveRepositoryImpl(resources)
            .loadOriginalSave(OriginalSaveRepositoryImpl.QUICK_START)
            .getOrThrow()
    }

    private val sub get() = level.subLevels[0]

    /** The party outside the window, with the clerics where the file puts them. */
    private fun world() = GameState(
        party = PartyState(Location(14, 11), Direction.EAST),
        champions = saved.party,
        items = saved.items,
    ).arrivingAt(
        level = 5,
        places = level.monsterInstances,
        maz = sub.maz,
        kinds = sub.monsters,
    )

    private fun runner() = LevelScriptRunner(
        script = level.script,
        level = 5,
        subLevel = 0,
        kinds = sub.monsters,
        itemTypes = types,
    )

    /** The window itself, as the maze has it. */
    private fun GameState.theWindow() = wall(5, Location(15, 11), WallSide.WEST)

    /** Whatever the first champion is holding, which the save says is a weapon. */
    private val aWeapon: ItemIndex get() = saved.party.first().carrying.first()

    /** The slot of something that is not a weapon: a scroll from the pack. */
    private val notAWeapon: ItemIndex
        get() = saved.party.first().carrying
            .drop(2)
            .first { slot ->
                slot.isSomething &&
                    saved.items.getOrNull(slot.value)?.let { !types.isSwungByHand(it) } == true
            }

    private fun used(
        what: ItemIndex?,
        event: ScriptEvent,
        from: GameState = world(),
    ) = runBlocking {
        runner().onEvent(
            triggers = level.triggers,
            event = event,
            state = from,
            at = Location(15, 11),
            used = what,
        ).state
    }

    @Test
    fun `the window is a decoration on the far square's face`() {
        val wall = world().theWindow()

        assertTrue(wall is Maz.WallType.Decoration, "15x11 west is not a decoration")
        assertEquals(62, wall.decorationWallIndex)

        val kind = sub.decorations.first { it.decorationWallIndex == 62 }
        assertEquals(8, kind.specialType, "the window is not a shape on a wall")
    }

    /**
     * Pointing at it reads the carving and leaves it alone. This is the branch
     * a click takes, and it is why breaking the window cannot be a click.
     */
    @Test
    fun `clicking the window leaves it whole`() {
        val after = used(null, ScriptEvent.WALL_CLICKED)

        assertEquals(world().theWindow(), after.theWindow())
        assertTrue(after.monsters.none { it.provoked }, "pointing at it roused the temple")
    }

    /** Taking a weapon to it turns the wall into a different decoration. */
    @Test
    fun `using a weapon on the window breaks it`() {
        val broken = used(aWeapon, ScriptEvent.ITEM_USED_ON_WALL).theWindow()

        assertTrue(broken is Maz.WallType.Decoration)
        assertEquals(63, broken.decorationWallIndex, "the window did not break")
    }

    /**
     * They give the party one warning.
     *
     * The subroutine the window calls asks two things: whether it has been run
     * before, and whether there is still anybody on the clerics' square to
     * mind. Both hold the first time, so the first blow is answered by their
     * speaking; the second is answered by the pair of them turning, which is
     * the same rouse bit the door dialogue sets, reached another way.
     */
    @Test
    fun `the first blow is a warning and the second rouses them`() {
        val warned = used(aWeapon, ScriptEvent.ITEM_USED_ON_WALL)

        assertTrue(
            warned.monsters.none { it.provoked },
            "the clerics turned on the party without a word",
        )

        val angered = used(aWeapon, ScriptEvent.ITEM_USED_ON_WALL, from = warned)

        assertEquals(
            listOf(16, 17),
            angered.monsters.filter { it.provoked }.map { it.index.value }.sorted(),
        )
    }

    /**
     * And anything else leaves it alone. This is the case that fails when the
     * condition asking what was used is not answered at all: unanswered reads
     * as true, and the window breaks for a scroll or an empty hand.
     */
    @Test
    fun `using something that is not a weapon on it does nothing`() {
        listOf(notAWeapon, null).forEach { what ->
            val after = used(what, ScriptEvent.ITEM_USED_ON_WALL)

            assertEquals(world().theWindow(), after.theWindow(), "$what broke the window")
            assertFalse(after.monsters.any { it.provoked }, "$what roused the temple")
        }
    }
}
