package pl.pelotasplus.eyeofbeholder.data

import kotlinx.coroutines.runBlocking
import org.junit.experimental.categories.Category
import pl.pelotasplus.eyeofbeholder.NeedsGameData
import pl.pelotasplus.eyeofbeholder.data.model.Horn
import pl.pelotasplus.eyeofbeholder.data.model.ItemTypes
import pl.pelotasplus.eyeofbeholder.data.model.TrackIndex
import pl.pelotasplus.eyeofbeholder.data.repository.ItemTypesRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.ItemsRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.ResourceRepositoryImpl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The four horns, which are blown rather than swung.
 *
 * A horn's value says which of them it is, counting from one, and that is what
 * decides the sound. The game holds one of each, a floor apart: the bellowing
 * one on level 1, the hollow one on level 2, and the other two on level 3.
 */
@Category(NeedsGameData::class)
class BlowingAHornTest {

    private val resources = ResourceRepositoryImpl()

    private val types: ItemTypes by lazy {
        runBlocking { ItemTypesRepositoryImpl(resources).loadItemTypes().getOrThrow() }
    }

    private val dungeonItems by lazy {
        runBlocking { ItemsRepositoryImpl(resources).loadItems().getOrThrow().items }
    }

    @Test
    fun `each of the four sounds its own note`() {
        assertEquals(TrackIndex(0x40), Horn.BELLOWING.heardAs)
        assertEquals(TrackIndex(0x41), Horn.HOLLOW.heardAs)
        assertEquals(TrackIndex(0x42), Horn.MELODIOUS.heardAs)
        assertEquals(TrackIndex(0x43), Horn.EERIE.heardAs)

        assertEquals(4, Horn.entries.map { it.heardAs }.toSet().size, "two horns sound alike")
    }

    @Test
    fun `a horn's value counts from one`() {
        assertEquals(Horn.BELLOWING, Horn.of(1))
        assertEquals(Horn.EERIE, Horn.of(4))
        assertNull(Horn.of(0), "there is no horn before the first")
        assertNull(Horn.of(5), "there is no fifth horn")
    }

    /**
     * The one the party find first. It lies on level 1, and blowing it is
     * heard rather than swung — which is the whole of the bug this pins: it
     * used to be a weapon that made no sound.
     */
    @Test
    fun `the horn on level 1 is blown and heard`() {
        val onLevelOne = dungeonItems.first { it.level == 1 && types.hornBlown(it) != null }

        assertEquals(Horn.BELLOWING, types.hornBlown(onLevelOne))
        assertEquals(false, types.isSwungByHand(onLevelOne), "it is swung rather than blown")
    }

    /** And the game holds one of each, so no puzzle is left without its note. */
    @Test
    fun `the game holds one of every horn`() {
        val found = dungeonItems.mapNotNull { types.hornBlown(it) }.toSet()

        assertEquals(Horn.entries.toSet(), found)
    }
}
