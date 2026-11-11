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

    /**
     * The wall of souls on the eleventh floor, the east face of 11x20: a
     * picture with no wall behind it, like a floor plate, but one that stops
     * the party, so it is a face across the way and not something on the floor.
     */
    @Test
    fun `a picture that bars the way is told as a wall`() {
        val told = toldAt("LEVEL11.INF", Location(3, 20), Direction.WEST)
        val itsSquare = told.inSight.single { it.ahead == 1 && it.across == 0 }

        assertEquals("wall with picture 36", itsSquare.near)
        assertEquals(null, itsSquare.floor)
    }

    /** `level5-10x8-east`: a wall straight in front, and nothing beyond it is told. */
    @Test
    fun `nothing behind a wall is told`() {
        val told = toldAt("LEVEL5.INF", Location(10, 8), Direction.EAST)

        assertEquals("wall", told.inSight.single { it.ahead == 1 }.near)
        assertTrue(told.inSight.none { it.ahead > 1 }, "it was told what is behind the wall")
    }

    /**
     * The second floor's Skull door at 8x6, shut, with a room and its loot
     * behind it. A shut door is drawn as a solid panel, so nothing past it is
     * told.
     */
    @Test
    fun `nothing behind a shut door is told`() {
        val told = toldAt("LEVEL2.INF", Location(8, 7), Direction.NORTH)

        assertEquals("shut door", told.inSight.single { it.ahead == 1 && it.across == 0 }.near)
        assertTrue(told.inSight.none { it.ahead > 1 }, "it was told what is behind the door")
        assertTrue(told.inSight.all { it.items.isEmpty() && it.monsters.isEmpty() }, "it was told what is behind the door")
    }

    /**
     * The door into the second floor's store at 8x3, which Pericles calls
     * stuck and which is drawn as a door, not a wall.
     */
    @Test
    fun `a stuck door is told as a door`() {
        val told = toldAt("LEVEL2.INF", Location(8, 4), Direction.NORTH)

        assertEquals("stuck door", told.inSight.single { it.ahead == 1 && it.across == 0 }.near)
    }

    /**
     * An undug grave in the forest, the first picture of its set — drawn as a
     * cross in front of the party, and the thing to click to dig it.
     */
    @Test
    fun `the first picture of a set is told like any other`() {
        val told = toldAt("LEVEL4.INF", Location(19, 5), Direction.NORTH)

        assertEquals("picture 0", told.inSight.single { it.ahead == 1 && it.across == 0 }.floor)
    }

    /**
     * The first floor's food store, Iron Rations on the shelf in the wall
     * ahead. They are reached through the wall, not from the floor, and are
     * told so.
     */
    @Test
    fun `a thing on a shelf is told as in a niche`() {
        val told = toldAt("LEVEL1.INF", Location(4, 3), Direction.WEST)
        val shelf = told.inSight.single { it.ahead == 1 && it.across == 0 }

        assertEquals(listOf(SeenItem("Iron Rations", "in a niche")), shelf.items)
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
