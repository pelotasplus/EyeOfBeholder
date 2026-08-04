package pl.pelotasplus.eyeofbeholder.data

import kotlinx.coroutines.runBlocking
import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.FlagBit
import pl.pelotasplus.eyeofbeholder.data.model.GameState
import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.Maz
import pl.pelotasplus.eyeofbeholder.data.model.MonsterInstance
import pl.pelotasplus.eyeofbeholder.data.model.MonsterTypeId
import pl.pelotasplus.eyeofbeholder.data.model.PaletteIndex
import pl.pelotasplus.eyeofbeholder.data.model.PartyState
import pl.pelotasplus.eyeofbeholder.data.model.SquarePlace
import pl.pelotasplus.eyeofbeholder.data.model.PlayField
import pl.pelotasplus.eyeofbeholder.data.model.SavedGame
import pl.pelotasplus.eyeofbeholder.data.model.WallByte
import pl.pelotasplus.eyeofbeholder.data.model.WallSide
import pl.pelotasplus.eyeofbeholder.data.repository.FileSaveStore
import pl.pelotasplus.eyeofbeholder.data.repository.OriginalSaveRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.ResourceRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.SaveSlot
import pl.pelotasplus.eyeofbeholder.data.repository.SavedGameRepositoryImpl
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A game saved and loaded is the game that was saved.
 *
 * The world here is built to be awkward on purpose — a level walked out of and
 * remembered, flags set on two levels, walls changed on a level the party are
 * not standing on — because those are the parts a save loses quietly. A party
 * standing still with nothing set would round trip even if half the fields
 * were dropped.
 */
class SavedGameRoundTripTest {

    private val store = FileSaveStore(createTempDirectory("eob-saves").toFile())
    private val repository = SavedGameRepositoryImpl(store)

    private val champions = runBlocking {
        OriginalSaveRepositoryImpl(ResourceRepositoryImpl())
            .loadOriginalSave(OriginalSaveRepositoryImpl.QUICK_START)
            .getOrThrow()
            .party
    }

    private val world = GameState(PartyState(Location(10, 8), Direction.WEST))
        .arrivingAt(6, monsters(onLevel = 6))
        .levelFlagSet(6, FlagBit(2))
        .leaving(6)
        .arrivingAt(5, monsters(onLevel = 5))
        .levelFlagSet(5, FlagBit(15))
        .globalFlagSet(FlagBit(30))
        .wallsChanged(5, Location(9, 8), to = WallByte(44))
        .wallChanged(6, Location(1, 2), WallSide.EAST, to = WallByte(0))

    @Test
    fun `a world saved and loaded is the world that was saved`() = runBlocking {
        repository.save(
            slot = SaveSlot.numbered.first(),
            description = "LEVEL5",
            savedAt = 1_754_000_000_000,
            level = 5,
            champions = champions,
            world = world,
        ).getOrThrow()

        val loaded = repository.load(SaveSlot.numbered.first()).getOrThrow()

        assertEquals(SavedGame.VERSION, loaded.version)
        assertEquals("LEVEL5", loaded.description)
        assertEquals(1_754_000_000_000, loaded.savedAt)
        assertEquals(5, loaded.level)
        assertEquals(champions, loaded.champions)
        assertEquals(world.saved(), loaded.world)
    }

    /**
     * The bar along the bottom comes back too. The original saved no such
     * thing, but coming back to a tab is not the same as choosing to load a
     * game: the lines on screen are the last thing that happened.
     */
    @Test
    fun `what was written on the bar comes back with the game`() = runBlocking {
        val said = listOf(
            PlayField.Message("you feel a cold draft from the north.", PaletteIndex(9)),
            PlayField.Message("the wall vanishes.", PaletteIndex(15)),
        )

        repository.save(
            slot = SaveSlot.AUTOSAVE,
            description = "auto",
            savedAt = 0,
            level = 5,
            champions = champions,
            world = world,
            messages = said,
        ).getOrThrow()

        assertEquals(said, repository.load(SaveSlot.AUTOSAVE).getOrThrow().messages)
    }

    /** A save written before the bar was kept still opens, with an empty one. */
    @Test
    fun `a save from before messages were kept reads as having none`() = runBlocking {
        val slot = SaveSlot.numbered[3]
        repository.save(slot, "older", 0, 5, champions = champions, world = world).getOrThrow()

        assertEquals(emptyList(), repository.load(slot).getOrThrow().messages)
    }

    /**
     * The wall a script opened is still open, and the one it did not is still
     * shut — restoring the overrides but not the mazes would pass the first
     * of those and fail the second.
     */
    @Test
    fun `walls a script changed come back changed`() = runBlocking {
        val restored = restore()
            .arrivingAt(5, emptyList(), mazeOfPlainWalls())

        assertEquals(Maz.WallType.Decoration(44), restored.wall(5, Location(9, 8), WallSide.NORTH))
        assertEquals(Maz.WallType.NoWall, restored.wall(6, Location(1, 2), WallSide.EAST))
        assertEquals(
            Maz.WallType.FixedWall(0),
            restored.wall(5, Location(1, 1), WallSide.NORTH),
            "a wall nobody touched still comes from the level file",
        )
    }

    @Test
    fun `flags set on a level the party have left are still set`() = runBlocking {
        val restored = restore()

        assertTrue(restored.isLevelFlagSet(6, FlagBit(2)), "set before the party walked out")
        assertTrue(restored.isLevelFlagSet(5, FlagBit(15)))
        assertTrue(restored.isGlobalFlagSet(FlagBit(30)))
        assertTrue(!restored.isLevelFlagSet(5, FlagBit(2)), "level 6's bit is not level 5's")
    }

    @Test
    fun `a level the party walked out of is peopled as they left it`() = runBlocking {
        val restored = restore().arrivingAt(6, emptyList())

        assertEquals(monsters(onLevel = 6), restored.monsters)
    }

    @Test
    fun `an empty slot reads as empty rather than as a broken save`() = runBlocking {
        val untouched = SaveSlot.numbered.last()

        assertNull(store.read(untouched))
        assertTrue(repository.load(untouched).isFailure)
        assertTrue(untouched !in repository.saved().keys)
    }

    @Test
    fun `erasing a slot empties it and leaves the others alone`() = runBlocking {
        val kept = SaveSlot.numbered[1]
        val dropped = SaveSlot.numbered[2]

        listOf(kept, dropped).forEach {
            repository.save(it, it.name, 0, 5, champions = champions, world = world).getOrThrow()
        }
        repository.erase(dropped).getOrThrow()

        assertEquals(setOf(kept), repository.saved().keys - SaveSlot.numbered.first())
    }

    @Test
    fun `the autosave is not one of the six`() {
        assertEquals(6, SaveSlot.numbered.size)
        assertTrue(SaveSlot.AUTOSAVE !in SaveSlot.numbered)
    }

    private suspend fun restore(): GameState {
        repository.save(
            slot = SaveSlot.AUTOSAVE,
            description = "auto",
            savedAt = 0,
            level = 5,
            champions = champions,
            world = world,
        ).getOrThrow()

        val saved = repository.load(SaveSlot.AUTOSAVE).getOrThrow()
        return GameState.restoredFrom(saved.world, on = saved.level)
    }

    /**
     * Loading must not repopulate the level the party were standing on from
     * its file, or a monster a script conjured — or one already killed —
     * would be undone by loading.
     */
    @Test
    fun `the level the party were saved on keeps the monsters it had`() = runBlocking {
        val restored = restore().arrivingAt(5, places = emptyList())

        assertEquals(monsters(onLevel = 5), restored.monsters)
    }

    private fun monsters(onLevel: Int) = listOf(0, 1).map { slot ->
        MonsterInstance(
            index = slot,
            unit = 0,
            block = onLevel * 32 + slot,
            place = SquarePlace.MIDDLE,
            direction = Direction.SOUTH,
            type = MonsterTypeId(onLevel),
            gfxIndex = 0,
            mode = 0,
            pause = 0,
            weapon = 0,
            pocketItem = 0,
        )
    }

    /** A maze with a plain wall on every side of every square. */
    private fun mazeOfPlainWalls() = Maz(
        name = "TEST.MAZ",
        width = 32,
        height = 32,
        squares = (0 until 32).flatMap { row ->
            (0 until 32).map { column ->
                Maz.Square(
                    x = column,
                    y = row,
                    north = Maz.WallType.FixedWall(0),
                    east = Maz.WallType.FixedWall(0),
                    south = Maz.WallType.FixedWall(0),
                    west = Maz.WallType.FixedWall(0),
                )
            }
        },
    )
}
