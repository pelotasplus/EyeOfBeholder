package pl.pelotasplus.eyeofbeholder.data

import kotlinx.coroutines.runBlocking
import pl.pelotasplus.eyeofbeholder.data.model.Alignment
import pl.pelotasplus.eyeofbeholder.data.model.CharacterClass
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
            "monsters": [],
            "flags": {},
            "leftBehind": {},
            "changedWalls": []
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
