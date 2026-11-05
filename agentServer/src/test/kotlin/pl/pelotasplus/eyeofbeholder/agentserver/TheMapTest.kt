package pl.pelotasplus.eyeofbeholder.agentserver

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The map is drawn from what the party were told facing one way, and has to
 * land on the maze facing every way. A turn table written backwards would
 * put every wall on the wrong side and look perfectly plausible.
 */
class TheMapTest {

    private fun told(json: String): JsonObject = Games.json.parseToJsonElement(json).jsonObject

    private fun fresh() = TheMap(File(createTempDirectory("eob-map").toFile(), "map.json"))

    /**
     * Facing east, a wall on the party's left is the north side of their
     * square, and the wall that stops the view two squares on is the east
     * side of the square before it.
     */
    @Test
    fun `what is seen facing east lands on the maze as north up has it`() {
        val map = fresh()
        map.saw(
            told(
                """{"level":4,"at":{"x":11,"y":5},"facing":"EAST","inSight":[
                {"ahead":0,"across":0,"left":"wall"},
                {"ahead":1,"across":0,"right":"wall"},
                {"ahead":2,"across":0,"near":"wall"},
                {"ahead":1,"across":1,"items":[{"name":"Rock","lying":"near_right"}]}]}""",
            ),
        )
        val floor = map.floor(4)!!

        assertEquals("wall", floor.sides["11,5,N"], "the left of a party facing east is north")
        assertEquals("", floor.sides["11,6,N"], "the right of the own square was seen open")
        assertEquals("wall", floor.sides["12,6,N"], "the right of the square ahead is its south side")
        assertEquals("wall", floor.sides["13,5,W"], "the wall two on is the near side of the square it hides")
        assertTrue("13,5" !in floor.seen, "the square behind the wall that stopped the view was taken as seen")
        assertEquals(listOf("Rock"), floor.items["12,6"], "an item one ahead and one right of a party facing east is south of the square ahead")
    }

    /** The forest's start, facing east: three squares of corridor and a wall closing it, which is all that is seen of the fourth. */
    @Test
    fun `a wall three ahead hides the square behind it`() {
        val map = fresh()
        map.saw(
            told(
                """{"level":4,"at":{"x":11,"y":5},"facing":"EAST","inSight":[
                {"ahead":0,"across":0,"left":"wall"},
                {"ahead":1,"across":0,"right":"wall"},
                {"ahead":2,"across":0,"left":"wall"},
                {"ahead":3,"across":0,"near":"wall"}]}""",
            ),
        )
        assertTrue("14,5" !in map.floor(4)!!.seen, "the square behind a wall three ahead was taken as seen")
        assertEquals("wall", map.floor(4)!!.sides["14,5,W"])
    }

    @Test
    fun `turning is a quarter at a time and four of them come back round`() {
        TheMap.Facing.entries.forEach { facing ->
            assertEquals(facing, facing.right.right.right.right)
            assertEquals(facing.back, facing.right.right)
            assertEquals(facing.left, facing.right.right.right)
        }
        assertEquals(TheMap.Facing.EAST, TheMap.Facing.NORTH.right)
        assertEquals(TheMap.Square(10, 5), TheMap.Square(11, 5).seenAt(TheMap.Facing.WEST, ahead = 1, across = 0))
        assertEquals(TheMap.Square(11, 4), TheMap.Square(11, 5).seenAt(TheMap.Facing.WEST, ahead = 0, across = 1))
    }

    /** A thing seen lying somewhere and then seen gone is gone from the map too. */
    @Test
    fun `an item taken is no longer drawn`() {
        val map = fresh()
        map.saw(told("""{"level":4,"at":{"x":13,"y":6},"facing":"SOUTH","inSight":[{"ahead":0,"across":0,"items":[{"name":"Rock","lying":"far_right"}]}]}"""))
        assertEquals(listOf("Rock"), map.floor(4)!!.items["13,6"])

        map.saw(told("""{"level":4,"at":{"x":13,"y":6},"facing":"SOUTH","inSight":[{"ahead":0,"across":0}]}"""))
        assertNull(map.floor(4)!!.items["13,6"])
        assertTrue(map.drawn(4).contains('v'), "the party facing south is not drawn")
    }
}
