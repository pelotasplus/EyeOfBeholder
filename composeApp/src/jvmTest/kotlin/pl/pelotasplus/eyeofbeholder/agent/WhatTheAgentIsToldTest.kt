package pl.pelotasplus.eyeofbeholder.agent

import kotlinx.coroutines.runBlocking
import org.junit.experimental.categories.Category
import pl.pelotasplus.eyeofbeholder.NeedsGameData
import pl.pelotasplus.eyeofbeholder.data.ThePlayField
import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.Location
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What an agent playing is told, at places the goldens already show — so
 * what to expect is what is on those pictures, read off them.
 */
@Category(NeedsGameData::class)
class WhatTheAgentIsToldTest {

    private fun toldAt(level: String, at: Location, facing: Direction): Observation = runBlocking {
        val field = ThePlayField()
        field.opened(level, at, facing)
        field.until("the party") { field.viewModel.state.value.game.champions.isNotEmpty() }
        requireNotNull(field.viewModel.observation())
    }

    /** `level12-plate-7x13-north`: a guardian two squares off, facing the party, on a plate, a wall behind it. */
    @Test
    fun `a monster in sight is told as it is drawn`() {
        val told = toldAt("LEVEL12.INF", Location(7, 13), Direction.NORTH)
        val itsSquare = told.inSight.single { it.monsters.isNotEmpty() }

        assertEquals(2, itsSquare.ahead)
        assertEquals(0, itsSquare.across)
        assertEquals(listOf(SeenMonster(looksLike = "guardian", facing = "towards you", standing = "middle")), itsSquare.monsters)
        assertTrue(itsSquare.floor != null, "the plate under it was not told")
        assertEquals("wall", told.inSight.single { it.ahead == 3 }.near)
    }

    /** `level5-10x8-east`: a wall straight in front, and nothing beyond it is told. */
    @Test
    fun `nothing behind a wall is told`() {
        val told = toldAt("LEVEL5.INF", Location(10, 8), Direction.EAST)

        assertEquals("wall", told.inSight.single { it.ahead == 1 }.near)
        assertTrue(told.inSight.none { it.ahead > 1 }, "it was told what is behind the wall")
    }

    /** The quick start party as the panel shows them: who is in each hand, what they could cast. */
    @Test
    fun `the party is told as the panel shows them`() {
        val told = toldAt("LEVEL5.INF", Location(10, 8), Direction.EAST)
        val laurann = told.party.single { it.name == "LAURANN" }

        assertEquals(4, told.party.size)
        assertEquals(listOf("+1 Dagger", "Spellbook"), laurann.hands)
        assertEquals(listOf("Turn undead"), told.party.single { it.name == "WOLFSPIRIT" }.spells)
    }

    /**
     * The old woman of the forest, who is the first conversation of the game.
     * A conversation stops everything until it is answered, so an agent that
     * is not told about it has no way to learn why nothing it does works.
     */
    @Test
    fun `a conversation is told, words and buttons`(): Unit = runBlocking {
        val field = ThePlayField()
        field.opened("LEVEL4.INF", Location(13, 11), Direction.WEST)
        field.until("the party") { field.viewModel.state.value.game.champions.isNotEmpty() }

        field.viewModel.act(AgentAction.Step(AgentAction.Way.FORWARD))
        field.until("the old woman") { field.viewModel.observation()?.dialogue?.says?.isNotEmpty() == true }

        val asked = requireNotNull(field.viewModel.observation()?.dialogue)
        assertTrue(asked.says.isNotEmpty(), "a conversation was told with nothing said")
        assertTrue(asked.buttons.isNotEmpty(), "a conversation was told with nothing to answer")

        assertEquals(
            "Not done: a dialogue is up.",
            field.viewModel.act(AgentAction.Step(AgentAction.Way.FORWARD)),
            "a step under a conversation was turned away for the wrong reason",
        )
    }
}
