package pl.pelotasplus.eyeofbeholder.agentserver

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Where `fight` steps between blows. A step into a wall goes nowhere and
 * leaves the party in front of the swing, and a step onto a monster's
 * square is refused, so both count as shut.
 */
class SteppingAsideTest {

    private fun told(json: String): JsonObject = Games.json.parseToJsonElement(json).jsonObject

    @Test
    fun `an open left side is stepped to first`() {
        assertEquals("LEFT", sideStepFrom(told("""{"inSight":[{"ahead":0,"across":0},{"ahead":1,"across":0}]}""")))
    }

    @Test
    fun `a wall on the left sends the party right`() {
        assertEquals("RIGHT", sideStepFrom(told("""{"inSight":[{"ahead":0,"across":0,"left":"wall"}]}""")))
    }

    @Test
    fun `an open door is a way aside and a shut one is not`() {
        assertEquals("LEFT", sideStepFrom(told("""{"inSight":[{"ahead":0,"across":0,"left":"open door","right":"wall"}]}""")))
        assertNull(sideStepFrom(told("""{"inSight":[{"ahead":0,"across":0,"left":"shut door","right":"wall"}]}""")))
    }

    @Test
    fun `a corridor has no side to step to`() {
        assertNull(sideStepFrom(told("""{"inSight":[{"ahead":0,"across":0,"left":"wall","right":"wall with picture 8"}]}""")))
    }

    @Test
    fun `a monster beside the party shuts that side`() {
        val seen = told(
            """{"inSight":[{"ahead":0,"across":0},
            {"ahead":0,"across":-1,"monsters":[{"looksLike":"guard1","facing":"towards you","standing":"middle"}]}]}""",
        )
        assertEquals("RIGHT", sideStepFrom(seen))
    }

    @Test
    fun `a side another monster stands in front of is not stepped to`() {
        val seen = told(
            """{"inSight":[{"ahead":0,"across":0},
            {"ahead":1,"across":0,"monsters":[{"looksLike":"bulette","facing":"towards you","standing":"middle"}]},
            {"ahead":1,"across":-1,"monsters":[{"looksLike":"bulette","facing":"towards you","standing":"middle"}]}]}""",
        )
        assertEquals("RIGHT", sideStepFrom(seen))
    }

    @Test
    fun `a side with a monster beyond it is not stepped to`() {
        val seen = told(
            """{"inSight":[{"ahead":0,"across":0,"right":"wall"},
            {"ahead":0,"across":-2,"monsters":[{"looksLike":"bulette","facing":"towards you","standing":"middle"}]}]}""",
        )
        assertNull(sideStepFrom(seen))
    }
}
