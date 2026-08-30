package pl.pelotasplus.eyeofbeholder.data

import pl.pelotasplus.eyeofbeholder.data.model.ThePortal
import pl.pelotasplus.eyeofbeholder.data.model.TrackIndex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The portal opening, read off the table the game keeps for it.
 *
 * The table is transcribed rather than derived, so what is worth asserting is
 * that it was transcribed whole and that the rules read off it — which stage,
 * what shows through, what is heard — come out where the game puts them.
 */
class ThePortalTest {

    private val opening = ThePortal.OPENS

    @Test
    fun `the whole table is read and the end of it is not a step`() {
        // 126 bytes, two to a step, less the pair of 0xFF that ends it.
        assertEquals(62, opening.size)
    }

    @Test
    fun `it starts shut and ends showing the last of the tiles`() {
        assertEquals(null, opening.first().showing, "it began already open")
        assertEquals(9, opening.last().showing, "it ended on the wrong tile")
        assertEquals(0, opening.last().arch, "the arch did not come back to rest")
    }

    /** Five stages of the arch and ten tiles, and nothing outside them. */
    @Test
    fun `every step names a stage and a tile the pictures hold`() {
        opening.forEach { step ->
            assertTrue(step.arch in 0..4, "no such stage of the arch: ${step.arch}")
            step.showing?.let {
                assertTrue(it in 0..9, "no such tile: $it")
            }
        }
    }

    /**
     * The arch groans as it starts to move, which belongs to the step that
     * leaves stage nought rather than to stage one wherever it appears.
     */
    @Test
    fun `the arch is heard only as it starts moving again`() {
        opening.forEachIndexed { i, step ->
            val groans = TrackIndex(24) in step.sounds
            val startingOut = step.arch == 1 && opening.getOrNull(i - 1)?.arch == 0

            assertEquals(startingOut, groans, "step $i")
        }
    }

    @Test
    fun `the first step is silent because nothing came before it`() {
        assertEquals(emptyList(), opening.first().sounds)
    }

    /** The other two go with what shows through rather than with the arch. */
    @Test
    fun `what shows through is what is heard`() {
        opening.forEach { step ->
            if (step.showing == 1) {
                assertTrue(TrackIndex(31) in step.sounds, "tile 1 was not heard")
            }
            if (step.showing == 3 && step.arch == 3) {
                assertTrue(TrackIndex(90) in step.sounds, "tile 3 under a full arch was not heard")
            }
        }
    }

    /** The pieces are cut where the picture keeps them. */
    @Test
    fun `the arch is cut in five stages from its own rows`() {
        assertEquals(ThePortal.Cut(0, 0, 24, 75), ThePortal.leftPillar(0))
        assertEquals(ThePortal.Cut(96, 0, 24, 75), ThePortal.leftPillar(4))
        assertEquals(ThePortal.Cut(0, 80, 24, 75), ThePortal.rightPillar(0))
        assertEquals(ThePortal.Cut(120, 72, 120, 18), ThePortal.lintel(4))
    }

    @Test
    fun `the tiles run five to a row`() {
        assertEquals(ThePortal.Cut(0, 0, 64, 77), ThePortal.showingThrough(0))
        assertEquals(ThePortal.Cut(256, 0, 64, 77), ThePortal.showingThrough(4))
        assertEquals(ThePortal.Cut(0, 77, 64, 77), ThePortal.showingThrough(5))
        assertEquals(ThePortal.Cut(256, 77, 64, 77), ThePortal.showingThrough(9))
    }
}
