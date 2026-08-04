package pl.pelotasplus.eyeofbeholder.data

import kotlinx.coroutines.runBlocking
import pl.pelotasplus.eyeofbeholder.data.model.Alignment
import pl.pelotasplus.eyeofbeholder.data.model.CharacterClass
import pl.pelotasplus.eyeofbeholder.data.model.SquarePlace
import pl.pelotasplus.eyeofbeholder.data.repository.FileSaveStore
import pl.pelotasplus.eyeofbeholder.data.repository.SaveSlot
import pl.pelotasplus.eyeofbeholder.data.repository.SavedGameRepositoryImpl
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A save written before a field had a type of its own is still a save.
 *
 * This is not hypothetical. A champion's class and alignment were plain
 * numbers, and giving them types stopped every save on disk from loading —
 * and the list of saves drops what it cannot read, so they looked deleted
 * rather than unreadable. The numbers the game itself uses are what goes into
 * a save, so that neither renaming a class nor typing one can do it again.
 */
class OlderSaveTest {

    private val store = FileSaveStore(createTempDirectory("eob-older-saves").toFile())
    private val repository = SavedGameRepositoryImpl(store)

    /**
     * A save as it was written before the champions' numbers were typed and
     * before race and sex were told apart: one champion, a class of 2 and an
     * alignment of 0, and a `raceAndSex` nothing reads any more.
     *
     * It also holds a monster and an item from before where a thing stands on
     * its square had a type: both call it `pos`, and both write a number.
     */
    private val asItUsedToBeWritten = """
        {
          "version": 1,
          "description": "LEVEL4 15x11",
          "savedAt": 1750000000000,
          "level": 4,
          "subLevel": 0,
          "champions": [
            {
              "name": "PERICLES",
              "portrait": 2,
              "abilities": {},
              "hitPoints": { "current": 70, "max": 70 },
              "armorClass": 1,
              "food": 99,
              "raceAndSex": 0,
              "characterClass": 2,
              "alignment": 0,
              "levels": [ { "level": 6, "experience": 69000 } ],
              "carrying": [ 436, 435 ],
              "flags": 1
            }
          ],
          "world": {
            "party": { "position": { "x": 15, "y": 11 }, "facing": "SOUTH" },
            "monsters": [
              {
                "index": 0, "unit": 0, "block": 367, "pos": 4,
                "direction": "NORTH", "type": 1, "gfxIndex": 0,
                "mode": 0, "pause": 0, "weapon": 0, "pocketItem": 0
              }
            ],
            "flags": {},
            "leftBehind": {},
            "changedWalls": [],
            "items": [
              {
                "nameUnidentified": 1, "nameIdentified": 2, "flags": 64,
                "icon": 3, "type": 4, "pos": 8,
                "location": { "x": 16, "y": 1 },
                "next": 0, "prev": 0, "level": 6, "value": 0
              }
            ]
          },
          "messages": []
        }
    """.trimIndent()

    @Test
    fun `a save written before the types existed still loads`() = runBlocking {
        store.write(SaveSlot.AUTOSAVE, asItUsedToBeWritten)

        val loaded = repository.load(SaveSlot.AUTOSAVE).getOrThrow()
        val pericles = loaded.champions.single()

        assertEquals("PERICLES", pericles.name)
        assertEquals(CharacterClass.PALADIN, pericles.characterClass)
        assertEquals(Alignment.LAWFUL_GOOD, pericles.alignment)
        assertEquals(70, pericles.hitPoints.max)
        assertEquals(4, loaded.level)
    }

    /** Such a save is one the list offers, not one it quietly leaves out. */
    @Test
    fun `an older save is listed among the ones that can be loaded`() = runBlocking {
        store.write(SaveSlot.numbered[0], asItUsedToBeWritten)

        assertTrue(SaveSlot.numbered[0] in repository.saved().keys)
    }

    /**
     * What it cannot know it says nothing about rather than guessing: race and
     * sex were one number then and are two things now, and no old save holds
     * either of them.
     */
    @Test
    fun `what an older save never wrote comes back as nothing`() = runBlocking {
        store.write(SaveSlot.AUTOSAVE, asItUsedToBeWritten)

        val pericles = repository.load(SaveSlot.AUTOSAVE).getOrThrow().champions.single()

        assertEquals(null, pericles.race)
        assertEquals(null, pericles.sex)
    }

    /**
     * Where a thing stands on its square is a number in a save and a name in
     * the code, and the two have to keep agreeing: 8 is the niche a thing is
     * shelved in, and 4 the middle of the square a big monster fills.
     */
    @Test
    fun `a place written as a number comes back as the place it names`() = runBlocking {
        store.write(SaveSlot.AUTOSAVE, asItUsedToBeWritten)

        val world = repository.load(SaveSlot.AUTOSAVE).getOrThrow().world

        assertEquals(SquarePlace.IN_A_NICHE, world.items.single().place)
        assertEquals(SquarePlace.MIDDLE, world.monsters.single().place)
    }

    /**
     * And a save written today, from what an older one said, reads back as
     * the same thing — so an old save can be loaded and saved again without
     * losing what it did hold.
     */
    @Test
    fun `an older save rewritten today says the same`() = runBlocking {
        store.write(SaveSlot.AUTOSAVE, asItUsedToBeWritten)
        val older = repository.load(SaveSlot.AUTOSAVE).getOrThrow()

        repository.save(
            slot = SaveSlot.numbered[1],
            description = older.description,
            savedAt = older.savedAt,
            level = older.level,
            subLevel = older.subLevel,
            champions = older.champions,
            world = pl.pelotasplus.eyeofbeholder.data.model.GameState.restoredFrom(
                older.world,
                on = older.level,
            ),
            messages = older.messages,
        ).getOrThrow()

        val again = repository.load(SaveSlot.numbered[1]).getOrThrow()

        assertEquals(older.champions, again.champions)
        assertEquals(CharacterClass.PALADIN, again.champions.single().characterClass)
    }
}
