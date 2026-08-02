package pl.pelotasplus.eyeofbeholder.data

import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.Maz
import pl.pelotasplus.eyeofbeholder.data.model.TeleporterPulse
import pl.pelotasplus.eyeofbeholder.data.model.WallByte
import pl.pelotasplus.eyeofbeholder.data.model.WallSide
import pl.pelotasplus.eyeofbeholder.data.model.isTeleporter
import pl.pelotasplus.eyeofbeholder.data.model.teleporterHazeAt
import pl.pelotasplus.eyeofbeholder.data.model.teleporterHazes
import pl.pelotasplus.eyeofbeholder.data.model.teleportersInView
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The shimmer is the engine's, not the level's: nothing in a file says a
 * square teleports, so what these pin down is the recognition and the shape of
 * what gets drawn.
 */
class TeleporterTest {

    @Test
    fun `a teleporter is one wall byte and nothing else`() {
        assertTrue(WallByte(44).isTeleporter)
        assertFalse(WallByte(43).isTeleporter)
        assertFalse(WallByte(45).isTeleporter)
    }

    @Test
    fun `every depth hangs two clouds of thirteen sparks`() {
        teleporterHazes.forEach { haze ->
            assertEquals(2, haze.clouds.size)
            assertEquals(2, haze.blobs.size)
            haze.clouds.forEach { assertEquals(13, it.sparks.size) }
        }
    }

    @Test
    fun `the pulse trades the two clouds' blobs`() {
        teleporterHazes.forEach { haze ->
            assertEquals(
                haze.blobFor(0, TeleporterPulse.AS_LAID_OUT),
                haze.blobFor(1, TeleporterPulse.TRADED),
            )
            assertEquals(
                haze.blobFor(1, TeleporterPulse.AS_LAID_OUT),
                haze.blobFor(0, TeleporterPulse.TRADED),
            )
        }
    }

    @Test
    fun `the nearer the square the bigger its sparks`() {
        val areas = teleporterHazes.map { haze -> haze.blobs.sumOf { it.w * it.h } }

        assertEquals(areas.sortedDescending(), areas, "nearest row first")
    }

    @Test
    fun `the party's own square wears no haze`() {
        (0..2).forEach { assertNotNull(teleporterHazeAt(it), "depth row $it") }
        assertNull(teleporterHazeAt(3))
    }

    @Test
    fun `a square is a teleporter by the face it turns to the party`() {
        val inView = teleportersInView(
            party = Location(10, 8),
            facing = Direction.WEST,
            wallAt = wallAt(Location(9, 8), WallSide.EAST),
        )

        assertEquals(listOf(13), inView.map { it.blockIndex }, "the square straight ahead")
    }

    @Test
    fun `the same byte on the far face of that square is not seen`() {
        val inView = teleportersInView(
            party = Location(10, 8),
            facing = Direction.WEST,
            wallAt = wallAt(Location(9, 8), WallSide.WEST),
        )

        assertEquals(emptyList(), inView)
    }

    /** A world whose only teleporter is on one face of one square. */
    private fun wallAt(teleporter: Location, side: WallSide): (Location, WallSide) -> Maz.WallType =
        { at, asked ->
            if (at == teleporter && asked == side) {
                Maz.WallType.Decoration(44)
            } else {
                Maz.WallType.NoWall
            }
        }
}
