package pl.pelotasplus.eyeofbeholder.agent

import kotlinx.coroutines.runBlocking
import org.junit.experimental.categories.Category
import pl.pelotasplus.eyeofbeholder.NeedsGameData
import pl.pelotasplus.eyeofbeholder.data.ThePlayField
import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.Location
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The shortcuts and the looking-behind that QA mode gives an agent, on the
 * real play field.
 */
@Category(NeedsGameData::class)
class WhatQaModeDoesTest {

    private fun field(level: String, at: Location, facing: Direction): ThePlayField = runBlocking {
        ThePlayField().apply {
            opened(level, at, facing)
            until("the party") { viewModel.state.value.game.champions.isNotEmpty() }
        }
    }

    @Test
    fun `a teleport puts the party where it says`() = runBlocking {
        val field = field("LEVEL12.INF", Location(19, 15), Direction.SOUTH)

        assertNull(field.viewModel.setUp(QaSetup.Teleport(level = 5, x = 13, y = 12, facing = Direction.NORTH)))
        field.until("level 5") { field.viewModel.observation()?.let { it.level == 5 && it.at == Location(13, 12) } == true }
    }

    /** On the fourth floor, which places wolves: one put two squares ahead stands there, facing the party. */
    @Test
    fun `a monster can be put in front of the party`() = runBlocking {
        val field = field("LEVEL4.INF", Location(18, 14), Direction.NORTH)

        assertNull(field.viewModel.setUp(QaSetup.SpawnMonster(looksLike = "wolf", ahead = 2)))

        val wolf = requireNotNull(field.viewModel.inspection()).monsters.single { it.at == Location(18, 12) }
        assertEquals("wolf", wolf.looksLike)
        assertEquals(Direction.SOUTH, wolf.facing)
    }

    @Test
    fun `a look the floor does not place is refused, naming those it does`() {
        val field = field("LEVEL4.INF", Location(18, 14), Direction.NORTH)

        val refused = requireNotNull(field.viewModel.setUp(QaSetup.SpawnMonster(looksLike = "dragon")))

        assertTrue("wolf" in refused, refused)
    }

    /**
     * A mage spell nothing has been written for yet, given to Laurann and cast
     * from her book: the casting is noted as reaching nothing.
     */
    @Test
    fun `a spell with no effect is noted when cast`() = runBlocking {
        val field = field("LEVEL12.INF", Location(19, 15), Direction.SOUTH)
        val before = Diagnostics.since(0).lastOrNull()?.number ?: 0

        assertNull(field.viewModel.setUp(QaSetup.GiveSpell(champion = 3, spell = "armor", ready = true)))
        assertNull(field.viewModel.act(AgentAction.UseHand(champion = 3, hand = 1)))
        field.until("the book") { field.viewModel.observation()?.book != null }
        assertEquals("Armor", field.viewModel.observation()?.book?.lines?.first())

        assertNull(field.viewModel.act(AgentAction.BookLine(0)))
        field.until("the note") { Diagnostics.since(before).any { "armor" in it.said } }
    }

    @Test
    fun `the game's own warnings are noted, and its quieter lines are not`() {
        Diagnostics.listen()
        val before = Diagnostics.since(0).lastOrNull()?.number ?: 0

        co.touchlab.kermit.Logger.w("QaTest") { "a warning worth seeing" }
        co.touchlab.kermit.Logger.d("QaTest") { "a line nobody needs" }

        val noted = Diagnostics.since(before).map { it.said }
        assertTrue(noted.any { "a warning worth seeing" in it }, "$noted")
        assertTrue(noted.none { "a line nobody needs" in it }, "$noted")
    }

    @Test
    fun `an item can be put on the pointer`() {
        val field = field("LEVEL12.INF", Location(19, 15), Direction.SOUTH)

        assertNull(field.viewModel.setUp(QaSetup.GiveItem("dagger")))

        assertNotNull(field.viewModel.observation()?.holding)
    }

    @Test
    fun `healing mends everybody to the full`() {
        val field = field("LEVEL12.INF", Location(19, 15), Direction.SOUTH)

        assertNull(field.viewModel.setUp(QaSetup.Heal))

        requireNotNull(field.viewModel.inspection()).party.forEach { champion ->
            val (current, most) = champion.hitPoints.split("/")
            assertEquals(most, current, "${champion.name} was not mended")
        }
    }
}
