package pl.pelotasplus.eyeofbeholder.agentserver

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** When `walk` and `fight` stop for someone's wounds. */
class BadlyHurtTest {

    private fun party(vararg champions: String): JsonObject =
        Games.json.parseToJsonElement("""{"party":[${champions.joinToString()}]}""").jsonObject

    @Test
    fun `a champion under a third of their hit points stops the party`() {
        val seen = party("""{"name":"Calandra","hitPoints":"20/76","conditions":[]}""")

        assertEquals("Calandra (20/76)", badlyHurt(seen))
    }

    @Test
    fun `a champion at a third or more does not`() {
        assertNull(badlyHurt(party("""{"name":"Calandra","hitPoints":"26/76","conditions":[]}""")))
    }

    @Test
    fun `a dead champion does not stop every step`() {
        val seen = party(
            """{"name":"San-raal","hitPoints":"-10/28","conditions":["dead"]}""",
            """{"name":"Calandra","hitPoints":"76/76","conditions":[]}""",
        )

        assertNull(badlyHurt(seen))
    }

    @Test
    fun `a dead champion does not hide a living one who is hurt`() {
        val seen = party(
            """{"name":"San-raal","hitPoints":"-10/28","conditions":["dead"]}""",
            """{"name":"Stumpy","hitPoints":"10/82","conditions":[]}""",
        )

        assertEquals("Stumpy (10/82)", badlyHurt(seen))
    }

    @Test
    fun `someone is struck by a move only if they lost hit points over it`() {
        val before = party(
            """{"name":"Calandra","hitPoints":"54/76","struckFor":22}""",
            """{"name":"LAURANN","hitPoints":"26/33"}""",
        )
        val after = party(
            """{"name":"Calandra","hitPoints":"54/76","struckFor":22}""",
            """{"name":"LAURANN","hitPoints":"17/33","struckFor":9}""",
        )

        assertEquals(listOf("LAURANN"), struckBetween(before, after))
    }

    @Test
    fun `a blow still shown from before the move does not stop it`() {
        val seen = party("""{"name":"Calandra","hitPoints":"54/76","struckFor":22}""")

        assertEquals(emptyList(), struckBetween(seen, seen))
    }
}
