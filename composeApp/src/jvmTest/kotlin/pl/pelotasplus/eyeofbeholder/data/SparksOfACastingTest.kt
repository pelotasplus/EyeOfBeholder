package pl.pelotasplus.eyeofbeholder.data

import pl.pelotasplus.eyeofbeholder.data.model.Spell
import pl.pelotasplus.eyeofbeholder.data.model.SparksInTheRoom
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The sparks a casting throws about the view: which spells throw them, and
 * what is alight on a given frame.
 *
 * The rows below are the game's own, worked out from the packed words rather
 * than read off what we draw. So is the list of four spells: seventy spells
 * carry a flag saying what a casting looks like, and only those four ask for
 * this.
 */
class SparksOfACastingTest {

    private fun SparksInTheRoom.row() = List(SparksInTheRoom.HOW_MANY) { showing(it) }

    @Test
    fun `only four spells throw sparks across the view`() {
        assertEquals(
            setOf(
                Spell.DISPEL_MAGIC,
                Spell.DISINTEGRATE,
                Spell.A_CLERICS_DISPEL_MAGIC,
                Spell.TURN_UNDEAD,
            ),
            Spell.entries.filter { it.throwsSparks }.toSet(),
        )
    }

    @Test
    fun `it opens on one spark and fills out from there`() {
        assertEquals(
            listOf(1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
            SparksInTheRoom(0).row(),
        )
        assertEquals(
            listOf(2, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
            SparksInTheRoom(4).row(),
        )
        assertEquals(
            listOf(3, 2, 2, 2, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0),
            SparksInTheRoom(8).row(),
        )
    }

    @Test
    fun `halfway through, every one of them is alight`() {
        assertEquals(
            listOf(0, 1, 1, 1, 2, 2, 2, 2, 3, 3, 3, 2, 2, 2, 2, 1),
            SparksInTheRoom(20).row(),
        )
    }

    /**
     * Four frames of nothing at the end, which is a beat rather than an
     * oversight: the last spark goes out on frame 39 and the animation keeps
     * running to 43. Cutting the run short there would take the pause with it.
     */
    @Test
    fun `the last four frames are dark`() {
        assertEquals(
            listOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1),
            SparksInTheRoom(39).row(),
        )
        for (frame in 40 until SparksInTheRoom.FRAMES) {
            assertTrue(SparksInTheRoom(frame).row().all { it == 0 }, "frame $frame lit something")
        }
    }

    @Test
    fun `a spark never asks for a picture there is not`() {
        for (frame in 0 until SparksInTheRoom.FRAMES) {
            assertTrue(
                SparksInTheRoom(frame).row().all { it in 0..SparksInTheRoom.PICTURES },
                "frame $frame asked for a picture outside the three",
            )
        }
    }

    @Test
    fun `every spark falls inside the view`() {
        for (which in 0 until SparksInTheRoom.HOW_MANY) {
            val spark = SparksInTheRoom()
            assertTrue(spark.x(which) + SPARK_SIDE <= VIEW_WIDTH, "spark $which runs off the side")
            assertTrue(spark.y(which) + SPARK_SIDE <= VIEW_HEIGHT, "spark $which runs off the bottom")
        }
    }

    @Test
    fun `it runs its frames and stops`() {
        var spark: SparksInTheRoom? = SparksInTheRoom()
        var frames = 0

        while (spark != null) {
            frames++
            spark = spark.next()
        }

        assertEquals(SparksInTheRoom.FRAMES, frames)
        assertNull(SparksInTheRoom(SparksInTheRoom.FRAMES - 1).next())
    }

    private companion object {
        const val SPARK_SIDE = 16
        const val VIEW_WIDTH = 176
        const val VIEW_HEIGHT = 120
    }
}
