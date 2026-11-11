package pl.pelotasplus.eyeofbeholder.agentserver

import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BrieflyTest {

    private val seen = Games.json.parseToJsonElement(
        """{"at":{"x":1,"y":2},"party":[{"name":"Laurann","hitPoints":"33/33","hands":["Spellbook"],"pack":["Dagger"],"worn":["cloak"],"spells":["Fireball"]}]}""",
    ).jsonObject

    @Test
    fun `packs and what is worn are left out`() {
        val champion = briefly(seen)["party"]!!.jsonArray.single().jsonObject

        assertFalse("pack" in champion)
        assertFalse("worn" in champion)
    }

    @Test
    fun `everything a move or a fight is decided on stays`() {
        val brief = briefly(seen)
        val champion = brief["party"]!!.jsonArray.single().jsonObject

        assertEquals(seen["at"], brief["at"])
        assertTrue(listOf("name", "hitPoints", "hands", "spells").all { it in champion })
    }
}
